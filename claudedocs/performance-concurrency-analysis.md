# 성능 및 동시성 분석 보고서

## 1. 조회 성능 분석

### 1.1 조회가 오래 걸릴 수 있는 기능

#### 🔴 High Priority - 즉시 최적화 필요

##### 1) 예약 가능 좌석 조회
**현재 구현**: `GetAvailableSeatsUseCase.kt:26`
```kotlin
val availableSeats = seatService.findAllByConcertScheduleId(command.scheduleId)
    .filter { seat -> seat.isAvailable }
```

**문제점**:
- 스케줄별 전체 좌석 조회 후 메모리 필터링
- 콘서트당 좌석이 수천~수만 개일 경우 Full Table Scan 발생
- 좌석 상태별 필터링이 애플리케이션 레벨에서 수행

**예상 데이터 규모**:
- 대형 콘서트: 10,000 ~ 50,000석
- 중형 콘서트: 1,000 ~ 5,000석
- 조회 빈도: 초당 100+ TPS (인기 공연 오픈 시)

**성능 예측**:
- 현재: 10,000석 기준 200~500ms
- 개선 후: 10~50ms (95% 개선)

##### 2) 임시 예약 만료 좌석 조회 (스케줄러)
**현재 구현**: `SeatService.kt:32-34`
```kotlin
val temporarySeats = seatRepository.findAllByStatus(SeatStatus.TEMPORARY_RESERVED)
val expiredSeats = temporarySeats.filter { it.isExpiredTemporaryReservation(expirationMinutes) }
```

**문제점**:
- 전체 임시예약 좌석 조회 후 만료 시간 메모리 필터링
- 스케줄러가 매 분마다 실행될 경우 불필요한 부하
- 시간 조건 필터링이 DB에서 처리되지 않음

**예상 데이터 규모**:
- 동시 임시예약: 1,000 ~ 10,000건
- 실행 빈도: 1분마다
- 만료 대상: 전체의 10~20%

**성능 예측**:
- 현재: 5,000건 기준 100~200ms
- 개선 후: 5~20ms (90% 개선)

#### 🟡 Medium Priority - 모니터링 후 최적화

##### 3) 사용자별 예약 내역 조회
**현재 구현**: `ReservationService.kt:16-18`
```kotlin
fun findAllByUserId(userId: Long): List<ReservationModel> {
    return reservationRepository.findAllByUserId(userId)
}
```

**문제점**:
- userId 인덱스 누락 시 Full Table Scan
- 페이징 미지원 (사용자당 수백~수천 건 누적 가능)
- 정렬 조건 미지정 (최신순 조회 시 정렬 오버헤드)

**예상 데이터 규모**:
- 활성 사용자 예약: 평균 10~50건
- 헤비유저: 100~500건
- 조회 빈도: 초당 10~50 TPS

**성능 예측**:
- 현재: 50건 기준 50~100ms (인덱스 없을 시)
- 개선 후: 5~10ms (복합 인덱스 + 페이징)

##### 4) 포인트 히스토리 조회
**위치**: `PointHistoryRepository` (추론)

**문제점**:
- 시간 범위 조회 시 인덱스 미활용 가능성
- 페이징 미지원 시 전체 히스토리 로딩
- 거래 유형(CHARGE/USE)별 필터링 비효율

**예상 데이터 규모**:
- 사용자당 히스토리: 평균 50~200건
- 헤비유저: 500~2,000건
- 조회 빈도: 초당 5~20 TPS

**성능 예측**:
- 현재: 200건 기준 100~300ms (복합 조건 시)
- 개선 후: 10~30ms (커버링 인덱스 + 페이징)

---

### 1.2 테이블 재설계 및 인덱스 솔루션

#### 📋 Seat 테이블 최적화

**현재 구조** (`Seat.kt`):
```kotlin
@Table(name = "seat")
class Seat(
    @Column(name = "concert_schedule_id", nullable = false)
    val concertScheduleId: Long,

    val seatNumber: Int,

    @Enumerated(EnumType.STRING)
    var seatStatus: SeatStatus,

    var price: Int,
)
```

