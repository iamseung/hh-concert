# Section 9 과제: Kafka 기초 학습 및 적용

## 과제 요약

### 필수 과제
1. ✅ Kafka 기초 개념 학습 및 문서화 → `kafka.md`
2. ✅ 로컬 Kafka 클러스터 설치 및 실습 → Docker Compose 3-broker 클러스터
3. ✅ 프로젝트에 Kafka 적용 → 예약 확정 이벤트 처리

### 선택 과제
1. ✅ 대용량 요청 지점 분석 → 알림 시스템, 좌석 만료 처리
2. ✅ 개선 설계 문서 작성 → 실시간 알림, 비동기 좌석 복원
3. ✅ Kafka 기반 개선 구현 → Notification, SeatExpiration Consumer

---

## 1. Kafka 환경 구성

### 선택: Docker Compose + 3-broker 클러스터

**선택 이유**
- 로컬 개발 환경에서 실전과 유사한 클러스터 구조 경험
- 고가용성, 복제, 파티셔닝 등 핵심 기능 학습 가능
- 별도 파일(`docker-compose.kafka.yml`)로 독립적 관리

**실행 방법**
```bash
# Kafka 클러스터 시작
docker-compose -f docker-compose.kafka.yml up -d

# 토픽 생성
docker exec -it kafka-1 kafka-topics --create \
  --topic reservation.confirmed \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server kafka-1:29092
```

---

## 2. 프로젝트 Kafka 적용

### Before (Spring Event 기반)
```
[결제 완료]
  → @TransactionalEventListener
  → ReservationEventListener
  → DataPlatformClient (HTTP 직접 호출)
```

**문제점**
- 외부 API 장애 시 재처리 어려움
- 트래픽 급증 시 시스템 과부하
- 단일 컨슈머만 처리 가능

### After (Kafka 기반)
```
[결제 완료]
  → @TransactionalEventListener
  → Kafka Producer
  → Kafka Cluster (영속화)
  → ReservationKafkaConsumer
  → DataPlatformClient
```

**개선 효과**
- 메시지 영속화로 장애 복구 가능
- 백프레셔 처리로 시스템 안정성 향상
- Consumer Group으로 수평 확장 가능

### 주요 설정 (application.yml)
```yaml
kafka:
  bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
  producer:
    acks: all  # 모든 복제본 확인
    properties:
      enable.idempotence: true  # 멱등성 프로듀서
  consumer:
    enable-auto-commit: false  # 수동 커밋 (At-Least-Once)
```

### 구현 핵심

**1. Producer (ReservationEventListener.kt)**
```kotlin
@TransactionalEventListener
fun onReservation(event: ReservationConfirmedEvent) {
    kafkaTemplate.send(
        topic,
        event.reservationId.toString(),  // Key → 순서 보장
        event
    )
}
```

**2. Consumer (ReservationKafkaConsumer.kt)**
```kotlin
@KafkaListener(topics = ["reservation.confirmed"])
fun consume(event: ReservationConfirmedEvent, ack: Acknowledgment) {
    dataPlatformClient.sendReservation(event)
        .doOnSuccess { ack.acknowledge() }  // 성공 시에만 커밋
        .block()
}
```

**3. DLQ (ReservationDLQConsumer.kt)**
- 3회 재시도 후 실패한 메시지를 DLQ로 전송
- 관리자가 수동으로 확인 및 재처리

---

## 3. 비즈니스 프로세스 개선

### 개선 1: 실시간 알림 시스템

**문제 정의**
- 예약/결제 완료 시 사용자에게 알림 없음
- 사용자 경험 저하 (즉각적인 피드백 부재)

**Kafka 적용 방안**
```
[예약/결제 완료]
  → NotificationEventListener
  → Kafka (notifications.payment-completed)
  → NotificationKafkaConsumer
  → Discord/Firebase Push
```

**구현 핵심**
- **Notifier 인터페이스**: 알림 채널 추상화 (Discord, Kakao, Firebase, SMS)
- **NotificationMessage DTO**: 채널 독립적인 범용 메시지 구조
- **DiscordNotifier**: Discord Webhook 구현체 (향후 확장 가능)

**효과**
- ✅ 예약/결제 완료 즉시 사용자에게 알림 발송
- ✅ 알림 실패가 메인 비즈니스 로직에 영향 없음
- ✅ 다양한 채널 독립적 확장 가능

### 개선 2: 좌석 만료 처리 비동기화

**문제 정의**
- SeatScheduler가 5분마다 만료된 좌석을 동기식으로 복원
- 피크 시간대 대량 좌석 복원 시 DB 부하 및 블로킹

