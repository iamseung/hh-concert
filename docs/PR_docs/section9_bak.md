# 과제 사항
## [필수] 카프카 기초 학습 및 활용
1. 카프카에 대한 기초 개념을 학습하고 문서로 작성
    - 카프카가 무엇인지, 얻는 이점은 어떤 것인지를 포함 (도입 과정을 예시로)
        - 장점 / 단점 명확히
    - 카프카 시스템의 특징, 어떤 요소로 구성되어 있는지, 제공하는 대표적인 기능들
    - 이벤트의 아이디어를 시스템 전체의 관점으로 확장시켜서 적용할 수 있다는
2. 로컬에서 카프카를 설치하고 기본적인 기능을 실습
    - docker-compose, 카프카 클러스터를 virtual Machine 등을 통해서 브로커를 세팅해서 연결해도 됨, AWS MSK
3. 진행하고 있는 시나리오에 맞추어 프로젝트에 카프카 적용
    - 기존에 이벤트로 되어 있는 것을 카프카로 대체, 카프카 클러스터에 접속하는 설정 등

## [선택] 카프카를 활용한 비즈니스 프로세스 개선
1. 대용량 요청이 발생할 수 있는 지점을 고민 → 카프카를 활용하면 좋을 포인트
2. 개선할 내용에 대한 설계 문서 작성
3. 설계한 내용을 바탕으로 실제 카프카를 활용하여 대응하도록 변경

---
# [필수] 카프카 기초 학습 및 활용
## 🟢 1. 카프카에 대한 기초 개념을 학습하고 문서로 작성
- kafka.md 파일을 참고

## 🟢 2. 로컬에서 카프카를 설치하고 기본적인 기능을 실습

### 카프카 설치 방식 선택

**선택: Docker Compose + 3-broker 클러스터**

#### 1. Docker Compose vs Virtual Machine

**Docker Compose 선택 이유:**
- 로컬 개발 환경에서 빠르고 간편한 설정 및 실행
- VM 대비 리소스 효율적 (동일 호스트에서 여러 브로커 운영 가능)
- 환경 재구성이 용이 (docker-compose down/up으로 즉시 초기화)
- 학습 및 테스트 목적에 최적화된 방식
- 설정 파일(docker-compose.yml)로 인프라를 코드화하여 관리 용이

**VM을 선택하지 않은 이유:**
- 초기 설정 복잡도가 높고 시간 소모적
- 로컬 머신의 리소스(CPU, 메모리) 과다 사용
- 학습 및 개발 목적에는 오버스펙
- 빠른 반복 테스트가 어려움

#### 2. 단일 브로커 vs 클러스터

**3-broker 클러스터 선택 이유:**
- 카프카의 핵심 기능인 **고가용성, 복제, 파티셔닝**을 실제로 학습 가능
- 실제 프로덕션 환경과 유사한 구조를 경험
- 브로커 장애 시나리오 테스트 가능 (1개 브로커 다운 시에도 서비스 지속)
- 토픽의 파티션이 여러 브로커에 분산되어 **부하 분산** 효과 확인 가능
- 리더 선출(Leader Election) 및 팔로워 복제(Replication) 메커니즘 이해
- 과제 요구사항에 "카프카 클러스터" 명시적 언급

**단일 브로커를 선택하지 않은 이유:**
- 카프카의 핵심 장점인 분산 처리, 복제, 고가용성을 경험할 수 없음
- 실제 운영 환경과의 괴리가 커서 학습 효과 제한적
- 콘서트 예약 서비스와 같은 트래픽 집중 시나리오에서의 안정성 검증 불가

#### 3. Confluent Kafka vs Apache Kafka 이미지

**Confluent Kafka 이미지 선택 이유:**
- Docker 환경에 최적화된 설정 (환경 변수를 통한 직관적인 설정)
- Kafka 원 개발자들이 만든 Confluent 사의 검증된 이미지
- 프로덕션 환경에서 가장 널리 사용되며 풍부한 커뮤니티 문서 제공
- Schema Registry, KSQL 등 확장 도구와의 쉬운 통합
- Apache 2.0 라이선스로 무료 사용 가능

**Apache Kafka 공식 이미지 대비:**
- Confluent 이미지가 Docker 환경 설정이 더 간편함
- 환경 변수 지원이 더 직관적이고 풍부함
- 로컬 개발 및 학습 목적에 실용적

#### 4. Docker Compose 파일 구성

**별도 파일(docker-compose.kafka.yml) 선택:**
- 기존 docker-compose.yml에는 MySQL, Redis, Jenkins가 이미 존재
- 카프카는 독립적으로 실행/중지할 수 있어야 함 (개발 시 선택적 사용)
- 파일 분리로 관심사의 분리(Separation of Concerns) 달성
- 카프카 클러스터만 재시작하거나 설정 변경 시 유리

**실행 방법:**
```bash
# 기본 인프라(MySQL, Redis, Jenkins)
docker-compose up -d

# 카프카 클러스터 (필요 시)
docker-compose -f docker-compose.kafka.yml up -d
```

#### 5. 결론

Docker Compose + 3-broker 클러스터 구성은:
- 로컬 환경의 제약 내에서 **실전과 유사한 카프카 환경** 구축
- 학습 목적과 프로덕션 준비를 동시에 충족
- 콘서트 예약 서비스의 이벤트 기반 아키텍처 전환에 최적화된 선택
- 향후 AWS MSK 등 클라우드 환경으로 마이그레이션 시에도 동일한 개념 적용 가능

## 🟢 3. 진행하고 있는 시나리오에 맞추어 프로젝트에 카프카 적용