**문제점**:
- `concert_schedule_id` 단일 인덱스만으로는 상태별 필터링 비효율
- 임시예약 만료 시간 조회를 위한 시간 컬럼 부재
- Full Table Scan 유발

**재설계 방안**:

##### 방안 1: 복합 인덱스 추가 (권장)
```sql
-- 예약 가능 좌석 조회 최적화
CREATE INDEX idx_seat_schedule_status
ON seat(concert_schedule_id, seat_status);

-- 임시예약 만료 조회 최적화
ALTER TABLE seat ADD COLUMN temporary_reserved_at TIMESTAMP NULL;

CREATE INDEX idx_seat_status_reserved_time
ON seat(seat_status, temporary_reserved_at)
WHERE seat_status = 'TEMPORARY_RESERVED';
```

**효과**:
- 스케줄별 좌석 조회: O(n) → O(log n)
- 만료 좌석 조회: Full Scan → 파티셔닝된 인덱스 스캔
- 디스크 공간: 테이블 크기의 약 15% 증가

##### 방안 2: 상태별 파티셔닝 (대용량 시스템)
```sql
-- 좌석 상태별 파티션 테이블
CREATE TABLE seat (
    id BIGINT PRIMARY KEY,
    concert_schedule_id BIGINT NOT NULL,
    seat_number INT NOT NULL,
    seat_status VARCHAR(20) NOT NULL,
    price INT NOT NULL,
    temporary_reserved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) PARTITION BY LIST (seat_status) (
    PARTITION p_available VALUES IN ('AVAILABLE'),
    PARTITION p_temporary VALUES IN ('TEMPORARY_RESERVED'),
    PARTITION p_reserved VALUES IN ('RESERVED')
);

CREATE INDEX idx_schedule ON seat(concert_schedule_id);
CREATE INDEX idx_temp_time ON seat(temporary_reserved_at)
WHERE seat_status = 'TEMPORARY_RESERVED';
```

**효과**:
- 상태별 데이터 물리적 분리
- 파티션 프루닝으로 조회 범위 축소
- 유지보수: 파티션 관리 복잡도 증가

##### 방안 3: 커버링 인덱스 (읽기 최적화)
```sql
-- 좌석 조회 시 필요한 모든 컬럼 포함
CREATE INDEX idx_seat_covering
ON seat(concert_schedule_id, seat_status, seat_number, price);
```

**효과**:
- 인덱스만으로 조회 완료 (테이블 접근 불필요)
- 조회 속도: 추가 50% 향상
- 단점: 인덱스 크기 증가, 쓰기 성능 소폭 저하

**최종 권장**:
- **현재 규모 (~10만 좌석)**: 방안 1 (복합 인덱스)
- **중규모 (10~100만 좌석)**: 방안 1 + 방안 3 (커버링 인덱스)
- **대규모 (100만+ 좌석)**: 방안 2 (파티셔닝) + 방안 3

---

#### 📋 Reservation 테이블 최적화

**현재 구조** (`Reservation.kt`):
```kotlin
@Table(name = "reservation")
class Reservation(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "seat_id", nullable = false)
    val seatId: Long,

    @Enumerated(EnumType.STRING)
    var reservationStatus: ReservationStatus = ReservationStatus.TEMPORARY,
)
```

**문제점**:
- userId 인덱스 누락 시 사용자별 조회 비효율
- 정렬 조건(최신순) 미지원
- 페이징 처리 없음

**재설계 방안**:

```sql
-- 사용자별 예약 조회 최적화 (최신순 정렬 포함)
CREATE INDEX idx_reservation_user_created
ON reservation(user_id, created_at DESC);

-- 좌석별 예약 조회 (중복 예약 검증)
CREATE INDEX idx_reservation_seat_status
ON reservation(seat_id, reservation_status);

-- 상태별 통계 조회 최적화
CREATE INDEX idx_reservation_status_created
ON reservation(reservation_status, created_at);
```

