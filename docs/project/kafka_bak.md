# Kafka

## 목차
1. [카프카란 무엇인가?](#카프카란-무엇인가)
2. [핵심 구성 요소](#핵심-구성-요소)
3. [토픽과 파티션](#토픽과-파티션)
4. [컨슈머 그룹](#컨슈머-그룹)
5. [리밸런싱](#리밸런싱)
6. [클러스터와 복제](#클러스터와-복제)
7. [배포 모드](#배포-모드)
8. [고급 개념](#고급-개념)

---

## 카프카란 무엇인가?

### 정의

**Apache Kafka**는 대규모 실시간 데이터 처리를 위한 **분산 메시징 시스템**(Distributed Messaging System)입니다.

### 왜 복잡한 분산 시스템을 위한 메시징 서비스가 필요한가?

현대의 대규모 시스템은 다음과 같은 요구사항을 가지고 있습니다:

1. **대용량 처리**: 초당 수백만 건의 이벤트 처리
2. **고가용성**: 24/7 무중단 서비스
3. **다양한 기능**: 메시지 유실 방지, 순서 보장, 재처리 등

#### 카프카 도입 전후 비교

**Before (HTTP 직접 호출)**
```
[주문 서비스] → HTTP → [결제 서비스]
           → HTTP → [재고 서비스]
           → HTTP → [알림 서비스]
           → HTTP → [분석 서비스]
```

**문제점**
- 주문 서비스가 모든 하위 서비스를 알아야 함 (강한 결합)
- 하나의 서비스 장애가 전체 시스템에 영향
- 재고 서비스가 느리면 주문도 느려짐
- 새로운 서비스 추가 시 주문 서비스 코드 수정 필요

**After (Kafka 사용)**
```
[주문 서비스] → Kafka Topic (order.created)
                    ↓
         ┌──────────┼──────────┬──────────┐
         ↓          ↓          ↓          ↓
    [결제 서비스] [재고 서비스] [알림 서비스] [분석 서비스]
```

**개선 효과**
- 주문 서비스는 Kafka에만 메시지 발행 (느슨한 결합)
- 각 서비스는 독립적으로 메시지 소비
- 한 서비스의 장애가 다른 서비스에 영향 없음
- 새로운 서비스 추가 시 주문 서비스 수정 불필요


### 중요: 카프카는 기본적으로 메시지의 순서를 보장하지 않는다

정확히는, **토픽 레벨에서는 순서를 보장하지 않지만, 파티션 레벨에서는 순서를 보장합니다.**

```
Topic: order.created (3개 파티션)
┌──────────────────────────────────┐
│ Partition 0: [M1] → [M3] → [M5]  │  ← 이 안에서는 순서 보장
│ Partition 1: [M2] → [M6]         │  ← 이 안에서는 순서 보장
│ Partition 2: [M4] → [M7]         │  ← 이 안에서는 순서 보장
└──────────────────────────────────┘

전체 토픽 순서: M1, M2, M3, M4, M5, M6, M7 (X)
실제 소비 순서: M1-M2 순서 보장 안됨, M1-M3 순서 보장됨
```

**순서를 보장하려면?**
- 동일한 Key를 가진 메시지는 같은 파티션으로 전송됩니다.
- 예: `userId`를 Key로 사용하면 동일 사용자의 이벤트는 순서 보장

```kotlin
// userId를 Key로 사용
kafkaTemplate.send(
    topic = "user.events",
    key = userId.toString(),  // 동일 userId는 같은 파티션으로
    value = event
)
```

---

## 핵심 구성 요소

### Producer (프로듀서)

메시지를 카프카 시스템에 **발행(Publish)** 하는 서비스입니다.

**역할**
- 메시지를 특정 Topic으로 전송
- 메시지의 Key를 기반으로 파티션 결정
- 전송 실패 시 재시도

**실제 예시**
```kotlin
@Service
class OrderEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, OrderEvent>,
) {
    // 주문 상태 변경 이벤트들: Created → Confirmed → Shipped → Delivered
    // orderId를 Key로 사용하면 동일 주문의 상태 변경 이벤트들이 순서대로 처리됨

    fun publishOrderCreated(order: Order) {
        kafkaTemplate.send(
            topic = "order.events",
            key = order.id.toString(),  // 동일 orderId → 같은 파티션
            value = OrderCreatedEvent(orderId = order.id, userId = order.userId)
        )
    }

    fun publishOrderConfirmed(order: Order) {
        kafkaTemplate.send(
            topic = "order.events",
            key = order.id.toString(),  // 동일 orderId → 같은 파티션
            value = OrderConfirmedEvent(orderId = order.id)
        )
    }

    fun publishOrderShipped(order: Order) {
        kafkaTemplate.send(
            topic = "order.events",
            key = order.id.toString(),  // 동일 orderId → 같은 파티션
            value = OrderShippedEvent(orderId = order.id, trackingNumber = order.trackingNumber)
        )
    }
}

// 결과: 주문 123의 이벤트는 항상 Created → Confirmed → Shipped 순서로 처리됨
// (같은 파티션에 순서대로 저장되기 때문)
```

### Consumer (컨슈머)

카프카 시스템에서 발행된 메시지를 **읽어오는(Subscribe)** 서비스입니다.

**역할:**
- 특정 Topic의 메시지 구독
- Offset 관리 (어디까지 읽었는지 추적)
- 메시지 처리 후 Commit

**실제 예시:**
```kotlin
@Component
class OrderEventConsumer(
    private val paymentService: PaymentService,
) {
    @KafkaListener(
        topics = ["order.created"],
        groupId = "payment-service-group"
    )
    fun consumeOrderCreated(event: OrderCreatedEvent, ack: Acknowledgment) {
        try {
            // 메시지 처리
            paymentService.processPayment(event.orderId, event.amount)

            // 성공 시 Offset 커밋
            ack.acknowledge()
        } catch (e: Exception) {
            logger.error("Failed to process order: ${event.orderId}", e)
            // 실패 시 커밋하지 않음 → 재처리
        }
    }
}
```

### Producer와 Consumer는 서로가 될 수 있다!

**중요 개념:** 하나의 서비스가 동시에 Producer이자 Consumer일 수 있습니다.

**예시: 결제 서비스**
```
[주문 서비스]
    → Kafka (order.created)
        → [결제 서비스] (Consumer 역할)
            → 결제 처리
            → Kafka (payment.completed) (Producer 역할)
                → [포인트 서비스] (Consumer 역할)
                → [알림 서비스] (Consumer 역할)
```

```kotlin
@Component
class PaymentService(
    private val kafkaTemplate: KafkaTemplate<String, PaymentEvent>,
) {
    // Consumer로 동작
    @KafkaListener(topics = ["order.created"])
    fun consumeOrderCreated(event: OrderCreatedEvent, ack: Acknowledgment) {
        // 결제 처리
        val payment = processPayment(event)

        // Producer로 동작
        kafkaTemplate.send(
            topic = "payment.completed",
            key = payment.orderId.toString(),
            value = PaymentCompletedEvent(
                paymentId = payment.id,
                orderId = payment.orderId,
                amount = payment.amount,
            )
        )

        ack.acknowledge()
    }
}
```

### Offset (오프셋)

컨슈머가 **어디까지 메시지를 읽었는지** 기록해둔 데이터입니다.

**개념:**
```
Partition 0: [M0] [M1] [M2] [M3] [M4] [M5]
              ↑                   ↑
           Offset 0           Offset 4 (현재 Consumer가 읽은 위치)
```

**Offset의 역할:**
1. **재시작 시 이어서 읽기**: Consumer가 재시작해도 마지막 읽은 위치부터 계속
2. **중복 방지**: 이미 처리한 메시지를 다시 읽지 않음
3. **재처리 가능**: Offset을 되돌려서 과거 메시지 재처리 가능

**Offset 저장 위치:**
- Kafka 내부 Topic (`__consumer_offsets`)에 저장
- Consumer Group + Topic + Partition 단위로 관리

**실제 예시:**
```bash
# Offset 확인
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group payment-service-group \
  --describe

# 결과:
# TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# order.created   0          1250            1250            0
# order.created   1          1180            1180            0
# order.created   2          1320            1350            30  ← Lag 발생!
```

**Lag (랙):**
- LOG-END-OFFSET - CURRENT-OFFSET
- Consumer가 처리하지 못한 메시지 개수
- Lag이 계속 증가하면 Consumer 성능 문제

### Broker (브로커)

카프카 클러스터를 구성하는 **물리적인 서버**들입니다.

**역할:**
- Producer에게 메시지를 받아 저장
- Consumer에게 메시지 전송
- 파티션 데이터를 디스크에 영속화
- Leader Election 참여

**특별한 역할의 브로커:**

**1. Controller**
- 클러스터 내 브로커 중 하나가 Controller 역할 수행
- 파티션 리더 선출 담당
- 브로커 장애 감지 및 복구 조정

**2. Coordinator**
- Consumer Group 관리
- Rebalancing 조정

**3. Bootstrap Server**
- 클라이언트(Producer/Consumer)가 카프카 클러스터에 **최초 접속**하기 위한 진입점
- 하나의 브로커 주소만 알면 전체 클러스터 정보를 자동으로 받아옴

**실제 예시:**
```yaml
# application.yml
kafka:
  bootstrap-servers:
    - localhost:9092  # Broker 1
    - localhost:9093  # Broker 2
    - localhost:9094  # Broker 3
```

```
Client 연결 과정:
1. Client → Broker 1 (Bootstrap Server) 연결
2. Broker 1 → Client에게 전체 클러스터 메타데이터 전송
   (모든 브로커 목록, 토픽 정보, 파티션 리더 등)
3. Client → 필요한 파티션의 리더 브로커에 직접 연결
```

**브로커 3대 클러스터 예시:**
```
┌─────────────────────────────────────────────────────────┐
│              Kafka Cluster (3-broker)                   │
├─────────────────────────────────────────────────────────┤
│  Broker 1 (Controller)                                  │
│  - Port: 9092                                           │
│  - Partitions: order.created-0 (Leader)                │
│  - Partitions: order.created-1 (Follower)              │
├─────────────────────────────────────────────────────────┤
│  Broker 2                                               │
│  - Port: 9093                                           │
│  - Partitions: order.created-1 (Leader)                │
│  - Partitions: order.created-2 (Follower)              │
├─────────────────────────────────────────────────────────┤
│  Broker 3                                               │
│  - Port: 9094                                           │
│  - Partitions: order.created-2 (Leader)                │
│  - Partitions: order.created-0 (Follower)              │
└─────────────────────────────────────────────────────────┘
```

### Message (메시지)

카프카를 통해 Producer에서 Consumer로 이동하는 **데이터**를 의미합니다.

**구조:**
```
Message = Key + Value + Headers + Timestamp
```

**1. Key (선택 사항)**
- 파티셔닝 기준
- 일반적으로 리소스 ID (userId, orderId 등)
- Key가 동일한 메시지는 같은 파티션으로 전송 (순서 보장)

**2. Value (필수)**
- 실제 메시지 내용
- 일반적으로 JSON, Avro, Protobuf 형식
- 리소스의 상세 정보

**3. Headers (선택 사항)**
- 메타데이터 (타임스탬프, 트레이싱 ID 등)

**4. Timestamp**
- 메시지 생성 시간 또는 브로커 수신 시간

**실제 예시:**
```kotlin
// Producer
val message = ProducerRecord(
    topic = "order.created",
    key = "order-12345",  // Key: 주문 ID
    value = OrderCreatedEvent(  // Value: 주문 상세 정보
        orderId = 12345L,
        userId = 999L,
        items = listOf(...),
        totalAmount = 50000L,
    ),
    headers = RecordHeaders().apply {
        add("traceId", "abc-123".toByteArray())  // 분산 추적용
        add("source", "mobile-app".toByteArray())
    }
)

kafkaTemplate.send(message)
```

**메시지 직렬화:**
- Kafka는 byte array만 전송 가능
- Key/Value를 byte array로 변환하는 Serializer 필요

```kotlin
// Producer 설정
kafka:
  producer:
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

---

## 토픽과 파티션

### Topic (토픽)

**메시지의 종류를 분류**하는 기준이며, Consumer는 이 단위로 메시지를 구독합니다.

**특징:**
- 물리적 + 논리적 개념
- 데이터베이스의 테이블과 유사
- 여러 개의 파티션으로 구성

### Partition (파티션)

실제 메시지가 담겨있는 **큐(Queue)** 이며, 토픽은 여러 개의 파티션으로 구성되어 있습니다.

**특징:**
- 물리적 개념 (디스크에 파일로 저장)
- 순서를 보장하는 불변(Immutable) 로그
- 파티션 개수는 일반적으로 브로커 개수와 맞춤

**파티션 분산 예시:**
```
Topic: order.created (3 Partitions, 3 Brokers)

Broker 1:
  ├── Partition 0 (Leader)
  │   ├── [M1] offset 0
  │   ├── [M4] offset 1
  │   └── [M7] offset 2

Broker 2:
  ├── Partition 1 (Leader)
  │   ├── [M2] offset 0
  │   ├── [M5] offset 1
  │   └── [M8] offset 2

Broker 3:
  ├── Partition 2 (Leader)
  │   ├── [M3] offset 0
  │   ├── [M6] offset 1
  │   └── [M9] offset 2
```

### 중요: 하나의 파티션은 하나의 컨슈머만 사용할 수 있음

**이유:** 순서 보장

만약 하나의 파티션을 여러 Consumer가 동시에 읽으면 순서를 보장할 수 없습니다.

**잘못된 예시:**
```
Partition 0: [M1] [M2] [M3] [M4]
              ↓         ↓
         Consumer A  Consumer B

Consumer A: M1, M3 읽음
Consumer B: M2, M4 읽음

전체 순서: M1 → M2 → M3 → M4 (X)
실제 처리: M1, M2 순서 보장 안됨!
```

**올바른 예시:**
```
Topic: order.created (3 Partitions)

Consumer Group: payment-service-group (3 Consumers)

Partition 0 → Consumer A (전담)
Partition 1 → Consumer B (전담)
Partition 2 → Consumer C (전담)

각 파티션 내에서 순서 보장!
```

**파티션 수와 Consumer 수의 관계:**
```
Case 1: Partition 3개, Consumer 3개
  → 각 Consumer가 1개씩 담당 (이상적)

Case 2: Partition 3개, Consumer 5개
  → Consumer 2개는 놀고 있음 (비효율)

Case 3: Partition 3개, Consumer 2개
  → Consumer 1개가 Partition 2개 담당 (가능하지만 부하 불균형)

Case 4: Partition 5개, Consumer 3개
  → Consumer 2개가 Partition 2개씩, 1개가 1개 담당
```

### 메시지가 어떤 파티션에 담길지는 Key 값에 따라 결정됨

**파티셔닝 전략:**

**1. Key가 있는 경우 (대부분)**
```
Partition = hash(key) % partition_count

예시:
- hash("user-123") % 3 = Partition 1
- hash("user-456") % 3 = Partition 1
- hash("user-789") % 3 = Partition 0

→ 동일 Key는 항상 같은 파티션!
```

**실제 코드:**
```kotlin
// userId를 Key로 사용
kafkaTemplate.send(
    topic = "user.events",
    key = userId.toString(),  // 동일 userId는 같은 파티션
    value = event
)

// 결과:
// userId=123 → 항상 Partition 1
// userId=456 → 항상 Partition 0
// userId=789 → 항상 Partition 2
```

**2. Key가 없는 경우**
- Round-robin 방식으로 파티션 할당
- 순서 보장 불필요한 경우 사용

```kotlin
// Key 없이 전송
kafkaTemplate.send(
    topic = "logs",
    value = LogEvent(...)
)

// 결과:
// Message 1 → Partition 0
// Message 2 → Partition 1
// Message 3 → Partition 2
// Message 4 → Partition 0 (반복)
```

**파티션 설계 Best Practice:**

```
1. 순서가 중요한 경우: Key 사용
   예: 동일 사용자의 이벤트는 순서대로 처리

2. 순서가 무관한 경우: Key 없이 전송
   예: 로그, 분석 데이터

3. 파티션 수 결정:
   - 처리량 기준: 파티션당 처리 가능한 TPS 고려
   - Consumer 수 고려: Consumer 수만큼 파티션 필요
   - 일반적으로 3~10개 (브로커 수의 배수)

4. 파티션 수 증가는 가능하지만 감소는 불가능!
   (토픽 재생성 필요)
```

---

## 컨슈머 그룹

### Consumer Group (컨슈머 그룹)

토픽에 있는 메시지를 **구독하는 단위**이며, 보통 **하나의 서비스**를 나타냅니다.

### 왜 Consumer Group이 필요한가?

**문제 상황:**
```
Topic: order.created

Consumer A (결제 서비스 인스턴스 1)
Consumer B (결제 서비스 인스턴스 2)

→ 같은 메시지를 A, B가 둘 다 처리하면?
→ 결제가 2번 발생! (중복 처리)
```

**해결: Consumer Group**
```
Topic: order.created (3 Partitions)

Consumer Group: payment-service-group
  ├── Consumer A (Partition 0, 1)
  └── Consumer B (Partition 2)

→ 각 파티션은 그룹 내 하나의 Consumer만 담당
→ 메시지는 그룹당 한 번만 처리됨!
```

### 핵심 개념: 하나의 토픽을 여러 서비스가 독립적으로 읽을 수 있음

**강력한 기능:**
```
Topic: order.created

Consumer Group 1: payment-service-group
  → 결제 처리

Consumer Group 2: inventory-service-group
  → 재고 차감

Consumer Group 3: analytics-service-group
  → 분석 데이터 수집

Consumer Group 4: notification-service-group
  → 알림 발송

→ 동일한 메시지를 4개 서비스가 독립적으로 소비!
→ 각 그룹은 독립적인 Offset 관리
```

**실제 예시:**
```kotlin
// 결제 서비스
@KafkaListener(
    topics = ["order.created"],
    groupId = "payment-service-group"  // 그룹 1
)
fun processPayment(event: OrderCreatedEvent) {
    // 결제 처리
}

// 재고 서비스
@KafkaListener(
    topics = ["order.created"],
    groupId = "inventory-service-group"  // 그룹 2
)
fun reserveInventory(event: OrderCreatedEvent) {
    // 재고 차감
}

// 알림 서비스
@KafkaListener(
    topics = ["order.created"],
    groupId = "notification-service-group"  // 그룹 3
)
fun sendNotification(event: OrderCreatedEvent) {
    // 알림 발송
}
```

**각 그룹의 독립적인 Offset:**
```bash
# Consumer Group 확인
kafka-consumer-groups --bootstrap-server localhost:9092 --list

# 결과:
payment-service-group
inventory-service-group
analytics-service-group
notification-service-group

# 각 그룹의 Offset 확인
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group payment-service-group --describe

# TOPIC           PARTITION  CURRENT-OFFSET  LAG
# order.created   0          5000            0
# order.created   1          4800            0
# order.created   2          5200            0

kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group analytics-service-group --describe

# TOPIC           PARTITION  CURRENT-OFFSET  LAG
# order.created   0          3000            2000  ← 분석 서비스는 천천히 처리 중
# order.created   1          2800            2000
# order.created   2          3200            2000
```

### Consumer Group 활용 패턴

**패턴 1: 확장성 (Scale-out)**
```
Topic: order.created (10 Partitions)

Consumer Group: payment-service-group
  ├── Consumer A (Partition 0, 1, 2)
  ├── Consumer B (Partition 3, 4, 5)
  ├── Consumer C (Partition 6, 7)
  └── Consumer D (Partition 8, 9)

→ 처리량 증가 시 Consumer 추가 (수평 확장)
```

**패턴 2: 독립적인 처리**
```
Topic: user.registered

Group 1: welcome-email-group → 환영 이메일 발송
Group 2: coupon-issuance-group → 가입 쿠폰 발급
Group 3: analytics-group → 가입 통계 수집
Group 4: crm-sync-group → CRM 시스템 동기화

→ 각 서비스는 독립적으로 동작
→ 한 서비스의 장애가 다른 서비스에 영향 없음
```

---

## 리밸런싱

### Rebalancing (리밸런싱)

컨슈머별로 할당된 **파티션을 다시 고르게 분배**하는 과정입니다.

### 리밸런싱이 발생하는 경우

**1. Consumer 추가**
```
Before:
Topic: order.created (4 Partitions)
Consumer Group: payment-service-group
  ├── Consumer A (Partition 0, 1, 2, 3)  ← 혼자 4개 처리 (과부하)

After (Consumer B 추가):
Rebalancing 발생!
  ├── Consumer A (Partition 0, 1)  ← 2개로 줄어듦
  └── Consumer B (Partition 2, 3)  ← 2개 새로 할당
```

**2. Consumer 삭제 (장애 또는 종료)**
```
Before:
Consumer Group: payment-service-group
  ├── Consumer A (Partition 0, 1)
  ├── Consumer B (Partition 2, 3)  ← 장애 발생!
  └── Consumer C (Partition 4, 5)

After (Consumer B 제거):
Rebalancing 발생!
  ├── Consumer A (Partition 0, 1, 2)  ← Partition 2 추가 할당
  └── Consumer C (Partition 3, 4, 5)  ← Partition 3 추가 할당
```

**3. 새로운 파티션 추가**
```
Before:
Topic: order.created (3 Partitions)
Consumer Group: payment-service-group
  ├── Consumer A (Partition 0)
  ├── Consumer B (Partition 1)
  └── Consumer C (Partition 2)

After (Partition 추가 → 4 Partitions):
Rebalancing 발생!
  ├── Consumer A (Partition 0)
  ├── Consumer B (Partition 1)
  ├── Consumer C (Partition 2)
  └── Consumer D (Partition 3)  ← 새 파티션
```

### 리밸런싱 중에는 메시지를 읽을 수 없음!

**중요:** 리밸런싱 중에는 Consumer가 카프카 시스템으로부터 메시지를 읽을 수 없습니다.

**리밸런싱 과정:**
```
1. Coordinator가 리밸런싱 필요 감지
   ↓
2. 모든 Consumer에게 리밸런싱 시작 알림
   ↓
3. 모든 Consumer가 현재 파티션 처리 중단
   ↓
4. 파티션 재할당 (Eager Rebalancing)
   ↓ (이 동안 메시지 처리 중단! Stop-The-World)
5. Consumer들이 새로운 파티션 할당받음
   ↓
6. 메시지 소비 재개
```

**리밸런싱 시간:**
- 일반적으로 수백 ms ~ 수 초
- Consumer 수가 많을수록 오래 걸림
- 대부분의 경우 짧은 순간이지만, 대규모 시스템에서는 주의 필요

**실제 로그 예시:**
```
2024-01-15 10:23:45.123 INFO  o.a.k.c.c.i.AbstractCoordinator :
  [Consumer clientId=consumer-1, groupId=payment-service-group]
  Revoking previously assigned partitions [order.created-0, order.created-1]

2024-01-15 10:23:45.456 INFO  o.a.k.c.c.i.AbstractCoordinator :
  [Consumer clientId=consumer-1, groupId=payment-service-group]
  (Re-)joining group

2024-01-15 10:23:45.789 INFO  o.a.k.c.c.i.AbstractCoordinator :
  [Consumer clientId=consumer-1, groupId=payment-service-group]
  Successfully joined group with generation 5

2024-01-15 10:23:45.890 INFO  o.a.k.c.c.i.ConsumerCoordinator :
  [Consumer clientId=consumer-1, groupId=payment-service-group]
  Finished assignment for group at generation 5:
  {consumer-1=[order.created-0], consumer-2=[order.created-1]}

→ 약 0.8초 동안 리밸런싱 진행
```

### 리밸런싱 최소화 전략

**1. Consumer 안정성 유지**
```kotlin
// Heartbeat 설정 (Consumer가 살아있음을 알림)
kafka:
  consumer:
    heartbeat-interval-ms: 3000  # 3초마다 heartbeat
    session-timeout-ms: 10000    # 10초 동안 heartbeat 없으면 장애로 간주
    max-poll-interval-ms: 300000 # 5분 이내에 poll() 호출해야 함
```

**2. Graceful Shutdown**
```kotlin
@PreDestroy
fun shutdown() {
    logger.info("Shutting down consumer gracefully...")
    // Consumer가 현재 처리 중인 메시지 완료 후 종료
}
```

**3. Incremental Cooperative Rebalancing (Kafka 2.4+)**
```
Eager Rebalancing (기존):
  → 모든 파티션 회수 후 재할당 (전체 중단)

Incremental Cooperative Rebalancing (개선):
  → 필요한 파티션만 재할당 (부분 중단)

예시:
Consumer A: [P0, P1, P2] → [P0, P1] (P2만 회수)
Consumer B: [P3] → [P2, P3] (P2 추가 할당)

→ P0, P1, P3는 계속 처리됨!
```

---

## 클러스터와 복제

### Cluster (클러스터)

하나의 서버가 아닌 **여러 개의 브로커가 모여서** 하나의 카프카 클러스터를 구성합니다.

### 왜 클러스터가 필요한가?

**1. 고가용성 (High Availability)**
- 브로커 한 대 장애 시에도 서비스 지속

**2. 확장성 (Scalability)**
- 트래픽 증가 시 브로커 추가로 대응

**3. 부하 분산 (Load Balancing)**
- 파티션을 여러 브로커에 분산하여 부하 분산

**단일 브로커 vs 클러스터:**
```
단일 브로커:
┌──────────────────────┐
│   Broker 1           │
│   - Partition 0      │
│   - Partition 1      │
│   - Partition 2      │
└──────────────────────┘
→ 이 브로커 장애 시 전체 서비스 중단!

3-브로커 클러스터:
┌──────────────────────┐
│   Broker 1           │
│   - Partition 0      │
└──────────────────────┘
┌──────────────────────┐
│   Broker 2           │
│   - Partition 1      │
└──────────────────────┘
┌──────────────────────┐
│   Broker 3           │
│   - Partition 2      │
└──────────────────────┘
→ 한 브로커 장애 시 다른 브로커로 서비스 지속!
```

### 유연한 스케일링

**카프카는 운영 도중에 브로커 구성을 변경**하여 유연한 스케일링이 가능합니다.

**Scale-out (브로커 추가):**
```bash
# 기존: 3-broker 클러스터
docker-compose -f docker-compose.kafka.yml up -d

# 브로커 4 추가
docker-compose -f docker-compose.kafka.yml up -d --scale kafka=4

# 파티션 재할당 (기존 파티션을 새 브로커로 이동)
kafka-reassign-partitions --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --execute
```

**Scale-down (브로커 제거):**
```bash
# 브로커 4 제거 전 파티션 재할당 필요
# 1. 브로커 4의 파티션을 다른 브로커로 이동
kafka-reassign-partitions ...

# 2. 브로커 4 종료
docker-compose -f docker-compose.kafka.yml stop kafka-4
```

---

## Replication (복제)

### 브로커 장애 시 데이터 유실 방지

Kafka는 다른 브로커에 **파티션 복제본(Replica)**을 만들어주는 기능을 제공합니다.

### Leader Replica vs Follower Replica

**Leader Replica (메인 파티션)**
- Producer와 Consumer의 모든 요청 처리
- 읽기/쓰기 모두 Leader만 처리

**Follower Replica (백업 파티션)**
- Leader의 데이터를 복제만 함
- Leader 장애 시 새로운 Leader로 승격

**복제 구조:**
```
Topic: order.created
Partition 0, Replication Factor 3

Broker 1: Partition 0 (Leader)   ← Producer/Consumer는 여기로만 요청
          ↓ 복제
Broker 2: Partition 0 (Follower) ← Leader 데이터 복제
          ↓ 복제
Broker 3: Partition 0 (Follower) ← Leader 데이터 복제
```

**Leader 장애 시:**
```
Before:
Broker 1: Partition 0 (Leader)   ← 장애 발생!
Broker 2: Partition 0 (Follower)
Broker 3: Partition 0 (Follower)

After (자동 복구):
Broker 1: (Down)
Broker 2: Partition 0 (Leader)   ← Follower가 Leader로 승격!
Broker 3: Partition 0 (Follower)

→ 서비스 무중단!
→ Broker 1 복구 시 Follower로 합류
```

### Replication Factor (복제 계수)

**각각의 파티션에 대한 복제본(데이터 백업)을 몇 개 만들 것인가?**

**설정 예시:**
```bash
# Replication Factor 3으로 토픽 생성
kafka-topics --create \
  --topic order.created \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

**복제 계수별 특징:**

**Replication Factor = 1 (복제 없음)**
```
Broker 1: Partition 0 (Leader)

→ 브로커 장애 시 데이터 유실!
→ 개발 환경에서만 사용
```

**Replication Factor = 2 (복제 1개)**
```
Broker 1: Partition 0 (Leader)
Broker 2: Partition 0 (Follower)

→ 브로커 1대 장애까지 견딤
→ 최소한의 안정성
```

**Replication Factor = 3 (복제 2개) ← 일반적 권장**
```
Broker 1: Partition 0 (Leader)
Broker 2: Partition 0 (Follower)
Broker 3: Partition 0 (Follower)

→ 브로커 2대 장애까지 견딤
→ 프로덕션 환경 권장
→ 안정성과 비용의 균형
```

**Replication Factor = 5 (복제 4개)**
```
→ 브로커 4대 장애까지 견딤
→ 매우 중요한 데이터 (금융 거래 등)
→ 디스크 용량 5배 필요 (비용 증가)
```

### 주의: 복제본은 다른 브로커에 저장됨

**올바른 복제:**
```
3-Broker Cluster, Replication Factor 3

Broker 1: Partition 0 (Leader)
Broker 2: Partition 0 (Follower)  ← 다른 브로커에 복제
Broker 3: Partition 0 (Follower)  ← 다른 브로커에 복제

→ 브로커 1 장애 시 Broker 2 또는 3이 Leader 승격
```

**잘못된 복제 (불가능):**
```
Broker 1: Partition 0 (Leader)
Broker 1: Partition 0 (Follower)  ← 같은 브로커에 복제 (의미 없음)
Broker 1: Partition 0 (Follower)

→ Broker 1 장애 시 모든 복제본 유실!
```

### 용량 계산 예시

**시나리오:**
- Topic: order.created
- Partitions: 10
- Replication Factor: 3
- 각 파티션 크기: 100GB

**총 디스크 용량:**
```
단일 복제 (RF=1): 10 partitions × 100GB = 1TB

3배 복제 (RF=3): 10 partitions × 100GB × 3 replicas = 3TB
                 ↑                              ↑
            파티션 수                      복제 계수

→ 디스크 용량이 3배 필요!
```

**브로커별 용량 (3-broker 클러스터):**
```
Broker 1: 1TB (10개 파티션 중 약 1/3씩 분산)
Broker 2: 1TB
Broker 3: 1TB

Total: 3TB
```

### In-Sync Replicas (ISR)

**ISR:** Leader와 동기화가 완료된 Follower 목록

**acks 설정과의 관계:**
```kotlin
kafka:
  producer:
    acks: all  # 모든 ISR이 확인할 때까지 대기

    # acks=0: 전송만 하고 확인 안함 (유실 가능, 빠름)
    # acks=1: Leader만 확인 (Leader 장애 시 유실 가능)
    # acks=all: 모든 ISR 확인 (유실 없음, 느림)
```

**ISR 예시:**
```
Broker 1: Partition 0 (Leader)   ← ISR
Broker 2: Partition 0 (Follower) ← ISR (동기화 완료)
Broker 3: Partition 0 (Follower) ← ISR (동기화 완료)

Broker 4: Partition 0 (Follower) ← Out of ISR (네트워크 지연으로 동기화 지연)

acks=all 설정 시:
→ Broker 1, 2, 3 모두 확인해야 Producer에 성공 응답
→ Broker 4는 ISR이 아니므로 무시
```

---

## 배포 모드

### ZooKeeper vs KRaft

Kafka 클러스터의 **메타데이터 관리 방식**에 대한 선택입니다.

### ZooKeeper (기존 방식)

**개념:**
- Apache ZooKeeper를 별도로 운영
- Kafka 클러스터의 메타데이터를 ZooKeeper에 저장
- 카프카 초기 버전부터 사용된 중앙 제어 방식

**구조:**
```
┌─────────────────────────────────────┐
│         ZooKeeper Ensemble          │
│  (메타데이터 저장 - 일종의 DB)          │
│  - 브로커 목록                         │
│  - 토픽 설정                          │
│  - 파티션 리더 정보                    │
│  - Controller 선출                   │
└──────────────┬──────────────────────┘
               ↓ (메타데이터 읽기/쓰기)
┌──────────────────────────────────────┐
│        Kafka Cluster                 │
│  ├── Broker 1                        │
│  ├── Broker 2                        │
│  └── Broker 3                        │
└──────────────────────────────────────┘
```

**장점:**
- 검증된 안정성 (10년 이상 사용)
- 풍부한 운영 경험과 문서
- 대부분의 조직에서 사용 중

**단점:**
- ZooKeeper 별도 운영 필요 (복잡도 증가)
- 확장성 제한 (ZooKeeper가 병목)
- 메타데이터 변경 시 ZooKeeper 부하

**실제 구성:**
```yaml
# docker-compose.kafka.yml (ZooKeeper 모드)
version: '3'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181  # ZooKeeper 연결
      KAFKA_BROKER_ID: 1
```

### KRaft (신규 방식)

**개념:**
- Kafka 2.8 버전부터 도입된 ZooKeeper 제거 모드
- Kafka 자체가 메타데이터 관리 (P2P 방식)
- Raft 합의 알고리즘 사용

**구조:**
```
┌──────────────────────────────────────┐
│        Kafka Cluster (KRaft)         │
│  ├── Broker 1 (Controller)           │ ← 메타데이터도 직접 관리
│  ├── Broker 2 (Controller)           │
│  ├── Broker 3 (Controller)           │
│  └── Broker 4                        │
└──────────────────────────────────────┘

→ ZooKeeper 불필요!
→ Controller들이 Raft로 메타데이터 동기화
```

**장점:**
- 단순한 아키텍처 (ZooKeeper 제거)
- 빠른 메타데이터 변경 (ZooKeeper 병목 제거)
- 더 많은 파티션 지원 (수백만 개)
- 빠른 컨트롤러 장애 복구

**단점:**
- 상대적으로 짧은 검증 기간 (2021년 출시)
- 일부 기능 미지원 (계속 개선 중)
- 운영 경험 부족

**실제 구성:**
```yaml
# docker-compose.kafka.yml (KRaft 모드)
version: '3'
services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_PROCESS_ROLES: 'broker,controller'  # KRaft 모드
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:29093,2@kafka-2:29093,3@kafka-3:29093'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      # KAFKA_ZOOKEEPER_CONNECT 없음!
```

**KRaft 도입 현황 (2024년 기준):**
- Kafka 3.3 버전부터 프로덕션 준비 완료
- 대부분의 조직은 아직 ZooKeeper 사용 (기존 시스템)
- 신규 프로젝트는 KRaft 고려
- Kafka 4.0부터 ZooKeeper 지원 종료 예정

**선택 기준:**
```
ZooKeeper 선택:
- 기존 시스템 마이그레이션 부담
- 검증된 안정성 필요
- 2024년 현재 대부분의 조직

KRaft 선택:
- 신규 프로젝트
- 단순한 아키텍처 선호
- 대규모 파티션 필요 (수십만~수백만)
- 향후 마이그레이션 계획
```

---

## Apache Kafka vs Confluent Kafka

### Apache Kafka (순수 오픈소스)

**개념:**
- Apache Software Foundation에서 오픈소스로 제공
- 완전 무료
- 기본 기능만 제공

**특징:**
- Apache 2.0 라이선스 (상업적 사용 가능)
- 커뮤니티 기반 지원
- 직접 설치 및 운영

**설치 예시:**
```bash
# Apache Kafka 다운로드
wget https://downloads.apache.org/kafka/3.6.0/kafka_2.13-3.6.0.tgz
tar -xzf kafka_2.13-3.6.0.tgz
cd kafka_2.13-3.6.0

# ZooKeeper 시작
bin/zookeeper-server-start.sh config/zookeeper.properties

# Kafka 시작
bin/kafka-server-start.sh config/server.properties
```

### Confluent Kafka (상업용 배포판)

**개념:**
- Apache Kafka를 기반으로 Confluent사에서 개발
- Kafka 원 개발자들이 설립한 회사
- 무료 + 유료 기능 제공

**구성:**
```
Confluent Platform = Apache Kafka + 추가 도구

추가 도구:
- Schema Registry: 스키마 관리 (Avro, Protobuf)
- ksqlDB: SQL로 스트림 처리
- Kafka Connect: 다양한 시스템 연동
- Control Center: 웹 기반 모니터링 UI
- REST Proxy: HTTP API로 Kafka 사용
```

**Docker 이미지 차이:**
```yaml
# Apache Kafka (공식)
image: apache/kafka:3.6.0

# Confluent Kafka
image: confluentinc/cp-kafka:7.5.0  # 더 많은 환경변수 지원
```

**라이선스:**
- Confluent Community License: 무료 (일부 기능 제한)
- Confluent Enterprise License: 유료 (모든 기능 + 지원)

**비교:**

| 항목 | Apache Kafka | Confluent Kafka |
|------|--------------|-----------------|
| 가격 | 완전 무료 | 무료 + 유료 |
| 기본 기능 | ✅ | ✅ |
| Schema Registry | ❌ | ✅ |
| ksqlDB | ❌ | ✅ (무료) |
| Control Center | ❌ | ✅ (유료) |
| 기술 지원 | 커뮤니티 | 공식 지원 (유료) |
| Docker 지원 | 기본 | 최적화됨 |
| 클라우드 (Confluent Cloud) | ❌ | ✅ (유료) |

**선택 기준:**
```
Apache Kafka 선택:
- 완전 무료 사용
- 기본 메시징 기능만 필요
- 오픈소스 선호

Confluent Kafka 선택:
- Schema Registry 필요 (스키마 검증)
- ksqlDB로 스트림 처리
- Docker 환경 (환경변수 편의성)
- 기술 지원 필요 (Enterprise)
```

**우리 프로젝트 선택:**
```yaml
# docker-compose.kafka.yml
services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.0  # Confluent 이미지 사용

선택 이유:
1. Docker 환경 설정이 더 간편 (환경변수 풍부)
2. 무료 버전 사용 (Community License)
3. 향후 Schema Registry 도입 가능성
4. 대부분의 튜토리얼이 Confluent 기반
```

---

## 고급 개념

### Auto-Commit vs Manual-Commit

**Offset Commit**: 컨슈머가 메시지를 잘 받았다고 Kafka에 확인을 보내주는 기능

**왜 Commit이 필요한가?**
```
Partition 0: [M1] [M2] [M3] [M4] [M5]
              ↑           ↑
         Committed    현재 읽음

Consumer 재시작 시:
- Committed Offset부터 다시 읽기 시작
- M3부터 다시 소비 (M1, M2는 건너뜀)
```

### Auto-Commit (자동 커밋)

**설정:**
```yaml
kafka:
  consumer:
    enable-auto-commit: true
    auto-commit-interval-ms: 5000  # 5초마다 자동 커밋
```

**동작 방식:**
```
1. Consumer가 메시지 읽음 (M1, M2, M3)
2. 5초 경과
3. 자동으로 Offset 커밋 (현재 위치 저장)
4. 반복
```

**문제점:**
```kotlin
@KafkaListener(topics = ["order.created"])
fun consume(event: OrderCreatedEvent) {
    // 1. 메시지 읽음 (Offset 3)

    // 2. 처리 시작
    processOrder(event)  // ← 여기서 예외 발생!

    // 3. 5초 후 자동 커밋됨 (Offset 4로 증가)
}

→ 문제: 처리 실패했지만 Offset은 증가!
→ 결과: M3 메시지 유실!
```

**At-Most-Once (최대 한 번):**
- 메시지가 유실될 수 있음
- 중복 처리는 없음
- 빠르지만 안전하지 않음

### Manual-Commit (수동 커밋)

**설정:**
```yaml
kafka:
  consumer:
    enable-auto-commit: false  # 수동 커밋
```

**동작 방식:**
```kotlin
@KafkaListener(topics = ["order.created"])
fun consume(event: OrderCreatedEvent, ack: Acknowledgment) {
    try {
        // 1. 메시지 읽음

        // 2. 처리
        processOrder(event)

        // 3. 성공 시에만 커밋
        ack.acknowledge()  // ← 명시적 커밋

    } catch (e: Exception) {
        logger.error("Failed to process", e)
        // 커밋하지 않음 → 재처리됨
    }
}
```

**At-Least-Once (최소 한 번):**
- 메시지가 유실되지 않음
- 중복 처리 가능 (멱등성 필요)
- 안전하지만 느림

**중복 처리 시나리오:**
```
1. Consumer가 M3 처리 완료
2. ack.acknowledge() 호출
3. Kafka로 Commit 요청 전송
4. ← 네트워크 오류! Commit 실패
5. Consumer 재시작
6. M3부터 다시 읽음 (Offset이 커밋 안됨)
7. M3 중복 처리!

→ 해결: 멱등성(Idempotency) 보장 필요
```

**선택 기준:**
```
Auto-Commit 사용:
- 로그 수집, 분석 (유실 괜찮음)
- 처리 속도가 중요
- 메시지 유실 허용 가능

Manual-Commit 사용 (권장):
- 결제, 주문 등 중요 데이터
- 메시지 유실 절대 불가
- 정확성이 속도보다 중요
```

---

### DLQ (Dead Letter Queue)

**문제 상황:**
```
Partition: [M1] [M2] [M3-독극물] [M4] [M5]
                       ↑
                  처리 불가능!

→ M3 처리 실패 시 어떻게 할 것인가?
→ 계속 재시도? M4, M5는 언제 처리?
```

**DLQ (Dead Letter Queue)**: 처리할 수 없는 메시지를 별도 큐로 보내는 패턴

**구조:**
```
Topic: order.created
  ↓
Consumer
  ├─ 성공 → Commit
  └─ 실패 (3회 재시도 후)
       ↓
    DLQ Topic: order.created.dlq
       ↓
    수동 확인 및 재처리
```

**구현 예시:**
```kotlin
@Component
class OrderConsumer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topics.order-created-dlq}") private val dlqTopic: String,
) {
    private val maxRetries = 3

    @KafkaListener(topics = ["order.created"])
    fun consume(
        event: OrderCreatedEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        ack: Acknowledgment,
    ) {
        var retryCount = 0

        while (retryCount < maxRetries) {
            try {
                processOrder(event)
                ack.acknowledge()
                return  // 성공

            } catch (e: Exception) {
                retryCount++
                logger.warn("Retry $retryCount/$maxRetries for offset $offset", e)
                Thread.sleep(1000 * retryCount)  // 지수 백오프
            }
        }

        // 3회 실패 → DLQ로 전송
        logger.error("Max retries exceeded, sending to DLQ: offset=$offset")
        kafkaTemplate.send(
            dlqTopic,
            event.orderId.toString(),
            DLQMessage(
                originalTopic = "order.created",
                partition = partition,
                offset = offset,
                failureReason = "Max retries exceeded",
                payload = event,
                timestamp = Instant.now(),
            )
        )

        ack.acknowledge()  // DLQ 전송 후 Offset 증가 (다음 메시지 처리)
    }
}
```

**DLQ 메시지 처리:**
```kotlin
@Component
class DLQMonitor(
    private val slackNotifier: SlackNotifier,
) {
    @KafkaListener(topics = ["order.created.dlq"])
    fun monitorDLQ(dlqMessage: DLQMessage) {
        // 1. 알림 발송
        slackNotifier.send("⚠️ DLQ 메시지 발생: ${dlqMessage.failureReason}")

        // 2. DB에 기록 (수동 재처리 대기)
        dlqRepository.save(dlqMessage)

        // 3. 관리자가 DLQ 조회 → 원인 파악 → 수동 재처리
    }
}
```

**DLQ Best Practice:**
```
1. 재시도 횟수 제한 (무한 재시도 방지)
2. 지수 백오프 (Exponential Backoff)
3. DLQ 메시지에 메타데이터 포함 (원본 토픽, 파티션, 오프셋, 실패 이유)
4. DLQ 모니터링 및 알림
5. 주기적인 DLQ 점검 및 재처리
```

**Kafka 기본 DLQ vs 직접 구현:**
```
Kafka 기본 DLQ:
- Spring Kafka의 ErrorHandler 사용
- 설정만으로 DLQ 구현 가능

직접 구현:
- 세밀한 제어 가능
- 비즈니스 로직에 맞는 재시도 전략
- 우리 프로젝트는 직접 구현 (유연성)
```

---

### Retention (리텐션) 정책

**질문: 카프카에 들어간 메시지는 영원히 보관되는가?**

**답: 아니오. Retention 정책에 따라 오래된 메시지는 삭제됩니다.**

### 리텐션 정책 종류

**1. 시간 기반 (Time-based) - 가장 일반적**
```yaml
# application.yml 또는 토픽 설정
kafka:
  topics:
    order-created:
      retention-ms: 604800000  # 7일 (7 * 24 * 60 * 60 * 1000)
```

**동작:**
```
Day 1: [M1] [M2] [M3]
Day 2: [M1] [M2] [M3] [M4] [M5]
Day 7: [M1] [M2] [M3] [M4] [M5] [M20] [M21]
Day 8: [M4] [M5] ... [M20] [M21] [M22]  ← M1, M2, M3 삭제됨!
```

**일반적인 설정:**
- 개발: 1일 (disk-retention-ms: 86400000)
- 로그/분석: 7일
- 중요 데이터: 30일
- 감사 로그: 90일~365일

**2. 크기 기반 (Size-based)**
```yaml
kafka:
  topics:
    order-created:
      retention-bytes: 10737418240  # 10GB
```

**동작:**
```
Partition 0 크기: 12GB
→ 가장 오래된 메시지부터 삭제하여 10GB로 유지
```

**3. 삭제 정책 vs 압축 정책**

**Delete (삭제 정책) - 기본값**
```yaml
kafka:
  topics:
    order-created:
      cleanup-policy: delete  # 오래된 메시지 삭제
      retention-ms: 604800000  # 7일
```

**Compact (압축 정책)**
```yaml
kafka:
  topics:
    user-profile:
      cleanup-policy: compact  # 최신 값만 유지
```

**압축 정책 동작:**
```
Before Compaction:
Key: user-123, Value: {name: "Alice", age: 20}  (Offset 0)
Key: user-456, Value: {name: "Bob", age: 25}    (Offset 1)
Key: user-123, Value: {name: "Alice", age: 21}  (Offset 2) ← 업데이트
Key: user-789, Value: {name: "Charlie", age: 30} (Offset 3)
Key: user-123, Value: {name: "Alice", age: 22}  (Offset 4) ← 또 업데이트

After Compaction:
Key: user-456, Value: {name: "Bob", age: 25}    (Offset 1)
Key: user-789, Value: {name: "Charlie", age: 30} (Offset 3)
Key: user-123, Value: {name: "Alice", age: 22}  (Offset 4) ← 최신 값만
```

**압축 정책 사용 사례:**
- 사용자 프로필 (최신 상태만 필요)
- 상품 정보 (최신 가격/재고만 필요)
- 설정 값 (최신 설정만 필요)

**LRU, TTL과의 비교:**
```
Redis LRU (Least Recently Used):
- 메모리 부족 시 가장 오래 사용 안한 데이터 삭제
- 사용 빈도 기반

Redis TTL (Time To Live):
- 키별로 만료 시간 설정
- 개별 키 기반

Kafka Retention:
- 파티션 전체에 대한 정책
- 시간 또는 크기 기반
- 메시지 단위가 아닌 세그먼트 단위 삭제
```

---

### Idempotency (멱등성)

**문제: 같은 메시지를 여러 번 읽어도 괜찮은 시스템을 만들 수 없을까?**

### 카프카는 정확히 한 번(Exactly-Once)이 아니라 최소 한 번(At-Least-Once)을 보장

**중복 소비 시나리오:**
```
1. Consumer가 M3 처리 완료
2. ack.acknowledge() 호출
3. Kafka로 Commit 요청 전송
4. ← 네트워크 오류! Commit 실패
5. Consumer 재시작
6. Offset이 2이므로 M3부터 다시 읽음
7. M3 중복 처리!
```

**문제 예시:**
```kotlin
// 멱등하지 않은 코드
@KafkaListener(topics = ["payment.completed"])
fun processPayment(event: PaymentCompletedEvent) {
    // 포인트 적립
    userRepository.findById(event.userId)
        .addPoints(event.amount * 0.01)  // 1% 적립

    // 중복 소비 시:
    // 1번째: 1000원 적립 ✅
    // 2번째: 1000원 또 적립 ❌ (총 2000원 적립됨!)
}
```

### 멱등성 보장 방법

**방법 1: 유니크 키 제약**
```kotlin
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["eventId"])  // 중복 방지
    ]
)
data class PointHistory(
    @Id val id: Long,
    val eventId: String,  // Kafka 메시지 ID
    val userId: Long,
    val points: Long,
)

@KafkaListener(topics = ["payment.completed"])
fun processPayment(event: PaymentCompletedEvent) {
    try {
        // eventId로 중복 체크
        pointHistoryRepository.save(
            PointHistory(
                eventId = event.eventId,  // Kafka 메시지 고유 ID
                userId = event.userId,
                points = event.amount * 0.01,
            )
        )

        ack.acknowledge()

    } catch (e: DataIntegrityViolationException) {
        // 이미 처리된 메시지 (중복)
        logger.warn("Duplicate event: ${event.eventId}")
        ack.acknowledge()  // 중복이므로 스킵
    }
}
```

**방법 2: 상태 체크**
```kotlin
@KafkaListener(topics = ["order.confirmed"])
fun reserveInventory(event: OrderConfirmedEvent) {
    val order = orderRepository.findById(event.orderId)

    // 이미 처리됨?
    if (order.inventoryReserved) {
        logger.warn("Inventory already reserved: ${event.orderId}")
        ack.acknowledge()
        return
    }

    // 재고 차감
    inventoryService.reserve(event.items)

    // 상태 업데이트
    order.inventoryReserved = true
    orderRepository.save(order)

    ack.acknowledge()
}
```

**방법 3: 분산 락 + 트랜잭션**
```kotlin
@KafkaListener(topics = ["payment.completed"])
fun processPayment(event: PaymentCompletedEvent) {
    val lockKey = "payment:${event.paymentId}"

    redissonClient.getLock(lockKey).use { lock ->
        if (!lock.tryLock(5, TimeUnit.SECONDS)) {
            logger.warn("Lock acquisition failed: $lockKey")
            return  // 다른 Consumer가 처리 중
        }

        // 중복 체크
        if (paymentRepository.existsByPaymentId(event.paymentId)) {
            logger.warn("Duplicate payment: ${event.paymentId}")
            ack.acknowledge()
            return
        }

        // 결제 처리
        processPaymentInternal(event)

        ack.acknowledge()
    }
}
```

**Producer 멱등성 (Kafka 0.11+):**
```yaml
kafka:
  producer:
    enable-idempotence: true  # Producer 멱등성 활성화
```

**동작:**
- Producer가 메시지에 고유 ID 부여
- Broker가 중복 메시지 자동 제거
- 네트워크 재전송으로 인한 중복 방지

```
Producer → Kafka Broker

1. Send M1 (ID=1, Sequence=0)
2. ← Ack timeout
3. Resend M1 (ID=1, Sequence=0)  ← 동일 ID
4. Broker: "이미 받았음, 중복 제거"
5. ← Ack 성공

→ M1은 한 번만 저장됨!
```

---

### Zero-Payload vs Full-Payload

**질문: 카프카 메시지 안에는 어떤 내용을 담으면 좋을까?**

### Full-Payload (전체 데이터 포함)

**개념: 메시지에 모든 정보를 담아 전송**

**예시:**
```kotlin
data class ProductUpdatedEvent(
    val productId: Long,
    val name: String,  // 전체 정보
    val description: String,
    val price: Long,
    val stockQuantity: Int,
    val category: String,
    val images: List<String>,
    val seller: SellerInfo,
    val specifications: Map<String, String>,
    // ... 50개 필드
)

// Producer
kafkaTemplate.send(
    topic = "product.updated",
    key = productId.toString(),
    value = ProductUpdatedEvent(
        productId = 12345,
        name = "노트북",
        description = "고성능 노트북입니다...",
        price = 1500000,
        // ... 모든 필드
    )
)

// Consumer
@KafkaListener(topics = ["product.updated"])
fun updateProductCache(event: ProductUpdatedEvent) {
    // 메시지에 모든 정보가 있어서 DB 조회 불필요
    cacheService.update(event.productId, event)
}
```

**장점:**
- Consumer가 DB 조회 불필요 (빠름)
- DB 부하 감소
- 메시지만으로 처리 가능

**단점:**
- 메시지 크기 증가 (네트워크/디스크 부담)
- 민감 정보 노출 위험
- 스키마 변경 시 호환성 문제
- 일관성 문제 (메시지와 실제 DB 데이터 불일치 가능)

### Zero-Payload (ID만 포함)

**개념: 메시지에 ID만 담고 Consumer가 DB 조회**

**예시:**
```kotlin
data class ProductUpdatedEvent(
    val productId: Long,  // ID만!
    val updatedAt: Instant,  // 최소 메타데이터
)

// Producer
kafkaTemplate.send(
    topic = "product.updated",
    key = productId.toString(),
    value = ProductUpdatedEvent(
        productId = 12345,
        updatedAt = Instant.now(),
    )
)

// Consumer
@KafkaListener(topics = ["product.updated"])
fun updateProductCache(event: ProductUpdatedEvent) {
    // DB에서 최신 데이터 조회
    val product = productRepository.findById(event.productId)
        .orElseThrow()

    // 캐시 업데이트
    cacheService.update(product.id, product)
}
```

**장점:**
- 메시지 크기 최소화 (네트워크/디스크 효율)
- 민감 정보 노출 방지
- 항상 최신 데이터 보장 (DB 조회)
- 스키마 변경에 강함

**단점:**
- Consumer가 DB 조회 필요 (느림)
- DB 부하 증가
- 데이터가 삭제된 경우 처리 어려움

### 실무에서는 Zero-Payload가 압도적으로 많음

**이유:**

**1. 데이터 일관성**
```
Full-Payload 시나리오:

10:00:00 - 상품 가격 10,000원으로 업데이트
10:00:01 - Kafka 메시지 발행 (price=10,000)
10:00:02 - 상품 가격 12,000원으로 재업데이트
10:00:03 - Consumer가 첫 번째 메시지 처리 (price=10,000 적용)

→ 문제: Consumer가 구버전 데이터(10,000원) 처리!
→ 실제 DB는 12,000원인데 캐시는 10,000원

Zero-Payload 시나리오:

10:00:00 - 상품 가격 10,000원으로 업데이트
10:00:01 - Kafka 메시지 발행 (productId=123)
10:00:02 - 상품 가격 12,000원으로 재업데이트
10:00:03 - Consumer가 메시지 처리
             → DB 조회 → 12,000원 조회 → 최신 데이터 적용!

→ 해결: 항상 최신 데이터 보장!
```

**2. 보안**
```kotlin
// Full-Payload: 민감 정보 노출 위험
data class UserUpdatedEvent(
    val userId: Long,
    val email: String,  // 민감 정보
    val phoneNumber: String,  // 민감 정보
    val password: String,  // ❌ 절대 안됨!
)

// Zero-Payload: 안전
data class UserUpdatedEvent(
    val userId: Long,  // ID만
)
```

**3. 메시지 크기**
```
Full-Payload:
- 상품 정보: 5KB/메시지
- 초당 1000건 → 5MB/s
- 하루 → 430GB/day

Zero-Payload:
- 상품 ID: 100B/메시지
- 초당 1000건 → 100KB/s
- 하루 → 8.6GB/day

→ 50배 차이!
```

### 하이브리드 접근 (실무 권장)

**최소한의 필요 정보만 포함:**
```kotlin
data class OrderCreatedEvent(
    val orderId: Long,  // ← ID (필수)
    val userId: Long,  // ← 파티셔닝용
    val totalAmount: Long,  // ← 비즈니스 판단용 (알림 문구 등)
    val createdAt: Instant,  // ← 메타데이터

    // 전체 주문 상세는 포함 안함!
    // items, shippingAddress, etc. → Consumer가 DB 조회
)

// Consumer
@KafkaListener(topics = ["order.created"])
fun sendNotification(event: OrderCreatedEvent) {
    // totalAmount로 알림 문구 결정 (DB 조회 불필요)
    if (event.totalAmount >= 50000) {
        sendPushNotification(
            userId = event.userId,
            message = "5만원 이상 주문 완료! 무료배송 적용되었습니다."
        )
    } else {
        sendPushNotification(
            userId = event.userId,
            message = "주문이 완료되었습니다."
        )
    }

    // 상세 정보 필요 시에만 DB 조회
    // val order = orderRepository.findById(event.orderId)
}
```

**선택 기준:**
```
Zero-Payload 선택:
- 데이터 일관성 중요
- 민감 정보 포함
- 메시지 크기 최소화 필요
- 실시간성이 덜 중요 (DB 조회 시간 허용)

Full-Payload 선택:
- 실시간성 매우 중요 (DB 조회 불가)
- 불변 데이터 (로그, 이벤트 기록)
- DB 부하 최소화 필요
- 데이터가 삭제될 수 있음

Hybrid (실무 권장):
- ID + 최소 메타데이터
- 비즈니스 판단에 필요한 최소 정보만
- 상세 정보는 DB 조회
```

---

## 마무리

### 카프카 도입 체크리스트

**1. 카프카가 정말 필요한가?**
```
✅ 카프카 도입이 적합한 경우:
- 대용량 메시지 처리 (초당 수천~수만 건)
- 여러 시스템 간 느슨한 결합 필요
- 메시지 재처리 필요
- 이벤트 기반 아키텍처
- 실시간 스트림 처리

❌ 카프카가 과한 경우:
- 소규모 시스템 (초당 수십 건)
- 단순 요청-응답 패턴
- 실시간 응답 필요 (동기 처리)
- 운영 리소스 부족
```

**2. 인프라 준비**
```
개발: Docker Compose (3-broker 클러스터)
스테이징: AWS MSK, Confluent Cloud
프로덕션: AWS MSK (관리형) 또는 자체 운영
```

**3. 모니터링**
```
필수 지표:
- Consumer Lag (지연)
- Producer/Consumer Throughput (처리량)
- Broker Disk Usage (디스크 사용량)
- Partition Leader 분산

도구:
- Kafka UI (개발)
- Confluent Control Center (프로덕션)
- Prometheus + Grafana
```

**4. 운영 Best Practice**
```
1. Replication Factor ≥ 3 (프로덕션)
2. Manual Commit 사용 (중요 데이터)
3. DLQ 구현 (실패 처리)
4. Idempotency 보장 (중복 방지)
5. Zero-Payload 선호 (일관성)
6. Retention 정책 설정 (디스크 관리)
7. Consumer Group 모니터링
8. 정기적인 성능 테스트
```

### 참고 자료

- [Apache Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Confluent 문서](https://docs.confluent.io/)
- [Spring Kafka 문서](https://spring.io/projects/spring-kafka)
- [Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide/)