### 기존 아키텍처 vs Kafka 적용 아키텍처

#### Before (Spring Event 기반)
```
[결제 완료]
  → @TransactionalEventListener
  → ReservationEventListener
  → DataPlatformClient (HTTP 직접 호출)
  → 외부 API
```

**문제점:**
- 외부 API 장애 시 재처리 어려움 (메모리에만 존재)
- 트래픽 급증 시 시스템 과부하
- 단일 컨슈머만 처리 가능 (확장성 제한)
- 이벤트 유실 가능성

#### After (Kafka 기반)
```
[결제 완료]
  → @TransactionalEventListener
  → ReservationEventListener (Producer)
  → Kafka Cluster (영속화)
  → ReservationKafkaConsumer
  → DataPlatformClient
  → 외부 API
```

**개선 효과:**
- 메시지 영속화로 장애 복구 가능
- 백프레셔 처리로 시스템 안정성 향상
- Consumer Group으로 수평 확장 가능
- 여러 컨슈머가 독립적으로 처리 가능

### 구현 내용

#### 1. 의존성 추가 (build.gradle.kts)
```kotlin
// Kafka
implementation("org.springframework.kafka:spring-kafka")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
```

#### 2. Kafka 설정 (application.yml)
```yaml
kafka:
  bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
  topics:
    reservation-confirmed: reservation.confirmed
  producer:
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    acks: all  # 모든 복제본 확인
    retries: 3
    properties:
      enable.idempotence: true  # 멱등성 프로듀서
  consumer:
    group-id: hhplus-reservation-consumer
    auto-offset-reset: earliest
    enable-auto-commit: false  # 수동 커밋
```

**주요 설정 설명:**
- `bootstrap-servers`: 3개 브로커 클러스터 연결
- `acks=all`: 모든 복제본이 확인해야 성공 (신뢰성 최대화)
- `enable.idempotence=true`: 중복 메시지 방지
- `enable-auto-commit=false`: 처리 완료 후 수동 커밋 (At-Least-Once)

#### 3. Producer/Consumer Config
- `KafkaProducerConfig.kt`: Producer 팩토리 및 KafkaTemplate 설정
- `KafkaConsumerConfig.kt`: Consumer 팩토리 및 ErrorHandlingDeserializer 설정

#### 4. 이벤트 발행 (ReservationEventListener.kt)
**변경 전:**
```kotlin
@TransactionalEventListener
fun onReservation(event: ReservationConfirmedEvent) {
    dataPlatformClient.sendReservation(event).subscribe()
}
```

**변경 후:**
```kotlin
@TransactionalEventListener
fun onReservation(event: ReservationConfirmedEvent) {
    kafkaTemplate.send(topic, event.reservationId.toString(), event)
    // Key: reservationId → 동일 예약은 같은 파티션으로 순서 보장
}
```

#### 5. 이벤트 소비 (ReservationKafkaConsumer.kt)
```kotlin
@KafkaListener(topics = ["reservation.confirmed"])
fun consume(
    @Payload event: ReservationConfirmedEvent,
    acknowledgment: Acknowledgment
) {
    dataPlatformClient.sendReservation(event)
        .doOnSuccess { acknowledgment.acknowledge() }  // 성공 시 커밋
        .block()
}
```

**특징:**
- 수동 커밋: 외부 API 성공 후에만 오프셋 커밋
- 재처리: 실패 시 커밋하지 않아 자동 재시도
- 파티션별 순서 보장: Key 기반 파티셔닝

### 테스트 방법

#### 1. Kafka 클러스터 실행
```bash
docker-compose -f docker-compose.kafka.yml up -d
```

#### 2. 토픽 생성
```bash
docker exec -it kafka-1 kafka-topics --create \
  --topic reservation.confirmed \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server kafka-1:29092
```