**쿼리 최적화**:
```kotlin
// 페이징 지원 추가
interface ReservationJpaRepository : JpaRepository<Reservation, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable
    ): Page<Reservation>

    fun findByUserIdAndCreatedAtAfter(
        userId: Long,
        after: LocalDateTime
    ): List<Reservation>
}
```

---

#### 📋 PointHistory 테이블 최적화

**현재 구조** (`PointHistory.kt`):
```kotlin
@Table(name = "point_history")
class PointHistory(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    val amount: Int,

    @Enumerated(EnumType.STRING)
    val transactionType: TransactionType,
)
```

**문제점**:
- 시간 범위 조회 시 성능 저하
- 거래 유형별 필터링 비효율
- 통계 쿼리 시 Full Scan

**재설계 방안**:

```sql
-- 사용자별 히스토리 조회 (시간순 정렬)
CREATE INDEX idx_point_history_user_time
ON point_history(user_id, created_at DESC);

-- 거래 유형별 필터링 최적화
CREATE INDEX idx_point_history_user_type_time
ON point_history(user_id, transaction_type, created_at DESC);

-- 통계 쿼리 최적화 (커버링 인덱스)
CREATE INDEX idx_point_history_covering
ON point_history(user_id, transaction_type, amount, created_at);
```

**추가 최적화**: 파티셔닝 (시간 기반)
```sql
-- 월별 파티셔닝 (히스토리 데이터 증가 대비)
CREATE TABLE point_history (
    ...
) PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p_202401 VALUES LESS THAN (202402),
    PARTITION p_202402 VALUES LESS THAN (202403),
    ...
);
```

---

#### 📋 Point 테이블 동시성 최적화

**현재 구조** (`Point.kt`):
```kotlin
@Table(name = "point")
class Point(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    var balance: Int,
)
```

**문제점**:
- Pessimistic Lock 사용 중이나 인덱스 최적화 필요
- 락 대기 시간 증가 가능성

**재설계 방안**:

```sql
-- userId는 이미 UNIQUE이므로 자동 인덱스 생성됨
-- 추가적인 인덱스 불필요

-- 단, 잔액 조회 성능을 위한 커버링 인덱스 고려
CREATE INDEX idx_point_user_balance
ON point(user_id, balance);
```

**동시성 제어 강화**:
```kotlin
// Repository에서 명시적 락 타임아웃 설정
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
fun findByUserIdWithLock(userId: Long): Point?
```

---

### 1.3 인덱스 적용 우선순위

| 우선순위 | 테이블 | 인덱스 | 예상 효과 | 구현 난이도 |
|---------|--------|--------|----------|-----------|
| 🔴 P0 | Seat | idx_seat_schedule_status | 조회 95% 개선 | 낮음 |
| 🔴 P0 | Seat | idx_seat_status_reserved_time | 스케줄러 90% 개선 | 중간 (컬럼 추가) |
| 🟡 P1 | Reservation | idx_reservation_user_created | 조회 80% 개선 | 낮음 |
| 🟡 P1 | PointHistory | idx_point_history_user_time | 조회 70% 개선 | 낮음 |
| 🟢 P2 | Seat | idx_seat_covering | 추가 50% 개선 | 중간 (디스크 공간) |
| 🟢 P2 | PointHistory | 파티셔닝 | 장기 확장성 | 높음 (운영 복잡도) |

---

## 2. 동시성 이슈 분석

### 2.1 동시성 이슈가 예민한 기능

#### 🔴 Critical - 반드시 제어 필요

##### 1) 좌석 예약 (동일 좌석 중복 예약 방지)

**현재 구현**: `CreateReservationUseCase.kt:35-37`
```kotlin
val seat = seatService.findByIdAndConcertScheduleIdWithLock(command.seatId, schedule.id)
seat.temporaryReservation()
seatService.update(seat)
```

