# 동시성 테스트

## 목적

동시성 이슈를 미리 발견하고 Rule로 가두어 시스템 안정성을 보장합니다.

> ⚠️ **이 테스트는 성공하는 것이 목적이 아니라, 동시성 이슈가 예민한 기능을 Rule로 가두는 것이 목적입니다.**

---

## 테스트 파일

- `SeatConcurrencyTest.kt`: 좌석 Pessimistic Lock 동작 검증
- `PointConcurrencyTest.kt`: 포인트 잔액 정합성 검증

---

## Rule 정의

### RULE-001: 동일 좌석 중복 Lock 방지
- **조건**: 동일 seatId에 대한 동시 Lock 시도
- **제어**: Pessimistic Write Lock (`findByIdWithLock`)
- **결과**: Lock은 순차 획득, 첫 번째만 상태 변경 성공

### RULE-002: 포인트 잔액 정합성
- **조건**: 동일 userId에 대한 동시 충전/사용
- **제어**: Pessimistic Write Lock (`findByUserIdWithLock`)
- **결과**: 최종 잔액 = 초기 + Σ충전 - Σ사용

### RULE-003: 잔액 부족 시 Lost Update 방지
- **조건**: 잔액보다 많은 금액 사용 시도
- **제어**: Lock + 잔액 검증
- **결과**: 첫 번째만 성공, 나머지 실패

---

## 실행 방법

```bash
# 전체 테스트
./gradlew test --tests "kr.hhplus.be.server.concurrency.*"

# 개별 테스트
./gradlew test --tests "kr.hhplus.be.server.concurrency.SeatConcurrencyTest"
./gradlew test --tests "kr.hhplus.be.server.concurrency.PointConcurrencyTest"
```

---

## 테스트 시나리오

### 1. SeatConcurrencyTest

#### TC1: 동일 좌석 10개 스레드 동시 Lock
- 10개 스레드가 동시에 동일 좌석 Lock 획득 시도
- **기대**: 1개 성공, 9개 이미 예약됨
- **검증**: `successCount == 1, alreadyReservedCount == 9`

#### TC2: 서로 다른 좌석 5개 동시 Lock
- 5개 스레드가 각각 다른 좌석 Lock 획득
- **기대**: 5개 모두 성공
- **검증**: `successCount == 5, 모든 좌석 TEMPORARY_RESERVED`

### 2. PointConcurrencyTest

#### TC1: 50회 동시 충전
- 초기 10,000원에서 50회 × 1,000원 충전
- **기대**: 최종 잔액 60,000원
- **검증**: `balance == 60000`

#### TC2: 충전 20회 + 사용 20회 혼합
- 초기 50,000원
- 충전 20회 × 1,000원 + 사용 20회 × 500원
- **기대**: 최종 잔액 = 50,000 + 20,000 - 10,000 = 60,000원
- **검증**: `balance == 60000`

#### TC3: 잔액 부족 시나리오
- 초기 10,000원에서 3명이 각각 6,000원 사용 시도
- **기대**: 1개 성공, 2개 실패
- **검증**: `successCount == 1, failCount == 2, balance == 4000`

---

## Pessimistic Lock 적용 위치

**SeatJpaRepository**:
```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.id = :id")
fun findByIdWithLock(id: Long): Seat?
```

**PointJpaRepository**:
```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Point p WHERE p.userId = :userId")
fun findByUserIdWithLock(userId: Long): Point?
```

---

## 참고 자료

- [JPA Pessimistic Locking](https://www.baeldung.com/jpa-pessimistic-locking)
- [MySQL Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)