#### 3. 애플리케이션 실행 및 예약 생성
- 결제 완료 시 Kafka로 메시지 발행
- Consumer가 메시지를 수신하여 외부 API 호출
- Kafka UI (http://localhost:8989)에서 메시지 확인

#### 4. 메시지 확인
```bash
# Consumer로 메시지 확인
docker exec -it kafka-1 kafka-console-consumer \
  --topic reservation.confirmed \
  --from-beginning \
  --bootstrap-server kafka-1:29092
```

### Kafka 적용 효과

#### 1. 안정성 (Reliability)
- 메시지 영속화: 브로커 재시작 후에도 메시지 보존
- 복제본 3개: 브로커 2개 장애 시에도 데이터 유지
- At-Least-Once 보장: 처리 완료 후 커밋

#### 2. 확장성 (Scalability)
- Consumer Group: 파티션별 병렬 처리
- 수평 확장: Consumer 인스턴스 추가로 처리량 증가
- 백프레셔: Producer가 빠르더라도 Consumer가 감당 가능한 속도로 처리

#### 3. 유연성 (Flexibility)
- 느슨한 결합: Producer와 Consumer 독립적 배포
- 다중 구독: 같은 이벤트를 여러 시스템이 독립적으로 소비 가능
- 이벤트 재처리: 오프셋 리셋으로 과거 데이터 재처리

#### 4. 관찰성 (Observability)
- Kafka UI로 메시지 흐름 실시간 모니터링
- 파티션, 오프셋, Lag 확인
- Consumer Group별 처리 상태 추적

---

# [선택] 카프카를 활용한 비즈니스 프로세스 개선

## 🟢 1. 대용량 요청 발생 지점 분석

콘서트 예약 시스템에서 **Kafka 적용이 필요한 핵심 지점**을 분석했습니다:

#### 1.1. 예약/결제 완료 시 알림 부재
```
현재 상황:
- 좌석 예약 완료, 결제 완료 시 사용자에게 알림 없음
- 사용자는 페이지를 새로고침하거나 마이페이지에서 확인 필요
- 중요한 비즈니스 이벤트 발생 시점에 즉각적인 피드백 부재

문제점:
- 사용자 경험 저하 (예약/결제 성공 여부를 즉시 알 수 없음)
- Push 알림, SMS, 이메일 등 다채널 알림 부재
- 알림 전송 로직이 메인 트랜잭션과 결합되어 있으면 성능 저하
```

**개선 필요성:**
- 예약 완료 시 즉시 Push 알림으로 사용자 만족도 향상
- 알림 전송 실패가 메인 비즈니스 로직에 영향 없도록 격리
- 다양한 알림 채널(Push/SMS/Email) 독립적 처리

#### 1.2. 좌석 만료 처리의 동기 처리 부하
```
현재 상황:
- SeatScheduler가 5분마다 만료된 좌석을 동기식으로 복원
- 피크 시간대 수백 개 좌석 동시 만료 시 DB 부하 발생
- 대량 좌석 복원 시 처리 시간 지연 가능

문제점:
- 좌석 복원 작업이 스케줄러 스레드를 블로킹
- 대량 UPDATE 쿼리로 인한 DB 부하
- 캐시 무효화가 동기식으로 처리되어 성능 저하
```

**개선 필요성:**
- 좌석 만료 이벤트를 Kafka로 발행하여 비동기 처리
- Consumer가 배치 단위로 처리하여 DB 부하 분산
- 좌석 복원 완료 시 캐시 무효화 이벤트 발행

#### 1.3. 예약 확정 후 외부 API 연동 (이미 Kafka로 구현됨)
```
✅ 현재 Kafka로 처리 중:
- 예약 확정 → Kafka (reservation.confirmed) → DataPlatformClient
- DLQ 구현 완료 (reservation.confirmed.dlq)
- 외부 API 장애 격리 완료
- Resilience4j 재시도 + 서킷브레이커 적용
```

## 🟢 2. Kafka 활용 개선 설계

### 개선 1: 🔔 실시간 알림 시스템

**문제 정의:**
- 좌석 예약 완료, 결제 완료 등 중요 이벤트 발생 시 사용자에게 알림 없음
- 알림 전송 로직이 메인 트랜잭션과 결합되면 성능 저하 우려
- 알림 전송 실패 시 메인 비즈니스 로직에 영향 가능성

**Kafka 적용 방안:**

```
[예약/결제 완료]
  → Spring @TransactionalEventListener
  → Kafka Topic (notifications.{type})
  → NotificationConsumer
  → 알림 채널별 분기 (Push/SMS/Email)
  → 외부 알림 서비스 (Firebase, AWS SNS)
```

**토픽 설계:**
```yaml
notifications.reservation-completed # 좌석 예약 완료
notifications.payment-completed     # 결제 완료
```

**이벤트 예시:**
```kotlin
// 1. 예약 완료 이벤트
data class ReservationCompletedEvent(
    val userId: Long,
    val reservationId: Long,
    val concertTitle: String,
    val seatNumber: String,
    val reservedAt: Instant,
    val expiresAt: Instant, // 임시 예약 만료 시간
)

// 2. 결제 완료 이벤트
data class PaymentCompletedEvent(
    val userId: Long,
    val reservationId: Long,
    val concertTitle: String,
    val amount: Long,
    val paidAt: Instant,
)

// 발행 위치: ReservationUseCase, PaymentUseCase
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onReservationCompleted(event: ReservationCompletedEvent) {
    kafkaTemplate.send(
        "notifications.reservation-completed",
        event.userId.toString(),
        event
    )
}
```

**Consumer 구현:**
```kotlin
@KafkaListener(topics = ["notifications.reservation-completed"])
fun consumeReservationNotification(event: ReservationCompletedEvent) {
    // Push 알림 발송
    firebasePushService.send(
        userId = event.userId,
        title = "좌석 예약 완료",
        body = "${event.concertTitle} - ${event.seatNumber} 예약이 완료되었습니다!"
    )

    // SMS 발송 (선택)
    smsService.send(event.userId, "예약 완료 안내...")

    acknowledgment.acknowledge()
}
```

**효과:**
- ✅ 예약/결제 완료 즉시 사용자에게 Push 알림 발송
- ✅ 알림 전송 실패가 메인 비즈니스 로직에 영향 없음 (격리)
- ✅ DLQ로 실패한 알림 재시도 보장
- ✅ Push/SMS/Email 등 다양한 채널 독립적 확장 가능

---

### 개선 2: 🔄 좌석 만료 처리 비동기화

**문제 정의:**
- SeatScheduler가 5분마다 만료된 좌석을 동기식으로 대량 복원
- 피크 시간대 수백 개 좌석 동시 만료 시 DB 부하 발생
- 스케줄러 스레드가 블로킹되어 다음 스케줄 지연 가능성

**Kafka 적용 방안:**

```
[SeatScheduler]
  → 만료된 좌석 ID 조회
  → Kafka Topic (seats.expired)
  → SeatExpirationConsumer
  → 배치 단위로 좌석 복원 (Bulk Update)
  → 캐시 무효화 이벤트 발행 (선택)
```

**토픽 설계:**
```yaml
seats.expired  # 좌석 만료 이벤트
```

**이벤트 및 구현:**
```kotlin
// 1. 좌석 만료 이벤트
data class SeatExpiredEvent(
    val seatIds: List<Long>,
    val scheduleId: Long,
    val expiredAt: Instant,
)

// 2. SeatScheduler 수정 (Producer)
@Scheduled(fixedDelay = 300000) // 5분
fun expireReservations() {
    val expiredSeatIds = reservationService.findExpiredReservationSeatIds(now)

    if (expiredSeatIds.isEmpty()) return

    // Kafka로 이벤트 발행 (비동기)
    kafkaTemplate.send(
        "seats.expired",
        SeatExpiredEvent(
            seatIds = expiredSeatIds,
            scheduleId = scheduleId,
            expiredAt = Instant.now()
        )
    )

    logger.info("Published ${expiredSeatIds.size} expired seats to Kafka")
}

// 3. Consumer 구현
@KafkaListener(topics = ["seats.expired"])
fun consumeSeatExpiration(event: SeatExpiredEvent, ack: Acknowledgment) {
    try {
        // 배치로 좌석 복원
        val restoredCount = seatService.restoreExpiredSeats(event.seatIds)

        logger.info("Restored $restoredCount seats for schedule ${event.scheduleId}")

        // (선택) 캐시 무효화
        cacheManager.evict("availableSeats", event.scheduleId)

        ack.acknowledge()
    } catch (e: Exception) {
        logger.error("Failed to restore seats: ${e.message}", e)
        // DLQ로 전송
    }
}
```

**효과:**
- ✅ 좌석 복원 작업이 스케줄러에서 분리되어 블로킹 제거
- ✅ Consumer가 배치 단위로 처리하여 DB 부하 분산
- ✅ 대량 좌석 만료 시에도 안정적 처리
- ✅ Consumer 수평 확장 가능 (파티션별 병렬 처리)

---

### 3. 구현 우선순위

**우선순위 1: 실시간 알림 시스템** ⭐⭐⭐⭐⭐
- 예상 효과: 사용자 만족도 대폭 향상, 즉각적인 피드백 제공
- 구현 복잡도: 중
- ROI: 매우 높음
- 외부 의존성: Firebase/AWS SNS (Push 알림 서비스)

**우선순위 2: 좌석 만료 처리 비동기화** ⭐⭐⭐⭐
- 예상 효과: 스케줄러 성능 향상, DB 부하 분산
- 구현 복잡도: 낮음
- ROI: 높음
- 외부 의존성: 없음 (Kafka만 필요)

### 4. 예상 시스템 아키텍처 (개선 후)

```
┌─────────────────────────────────────────────────────────────┐
│                        Client                                │
│  (Web, Mobile App)                                           │
└──────────────────┬──────────────────────────────────────────┘
                   │ HTTP
                   │ ↑ Push Notification
┌──────────────────▼──────────────────────────────────────────┐
│                  API Server (Spring Boot)                    │
│                                                               │
│  UseCase Layer:                                              │
│  ├─ ReservationUseCase → @TransactionalEventListener        │
│  └─ PaymentUseCase → @TransactionalEventListener            │
└──┬───┬───┬────────────────────────────────────────────────┬─┘
   │   │   │                                                 │
   │   │   │                                                 └─► SeatScheduler
   │   │   │                                                     (좌석 만료 조회)
   │   │   │
   │   │   └──────────► Kafka Cluster (3-broker)
   │   │                ├─ reservation.confirmed (기존)
   │   │                ├─ reservation.confirmed.dlq (기존)
   │   │                ├─ notifications.reservation-completed (신규)
   │   │                ├─ notifications.payment-completed (신규)
   │   │                └─ seats.expired (신규)
   │   │
   │   ├──────────────► Redis (캐시, 대기열)
   │   └──────────────► MySQL (트랜잭션 데이터)
   │
   ├──────────────────► ReservationKafkaConsumer (기존)
   │                    └─ Data Platform Client
   │
   ├──────────────────► NotificationConsumer (신규)
   │                    ├─ Push Notification (Firebase/AWS SNS)
   │                    └─ SMS (선택)
   │
   └──────────────────► SeatExpirationConsumer (신규)
                        └─ 좌석 복원 (Bulk Update)
```

**핵심 변경사항:**
- ✅ 예약/결제 완료 이벤트 → Kafka → Push 알림
- ✅ 좌석 만료 이벤트 → Kafka → 비동기 복원
- ✅ DLQ 구현으로 메시지 유실 방지

---

### 5. 기대 효과

#### 5.1. 사용자 경험 향상
- **실시간 피드백**: 예약/결제 완료 즉시 Push 알림으로 사용자 만족도 향상
- **명확한 상태 전달**: "좌석 예약이 완료되었습니다" 알림으로 불안감 해소
- **다채널 지원**: Push/SMS 선택적 발송으로 다양한 사용자 니즈 충족

#### 5.2. 시스템 성능 개선
- **스케줄러 블로킹 제거**: 좌석 복원이 비동기로 처리되어 스케줄러 응답성 향상
- **DB 부하 분산**: 대량 좌석 복원을 Consumer가 배치 처리하여 피크 부하 완화
- **Consumer 수평 확장**: 트래픽 증가 시 Consumer 인스턴스 추가로 대응 가능

#### 5.3. 운영 안정성 강화
- **장애 격리**: 알림 전송 실패가 메인 비즈니스 로직에 영향 없음
- **메시지 유실 방지**: DLQ로 실패한 메시지 추적 및 재처리 가능
- **모니터링**: Kafka UI로 메시지 흐름 및 Consumer Lag 실시간 확인

---

### 6. 리스크 및 대응 방안

#### 리스크 1: Kafka 클러스터 장애
**대응:**
- 3-broker 클러스터 + Replication Factor 3으로 고가용성 확보
- 브로커 1~2대 장애 시에도 서비스 지속 가능
- 필수 기능(예약/결제)은 Kafka 없이도 동작 (알림만 실패)

#### 리스크 2: Consumer 장애로 메시지 적체
**대응:**
- DLQ 구현으로 메시지 유실 방지
- Consumer Group으로 여러 인스턴스 운영 (고가용성)
- Consumer Lag 모니터링 및 알림 설정

#### 리스크 3: 외부 알림 서비스 장애 (Firebase, AWS SNS)
**대응:**
- 알림 전송 실패 시 DLQ로 재시도
- 재시도 횟수 제한 (Resilience4j)으로 무한 재시도 방지
- 알림 실패는 메인 비즈니스에 영향 없음 (격리됨)

#### 리스크 4: 메시지 순서 보장
**대응:**
- 동일 사용자 이벤트는 userId를 Key로 사용 (같은 파티션 할당)
- 파티션 내에서는 메시지 순서 보장됨
- 좌석 만료는 순서 무관하므로 문제 없음

---

### 7. 향후 확장 가능성

현재 설계한 2가지 개선안 외에도, 필요 시 다음과 같은 확장이 가능합니다:

- **분석 데이터 수집**: 사용자 행동 로그를 Kafka로 수집하여 Data Warehouse 연동
- **감사 로그**: 예약/결제 이벤트를 Audit Database에 장기 보관
- **대기열 활성화 알림**: 대기 중인 사용자에게 "지금 예약하세요" Push 발송

이러한 확장은 현재 구조에 토픽과 Consumer만 추가하면 되므로, 기존 시스템 변경 없이 점진적 확장 가능합니다.

---

## 🟢 3. 설계 기반 Kafka 구현 완료

### 구현 개요

설계 문서를 바탕으로 **실시간 알림 시스템**과 **좌석 만료 처리 비동기화**를 Kafka를 활용하여 구현했습니다.

### 구현 내용

#### 1. 실시간 알림 시스템

**1.1. Discord 알림 서비스**
- 파일: `DiscordNotifier.kt`
- 역할: Discord Webhook을 통한 실시간 알림 발송
- 기능:
  - 결제 완료 알림
  - 좌석 만료 처리 완료 알림
  - 에러 알림

**1.2. 알림 이벤트 정의**
- `PaymentCompletedNotificationEvent.kt`: 결제 완료 알림 이벤트
- Spring ApplicationEvent를 Kafka 메시지로 변환

**1.3. NotificationEventListener**
- 파일: `NotificationEventListener.kt`
- 역할: 결제 완료 시 Kafka로 알림 이벤트 발행
- 처리 흐름:
  ```
  결제 완료 (@TransactionalEventListener)
  → Kafka 발행 (notifications.payment-completed)
  → NotificationKafkaConsumer
  ```

**1.4. NotificationKafkaConsumer**
- 파일: `NotificationKafkaConsumer.kt`
- 역할: Kafka에서 알림 이벤트 수신 후 Discord로 전송
- 특징:
  - 수동 커밋 (알림 성공 후 커밋)
  - 실패 시 로깅 (TODO: DLQ 추가 가능)
  - 비동기 처리 (WebClient)

#### 2. 좌석 만료 처리 비동기화

**2.1. 좌석 만료 이벤트 정의**
- `SeatExpiredEvent.kt`: 만료된 좌석 ID 목록 포함

**2.2. SeatScheduler 수정**
- 파일: `SeatScheduler.kt`
- 변경 사항:
  - Before: 만료 확인 + 직접 복원 (동기)
  - After: 만료 확인 + Kafka 이벤트 발행 (비동기)
- 개선 효과:
  - 스케줄러 블로킹 제거
  - 빠른 실행 (만료 확인만 수행)

**2.3. SeatExpirationConsumer**
- 파일: `SeatExpirationConsumer.kt`
- 역할: Kafka에서 좌석 만료 이벤트 수신 후 복원 처리
- 처리 흐름:
  ```
  Kafka 메시지 수신
  → 좌석 정보 조회
  → 배치 복원 (Bulk Update)
  → 캐시 무효화
  → Discord 알림 (선택)
  → 오프셋 커밋
  ```
- 특징:
  - 트랜잭션 처리
  - 배치 단위 복원으로 DB 부하 분산
  - Consumer 수평 확장 가능

#### 3. 설정 및 인프라

**3.1. application.yml**
```yaml
kafka:
  topics:
    notification-payment-completed: notifications.payment-completed
    seat-expired: seats.expired

notification:
  discord:
    enabled: false  # true로 변경 시 활성화
    webhook-url: ${DISCORD_WEBHOOK_URL:}
```

**3.2. docker-compose.kafka.yml**
- 토픽 자동 생성:
  - `notifications.payment-completed` (파티션 3, 복제 3)
  - `seats.expired` (파티션 3, 복제 3)

**3.3. WebClientConfig**
- Discord 알림용 WebClient Bean 추가
- 타임아웃 설정 공유

### 구현 파일 목록

| 파일 | 경로 | 역할 |
|------|------|------|
| DiscordNotifier.kt | `infrastructure/notification/` | Discord Webhook 알림 발송 |
| PaymentCompletedNotificationEvent.kt | `domain/notification/event/` | 결제 완료 알림 이벤트 |
| SeatExpiredEvent.kt | `domain/seat/event/` | 좌석 만료 이벤트 |
| NotificationEventListener.kt | `infrastructure/event/` | 알림 이벤트 → Kafka 발행 |
| NotificationKafkaConsumer.kt | `infrastructure/kafka/` | Kafka → Discord 알림 |
| SeatScheduler.kt | `application/scheduler/` | 좌석 만료 감지 및 Kafka 발행 |
| SeatExpirationConsumer.kt | `infrastructure/kafka/` | 좌석 복원 비동기 처리 |

### 사용 방법

#### 1. Kafka 클러스터 시작
```bash
docker-compose -f docker-compose.kafka.yml up -d
```

#### 2. Discord Webhook 설정 (선택)
```bash
# 환경변수 설정
export DISCORD_WEBHOOK_URL="https://discord.com/api/webhooks/..."

# application.yml에서 enabled=true로 변경
notification:
  discord:
    enabled: true
```

#### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

#### 4. 알림 테스트
- 결제 완료 시: Discord로 "💳 결제 완료" 메시지 수신
- 좌석 만료 시 (1분마다): Discord로 "⏰ 좌석 만료 처리 완료" 메시지 수신

### 모니터링

**Kafka UI** (http://localhost:8989)
- Topics 탭에서 `notifications.payment-completed`, `seats.expired` 토픽 확인
- Messages 탭에서 실시간 메시지 흐름 확인
- Consumer Groups 탭에서 Consumer Lag 모니터링

### 아키텍처 개선: Interface 기반 알림 시스템 리팩토링

#### 개선 동기

초기 구현에서는 `DiscordNotifier`가 Discord 특화 메시지를 직접 생성하고 전송하는 구조였습니다:

```kotlin
// Before: 단일 구현체, Discord 특화
class DiscordNotifier {
    fun sendPaymentNotification(userId: Long, amount: Long, ...)
    fun sendSeatExpirationNotification(seatCount: Int, ...)
}
```

**문제점:**
1. **확장성 부족**: 카카오톡, SMS, Firebase 등 새로운 알림 채널 추가 시 구조 변경 필요
2. **책임 혼재**: 메시지 생성과 전송 로직이 하나의 클래스에 결합
3. **재사용성 낮음**: Consumer마다 Discord 특화 코드 작성 필요
4. **테스트 어려움**: Discord Webhook 의존성으로 단위 테스트 어려움

#### 개선 내용

**1. Notifier 인터페이스 도입**

```kotlin
// Notifier.kt
interface Notifier {
    suspend fun send(message: NotificationMessage)
}
```

**역할:**
- 다양한 알림 채널(Discord, Kakao, Firebase, SMS)을 동일한 방식으로 사용
- 메시지 전송만 담당 (메시지 포맷팅은 호출자가 담당)
- 추상화를 통한 느슨한 결합

**2. NotificationMessage DTO**

```kotlin
// NotificationMessage.kt
data class NotificationMessage(
    val title: String,
    val content: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val color: NotificationColor = NotificationColor.DEFAULT,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)

enum class NotificationColor(val rgbValue: Int) {
    SUCCESS(0x00FF00),  // 녹색
    ERROR(0xFF0000),    // 빨강
    WARNING(0xFFA500),  // 주황
    INFO(0x0000FF),     // 파란색
    DEFAULT(0x808080),  // 회색
}
```

**특징:**
- **채널 독립적**: Discord, 카카오톡, Firebase 등 모든 채널에서 사용 가능
- **범용 구조**: title, content, fields로 대부분의 알림 표현 가능
- **확장 가능**: color, timestamp 등 메타데이터 포함

**3. DiscordNotifier 리팩토링**

```kotlin
// Before: Discord 특화 메서드
class DiscordNotifier {
    fun sendPaymentNotification(userId: Long, amount: Long, ...) {
        val embed = createDiscordEmbed(...)
        sendToDiscord(embed)
    }
}

// After: Interface 구현, 범용 메시지 처리
@Service
@ConditionalOnProperty(prefix = "notification.discord", name = ["enabled"], havingValue = "true")
class DiscordNotifier(
    private val webClient: WebClient,
    @Value("\${notification.discord.webhook-url:}") private val webhookUrl: String,
) : Notifier {

    override suspend fun send(message: NotificationMessage) {
        val embed = convertToDiscordEmbed(message)
        sendToDiscord(embed)
    }

    private fun convertToDiscordEmbed(message: NotificationMessage): Map<String, Any> {
        // NotificationMessage → Discord Embed 형식 변환
    }
}
```

**변경사항:**
- `Notifier` 인터페이스 구현
- Discord 특화 메서드 제거, 단일 `send()` 메서드로 통합
- NotificationMessage를 Discord Embed 형식으로 변환하는 책임만 보유

**4. Consumer 코드 개선**

```kotlin
// Before: Discord 특화 코드
@Component
class NotificationKafkaConsumer(
    private val discordNotifier: DiscordNotifier,
) {
    @KafkaListener(...)
    fun consume(event: PaymentCompletedNotificationEvent) {
        discordNotifier.sendPaymentNotification(
            userId = event.userId,
            amount = event.amount,
            ...
        )
    }
}

// After: 메시지 생성 + Notifier 사용
@Component
class NotificationKafkaConsumer(
    private val notifier: Notifier,  // Interface 의존
) {
    @KafkaListener(...)
    fun consume(event: PaymentCompletedNotificationEvent) {
        // 1. NotificationMessage 생성 (Consumer의 책임)
        val message = NotificationMessage(
            title = "💳 결제 완료",
            fields = linkedMapOf(
                "사용자 ID" to event.userId.toString(),
                "예약 ID" to event.reservationId.toString(),
                "결제 금액" to "${event.amount}원",
            ),
            color = NotificationColor.INFO,
            timestamp = event.paidAt,
        )

        // 2. 메시지 전송 (Notifier의 책임)
        runBlocking { notifier.send(message) }
        acknowledgment.acknowledge()
    }
}
```

**책임 분리:**
- **Consumer**: 비즈니스 맥락에 맞는 메시지 생성 (어떤 내용을 보낼지)
- **Notifier**: 메시지를 특정 채널로 전송 (어떻게 보낼지)

#### 개선 효과

**1. 확장성 (Extensibility)**

새로운 알림 채널 추가가 간단해집니다:

```kotlin
// 카카오톡 알림 추가 예시
@Service
@ConditionalOnProperty(prefix = "notification.kakao", name = ["enabled"], havingValue = "true")
class KakaoNotifier(
    private val kakaoApiClient: KakaoApiClient,
) : Notifier {

    override suspend fun send(message: NotificationMessage) {
        // NotificationMessage → 카카오톡 템플릿 형식 변환
        val kakaoTemplate = convertToKakaoTemplate(message)
        kakaoApiClient.sendMessage(kakaoTemplate)
    }
}

// Firebase 알림 추가 예시
@Service
class FirebaseNotifier(
    private val firebaseMessaging: FirebaseMessaging,
) : Notifier {

    override suspend fun send(message: NotificationMessage) {
        // NotificationMessage → FCM 메시지 형식 변환
        val fcmMessage = convertToFcmMessage(message)
        firebaseMessaging.send(fcmMessage)
    }
}
```

**기존 Consumer 코드는 변경 불필요!** Spring이 설정에 따라 적절한 Notifier를 주입합니다.

**2. 단일 책임 원칙 (SRP)**

| 컴포넌트 | 책임 |
|----------|------|
| Consumer | 이벤트 수신, 비즈니스 맥락에 맞는 메시지 생성 |
| NotificationMessage | 채널 독립적인 메시지 구조 제공 |
| Notifier (Interface) | 메시지 전송 계약 정의 |
| DiscordNotifier | Discord Webhook 전송, Discord 형식 변환 |
| KakaoNotifier | 카카오톡 API 전송, 카카오 형식 변환 |

각 클래스가 명확한 단일 책임을 가집니다.

**3. 테스트 용이성 (Testability)**

```kotlin
// Mock Notifier로 Consumer 단위 테스트
class NotificationKafkaConsumerTest {

    @Test
    fun `결제 완료 이벤트 수신 시 올바른 메시지를 생성한다`() {
        // Given
        val mockNotifier = mockk<Notifier>()
        val consumer = NotificationKafkaConsumer(mockNotifier)

        val event = PaymentCompletedNotificationEvent(...)

        // When
        consumer.consumePaymentCompleted(event, ...)

        // Then
        verify {
            mockNotifier.send(
                match { message ->
                    message.title == "💳 결제 완료" &&
                    message.color == NotificationColor.INFO &&
                    message.fields["사용자 ID"] == "123"
                }
            )
        }
    }
}
```

**4. 의존성 역전 원칙 (DIP)**

```
Before:
NotificationKafkaConsumer → DiscordNotifier (구체 클래스 의존)

After:
NotificationKafkaConsumer → Notifier (추상화 의존)
                               ↑
                               |
                          DiscordNotifier (구현체)
```

고수준 모듈(Consumer)이 저수준 모듈(DiscordNotifier)에 의존하지 않고, 추상화(Notifier)에 의존합니다.

#### 파일 구조

```
infrastructure/notification/
├── Notifier.kt                    # 알림 인터페이스
├── NotificationMessage.kt         # 범용 메시지 DTO + Color enum
└── DiscordNotifier.kt             # Discord 구현체

(향후 확장)
└── KakaoNotifier.kt               # 카카오톡 구현체
└── FirebaseNotifier.kt            # Firebase 구현체
└── SmsNotifier.kt                 # SMS 구현체
```

#### 설정 기반 알림 채널 전환

```yaml
# application.yml
notification:
  discord:
    enabled: true  # Discord 활성화
    webhook-url: ${DISCORD_WEBHOOK_URL:}

  kakao:
    enabled: false  # 카카오톡 비활성화 (추후 구현)
    api-key: ${KAKAO_API_KEY:}
```

`@ConditionalOnProperty`로 설정 파일만 변경하면 알림 채널을 전환할 수 있습니다.

#### 향후 확장 시나리오

**1. 다중 채널 동시 발송**

```kotlin
@Component
class MultiChannelNotifier(
    private val notifiers: List<Notifier>,  // Spring이 모든 Notifier 구현체 주입
) : Notifier {

    override suspend fun send(message: NotificationMessage) {
        notifiers.forEach { notifier ->
            try {
                notifier.send(message)
            } catch (e: Exception) {
                logger.error("Failed to send via ${notifier.javaClass.simpleName}", e)
            }
        }
    }
}
```

**2. 우선순위 기반 Fallback**

```kotlin
@Component
class FallbackNotifier(
    @Qualifier("firebaseNotifier") private val primary: Notifier,
    @Qualifier("smsNotifier") private val fallback: Notifier,
) : Notifier {

    override suspend fun send(message: NotificationMessage) {
        try {
            primary.send(message)
        } catch (e: Exception) {
            logger.warn("Primary notifier failed, using fallback", e)
            fallback.send(message)
        }
    }
}
```

### 멱등성(Idempotency) 보장

Kafka Consumer에서 **At-Least-Once** 전략을 사용하기 때문에, 동일한 메시지가 재처리될 수 있습니다.
이를 대비하여 각 Consumer별로 멱등성을 보장하는 전략을 적용했습니다.

#### 멱등성 분석 결과

| Consumer | 멱등성 | 전략 |
|----------|--------|------|
| ReservationKafkaConsumer | ✅ | Idempotency Key (외부 API) |
| NotificationKafkaConsumer | ⚠️ | 허용 (중복 알림은 비즈니스적으로 허용) |
| SeatExpirationConsumer | ✅ | 자연적 멱등성 (상태 조건 체크) |

#### 1. ReservationKafkaConsumer - Redis 기반 Consumer 레벨 멱등성

Consumer 레벨에서 **Redis를 사용한 중복 체크**로 1회 처리를 보장합니다.

```kotlin
// ReservationKafkaConsumer.kt
companion object {
    private const val IDEMPOTENCY_KEY_PREFIX = "kafka:reservation:processed:"
    private val IDEMPOTENCY_TTL: Duration = Duration.ofHours(24)
}

@KafkaListener(topics = ["reservation.confirmed"])
fun consume(event: ReservationConfirmedEvent, ack: Acknowledgment) {
    // 멱등성 체크: 이미 처리된 메시지인지 확인
    val idempotencyKey = "$IDEMPOTENCY_KEY_PREFIX${event.reservationId}"
    val isNewMessage = stringRedisTemplate.opsForValue()
        .setIfAbsent(idempotencyKey, Instant.now().toString(), IDEMPOTENCY_TTL)

    if (isNewMessage == false) {
        logger.info("이미 처리된 메시지 스킵 - reservationId={}", event.reservationId)
        ack.acknowledge()
        return
    }

    // 실제 처리 로직
    dataPlatformClient.sendReservation(event).block()
    ack.acknowledge()
}
```

**동작 원리:**
```
1차 처리: Redis SETNX("kafka:reservation:processed:123") → 성공 → 처리 → 커밋
2차 처리 (재시도): Redis SETNX → 실패 (이미 존재) → 스킵 → 커밋
```

**추가로 외부 API에도 Idempotency Key 전송:**
```kotlin
// DataPlatformClient.kt - 2중 보호
val idempotencyKey = "$prefix-$id"  // reservation-123
val headers = mapOf("X-Idempotency-Key" to idempotencyKey)
```

**2중 멱등성 보장:**
1. **Consumer 레벨 (Redis)**: 같은 메시지 재처리 방지
2. **외부 API 레벨 (Idempotency Key)**: 외부 시스템에서도 중복 방지 (지원 시)

#### 2. SeatExpirationConsumer - 자연적 멱등성 (상태 조건 체크)

좌석 복원 쿼리에서 **상태 조건**을 체크하여 자연스럽게 멱등성을 보장합니다.

```sql
-- SeatJpaRepository.kt
UPDATE Seat s
SET s.seatStatus = 'AVAILABLE', s.updatedAt = CURRENT_TIMESTAMP
WHERE s.id IN :seatIds
  AND s.seatStatus = 'TEMPORARY_RESERVED'  -- 핵심: 상태 조건
```

**동작 원리:**
```
1차 처리: seatIds=[1,2,3] → 상태가 TEMPORARY_RESERVED → 3개 복원
2차 처리 (재시도): seatIds=[1,2,3] → 상태가 이미 AVAILABLE → 0개 복원 (영향 없음)
```

이미 복원된 좌석은 `TEMPORARY_RESERVED` 상태가 아니므로 UPDATE 대상에서 제외됩니다.
같은 메시지를 여러 번 처리해도 결과가 동일합니다.

#### 3. NotificationKafkaConsumer - 중복 허용

알림은 비즈니스적으로 **중복 발송이 허용**됩니다.

```kotlin
// NotificationKafkaConsumer.kt
@KafkaListener(topics = ["notifications.payment-completed"])
fun consumePaymentCompleted(event: PaymentCompletedNotificationEvent, ack: Acknowledgment) {
    val message = NotificationMessage(
        title = "💳 결제 완료",
        fields = linkedMapOf(...),
    )

    runBlocking { notifier.send(message) }
    ack.acknowledge()
}
```

**비즈니스 판단:**
- 알림은 사용자에게 정보를 전달하는 목적
- 중복 알림이 발생해도 큰 문제 없음 (사용자가 2번 알림 받는 정도)
- 필요 시 Redis로 중복 체크 추가 가능

**향후 개선 (필요시):**
```kotlin
// Redis로 중복 체크
val isProcessed = redisTemplate.opsForValue()
    .setIfAbsent("notification:${event.reservationId}", "1", Duration.ofHours(24))

if (isProcessed == false) {
    logger.info("이미 처리된 알림: ${event.reservationId}")
    ack.acknowledge()
    return
}
```

#### 멱등성 보장 패턴 정리

| 패턴 | 적용 대상 | 특징 |
|------|----------|------|
| **Idempotency Key** | 외부 API 호출 | 외부 시스템이 중복 요청 감지 |
| **상태 조건 체크** | DB Update | WHERE 조건으로 자연적 멱등성 |
| **처리 이력 저장** | 중요 비즈니스 | Redis/DB에 처리 여부 기록 |
| **유니크 제약조건** | DB Insert | DB 레벨에서 중복 방지 |
| **중복 허용** | 알림, 로그 | 비즈니스적으로 허용 가능한 경우 |

---

### 향후 개선 사항

1. **DLQ 추가**: NotificationConsumer 및 SeatExpirationConsumer에 DLQ 로직 추가
2. **Firebase 연동**: Discord 대신 Firebase Cloud Messaging으로 실제 Push 알림
3. **SMS 추가**: AWS SNS 연동으로 SMS 알림 추가
4. **예약 완료 알림**: 좌석 임시 예약 시 알림 추가 (현재는 결제 완료만 구현)
5. **다중 채널 발송**: MultiChannelNotifier로 Push + SMS 동시 발송
6. **알림 이력 저장**: NotificationHistory 테이블에 발송 이력 기록

---