**동시성 제어 메커니즘**:
- Pessimistic Write Lock 사용 (`findByIdWithLock`)
- 트랜잭션 격리 수준: READ_COMMITTED (Spring 기본값)

**동시성 시나리오**:
```
시나리오 1: 동일 좌석에 대한 동시 예약 요청

Thread A: findByIdWithLock(seatId=1) → Lock 획득
Thread B: findByIdWithLock(seatId=1) → Lock 대기
Thread A: temporaryReservation() → AVAILABLE → TEMPORARY_RESERVED
Thread A: update() → Commit → Lock 해제
Thread B: Lock 획득 → seatStatus=TEMPORARY_RESERVED
Thread B: temporaryReservation() → 검증 실패 (이미 예약됨)
```

**잠재적 이슈**:
1. **Lock 타임아웃**: 다수의 동시 요청 시 대기 시간 증가
2. **Deadlock 가능성**: 다중 좌석 예약 시 락 순서 불일치
3. **성능 저하**: 락 경합으로 TPS 감소

**개선 방안**:
```kotlin
// 1. 락 타임아웃 명시
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
fun findByIdWithLock(id: Long): Seat?

// 2. 낙관적 락 병행 (버전 관리)
@Version
var version: Long = 0

// 3. 재시도 로직
@Retryable(
    value = [OptimisticLockException::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 100)
)
fun temporaryReservation() { ... }
```

##### 2) 포인트 충전/사용 (잔액 정합성)

**현재 구현**:
- `PointService.kt:18-23` (충전)
- `PointService.kt:25-30` (사용)

```kotlin
fun chargePoint(userId: Long, amount: Int): PointModel {
    val point = pointRepository.findByUserIdWithLock(userId)
    point.chargePoint(amount)
    return pointRepository.update(point)
}

fun usePoint(userId: Long, amount: Int): PointModel {
    val point = pointRepository.findByUserIdWithLock(userId)
    point.usePoint(amount)
    return pointRepository.update(point)
}
```

**동시성 제어 메커니즘**:
- Pessimistic Write Lock
- 사용자별 포인트는 1:1 관계 (userId UNIQUE)

**동시성 시나리오**:
```
시나리오 2: 동일 사용자의 동시 포인트 사용

초기 잔액: 10,000

Thread A: usePoint(userId=1, amount=6,000) → Lock 획득
Thread B: usePoint(userId=1, amount=5,000) → Lock 대기
Thread A: balance 10,000 → 4,000 → Commit → Lock 해제
Thread B: Lock 획득 → balance 4,000 - 5,000 → 검증 실패 (잔액 부족)

✅ 정상 동작: 비관적 락으로 순차 처리
```

**잠재적 이슈**:
1. **성능 병목**: 사용자당 포인트 연산 직렬화
2. **분산 환경 이슈**: 다중 인스턴스에서 DB 락 의존

**개선 방안**:
```kotlin
// 1. Redis 분산 락 (다중 인스턴스 환경)
@RedisLock(key = "#userId", waitTime = 3000)
fun chargePoint(userId: Long, amount: Int): PointModel

// 2. CAS(Compare-And-Set) 패턴
fun usePointWithCAS(userId: Long, amount: Int): PointModel {
    var retries = 0
    while (retries < 3) {
        val point = pointRepository.findByUserId(userId)
        val expectedBalance = point.balance
        point.usePoint(amount)

        val updated = pointRepository.updateWithCAS(
            point,
            expectedBalance
        )
        if (updated) return point
        retries++
    }
    throw ConcurrentModificationException()
}
```

##### 3) 결제 처리 (포인트 차감 + 좌석 확정)

**현재 구현**: `ProcessPaymentUseCase.kt:27-56`