**Kafka 적용 방안**
```
[SeatScheduler]
  → 만료된 좌석 ID 조회
  → Kafka (seats.expired)
  → SeatExpirationConsumer
  → 배치 단위 좌석 복원
```

**구현 핵심**
- **SeatScheduler**: 만료 확인 후 Kafka로 이벤트 발행 (비동기)
- **SeatExpirationConsumer**: 배치 복원 + 캐시 무효화 + Discord 알림

**효과**
- ✅ 스케줄러 블로킹 제거
- ✅ Consumer가 배치 처리하여 DB 부하 분산
- ✅ Consumer 수평 확장 가능

---

## 4. 멱등성(Idempotency) 보장

Kafka는 At-Least-Once 보장 → 중복 처리 가능 → 멱등성 필수

| Consumer | 전략 |
|----------|------|
| ReservationKafkaConsumer | Redis 기반 중복 체크 + Idempotency Key |
| NotificationKafkaConsumer | 중복 허용 (비즈니스적으로 허용 가능) |
| SeatExpirationConsumer | 자연적 멱등성 (상태 조건 체크) |

**ReservationKafkaConsumer - Redis 중복 체크**
```kotlin
val idempotencyKey = "kafka:reservation:processed:${event.reservationId}"
val isNewMessage = stringRedisTemplate.opsForValue()
    .setIfAbsent(idempotencyKey, Instant.now().toString(), Duration.ofHours(24))

if (isNewMessage == false) {
    logger.info("이미 처리된 메시지 스킵")
    ack.acknowledge()
    return
}
```

**SeatExpirationConsumer - 자연적 멱등성**
```sql
UPDATE Seat
SET seatStatus = 'AVAILABLE'
WHERE id IN :seatIds
  AND seatStatus = 'TEMPORARY_RESERVED'  -- 핵심: 상태 조건
```

---

## 5. 아키텍처 개선 효과

### Interface 기반 알림 시스템 리팩토링

**Before**
```kotlin
class DiscordNotifier {
    fun sendPaymentNotification(userId: Long, amount: Long, ...)
}
```

**After**
```kotlin
interface Notifier {
    suspend fun send(message: NotificationMessage)
}

class DiscordNotifier : Notifier { ... }
```

**개선 효과**
- ✅ **확장성**: 새로운 알림 채널 추가 시 Consumer 코드 변경 불필요
- ✅ **단일 책임**: Consumer는 메시지 생성, Notifier는 전송만 담당
- ✅ **테스트 용이성**: Mock Notifier로 Consumer 단위 테스트 가능

### 전체 시스템 아키텍처

```
┌─────────────────────────────────────────┐
│         API Server (Spring Boot)         │
│  ├─ ReservationUseCase                  │
│  ├─ PaymentUseCase                      │
│  └─ SeatScheduler                       │
└───┬───┬───┬────────────────────────────┘
    │   │   │
    │   │   └──────► Kafka Cluster (3-broker)
    │   │            ├─ reservation.confirmed
    │   │            ├─ reservation.confirmed.dlq
    │   │            ├─ notifications.payment-completed
    │   │            └─ seats.expired
    │   │
    │   ├──────────► Redis (캐시, 멱등성 체크)
    │   └──────────► MySQL (트랜잭션 데이터)
    │
    ├──────────────► ReservationKafkaConsumer
    │                └─ Data Platform Client
    │
    ├──────────────► NotificationConsumer
    │                └─ Discord/Firebase Push
    │
    └──────────────► SeatExpirationConsumer
                     └─ 좌석 복원 (Bulk Update)
```

---

## 6. 핵심 성과

### 사용자 경험 향상
- 예약/결제 완료 즉시 실시간 알림
- 명확한 상태 전달로 불안감 해소

### 시스템 성능 개선
- 스케줄러 블로킹 제거 → 응답성 향상
- 대량 좌석 복원을 배치 처리 → DB 부하 완화
- Consumer 수평 확장 가능 → 트래픽 증가 대응

### 운영 안정성 강화
- 장애 격리 (알림 실패 ≠ 비즈니스 로직 실패)
- DLQ로 메시지 유실 방지 및 재처리 가능
- 멱등성 보장으로 중복 처리 방지

### 확장성 확보
- Interface 기반 설계로 새로운 알림 채널 추가 용이
- Consumer Group으로 수평 확장 가능
- 토픽 추가만으로 새로운 기능 확장 가능

---

## 7. 향후 개선 사항

1. **Firebase 연동**: Discord 대신 실제 Push 알림
2. **SMS 추가**: AWS SNS 연동
3. **예약 완료 알림**: 좌석 임시 예약 시 알림 추가
4. **다중 채널 발송**: MultiChannelNotifier로 Push + SMS 동시 발송
5. **알림 이력 저장**: NotificationHistory 테이블에 발송 이력 기록