```kotlin
@Transactional
fun execute(command: ProcessPaymentCommand): ProcessPaymentResult {
    // 1. 사용자/예약 검증
    val user = userService.findById(command.userId)
    val reservation = reservationService.findById(command.reservationId)
    val seat = seatService.findById(reservation.seatId)

    // 2. 포인트 차감 (Lock)
    pointService.usePoint(user.id, seat.price)
    pointHistoryService.savePointHistory(user.id, seat.price, TransactionType.USE)

    // 3. 결제 저장
    val payment = paymentService.savePayment(...)

    // 4. 좌석 확정 (Lock)
    seat.confirmReservation()
    seatService.update(seat)

    // 5. 예약 확정
    reservation.confirmPayment()
    reservationService.update(reservation)

    return ProcessPaymentResult(...)
}
```

**동시성 제어 메커니즘**:
- 트랜잭션 내 여러 엔티티 변경
- 포인트: Pessimistic Lock
- 좌석: 조회 시 락 미사용 (`findById`)

**동시성 시나리오**:
```
시나리오 3: 동일 예약에 대한 중복 결제 시도

Thread A: processPayment(reservationId=1) → 트랜잭션 시작
Thread B: processPayment(reservationId=1) → 트랜잭션 시작
Thread A: reservation.validatePayable() → 통과 (TEMPORARY)
Thread B: reservation.validatePayable() → 통과 (TEMPORARY)
Thread A: usePoint() → 차감
Thread B: usePoint() → 차감 (잔액 부족 or 이중 차감)
Thread A: confirmReservation() → Commit
Thread B: confirmReservation() → Commit (중복 결제)

❌ 문제: 예약 상태 검증과 업데이트 사이 Race Condition
```

**잠재적 이슈**:
1. **중복 결제**: 예약 엔티티에 락 미적용
2. **부분 실패**: 포인트는 차감되었으나 좌석 확정 실패 시
3. **Deadlock**: 포인트→좌석 순서와 좌석→포인트 순서 교차 시

**개선 방안**:
```kotlin
@Transactional
fun execute(command: ProcessPaymentCommand): ProcessPaymentResult {
    // 1. 예약에 락 적용 (중복 결제 방지)
    val reservation = reservationService.findByIdWithLock(command.reservationId)
    reservation.validateOwnership(user.id)
    reservation.validatePayable()

    // 2. 좌석에도 락 적용
    val seat = seatService.findByIdWithLock(reservation.seatId)

    try {
        // 3. 포인트 차감
        pointService.usePoint(user.id, seat.price)
        pointHistoryService.savePointHistory(...)

        // 4. 결제/좌석/예약 업데이트
        val payment = paymentService.savePayment(...)
        seat.confirmReservation()
        reservation.confirmPayment()

        return ProcessPaymentResult(...)
    } catch (e: Exception) {
        // 5. 보상 트랜잭션 (Saga 패턴 고려)
        throw PaymentFailedException("결제 실패", e)
    }
}

// Repository 추가
interface ReservationJpaRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdWithLock(id: Long): Reservation?
}
```

#### 🟡 Important - 모니터링 필요

##### 4) 대기열 토큰 활성화 (동시 활성화 수 제한)

**위치**: `QueueScheduler` (추론)

**동시성 시나리오**:
```
시나리오 4: 동시에 100명 활성화 vs 최대 50명 제한

Thread 1: activateTokens() → 대기 순서 1~50 활성화
Thread 2: activateTokens() → 대기 순서 1~50 활성화 (중복)

❌ 문제: 동시 실행 시 제한 수 초과
```

**개선 방안**:
```kotlin
@Scheduled(fixedDelay = 10000)
@SchedulerLock(
    name = "activateQueueTokens",
    lockAtMostFor = "9s",
    lockAtLeastFor = "5s"
)
fun activateQueueTokens() {
    // Redis ZSET으로 원자적 처리
}
```

##### 5) 임시 예약 만료 처리

**현재 구현**: `SeatScheduler` / `SeatService.kt:31-42`

**동시성 시나리오**:
```
시나리오 5: 스케줄러 중복 실행

Instance A: restoreExpiredTemporaryReservations() → Seat 1 복원
Instance B: restoreExpiredTemporaryReservations() → Seat 1 복원 (중복)

⚠️ 문제: 멱등성 보장되나 불필요한 업데이트
```

**개선 방안**:
```kotlin
@Scheduled(fixedDelay = 60000)
@SchedulerLock(name = "restoreExpiredSeats")
fun restoreExpiredSeats() {
    // DB 쿼리로 원자적 처리
    seatRepository.updateExpiredToAvailable(expirationMinutes = 5)
}

// JPA Native Query
@Modifying
@Query("""
    UPDATE seat
    SET seat_status = 'AVAILABLE', updated_at = NOW()
    WHERE seat_status = 'TEMPORARY_RESERVED'
      AND temporary_reserved_at < :expirationTime
""")
fun updateExpiredToAvailable(@Param("expirationTime") expirationTime: LocalDateTime): Int
```

---

### 2.2 동시성 제어 전략 요약

| 기능 | 현재 제어 방식 | 취약점 | 권장 개선 | 우선순위 |
|-----|-------------|--------|----------|---------|
| 좌석 예약 | Pessimistic Lock | 락 타임아웃, Deadlock | 낙관적 락 병행, 재시도 로직 | 🔴 P0 |
| 포인트 충전/사용 | Pessimistic Lock | 성능 병목 | Redis 분산 락, CAS 패턴 | 🟡 P1 |
| 결제 처리 | 트랜잭션 | 예약 락 미적용 | 예약/좌석 동시 락, Saga 패턴 | 🔴 P0 |
| 대기열 활성화 | 미적용 | 중복 활성화 | Scheduler Lock, Redis 원자성 | 🟡 P1 |
| 임시예약 만료 | 미적용 | 중복 처리 | Scheduler Lock, Bulk Update | 🟢 P2 |

---

## 3. 구현 로드맵

### Phase 1: 긴급 최적화 (1주)
1. ✅ Seat 테이블 인덱스 추가 (`idx_seat_schedule_status`)
2. ✅ 좌석 예약 동시성 강화 (락 타임아웃, 예외 처리)
3. ✅ 결제 처리 동시성 강화 (예약 락 추가)

### Phase 2: 성능 개선 (2주)
1. ✅ Reservation/PointHistory 인덱스 추가
2. ✅ 페이징 처리 구현
3. ✅ 스케줄러 분산 락 적용

### Phase 3: 확장성 대비 (4주)
1. ✅ Redis 분산 락 도입
2. ✅ 커버링 인덱스 적용
3. ✅ 파티셔닝 검토 (데이터 규모 기준)

---

## 4. 모니터링 지표

### 성능 메트릭
- 좌석 조회 응답 시간: P95 < 100ms
- 예약 생성 응답 시간: P95 < 200ms
- 결제 처리 응답 시간: P95 < 300ms
- 스케줄러 실행 시간: < 1초

### 동시성 메트릭
- Lock 대기 시간: P99 < 3초
- Deadlock 발생률: < 0.01%
- 트랜잭션 롤백률: < 0.1%
- 중복 예약 차단률: 100%

### 비즈니스 메트릭
- 예약 성공률: > 95%
- 결제 성공률: > 98%
- 포인트 정합성: 100%

---

## 5. 참고 자료

### 데이터베이스 인덱스 설계
- [MySQL Index Optimization](https://dev.mysql.com/doc/refman/8.0/en/optimization-indexes.html)
- [Covering Index Best Practices](https://use-the-index-luke.com/sql/clustering/index-organized-clustered-index)

### 동시성 제어 패턴
- [Pessimistic vs Optimistic Locking](https://vladmihalcea.com/optimistic-vs-pessimistic-locking/)
- [Distributed Locks with Redis](https://redis.io/docs/manual/patterns/distributed-locks/)
- [Saga Pattern for Microservices](https://microservices.io/patterns/data/saga.html)

### JPA 락 전략
- [JPA Locking Mechanisms](https://www.baeldung.com/jpa-pessimistic-locking)
- [Hibernate Query Hints](https://vladmihalcea.com/hibernate-query-hints/)
