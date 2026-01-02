# Kafka 핵심 가이드

## 목차
1. [카프카란?](#카프카란)
2. [핵심 구성 요소](#핵심-구성-요소)
3. [토픽과 파티션](#토픽과-파티션)
4. [컨슈머 그룹](#컨슈머-그룹)
5. [고급 개념](#고급-개념)
6. [실무 적용](#실무-적용)

---

## 카프카란?

**Apache Kafka**는 대규모 실시간 데이터 처리를 위한 **분산 메시징 시스템**입니다.

### 도입 효과

**Before (HTTP 직접 호출)**
```
[주문 서비스] → 결제, 재고, 알림, 분석 서비스 직접 호출
문제점: 강한 결합, 장애 전파, 낮은 확장성
```

**After (Kafka)**
```
[주문 서비스] → Kafka Topic → 각 서비스가 독립적으로 소비
개선점: 느슨한 결합, 장애 격리, 수평 확장 가능
```

### 핵심 특징
- **순서 보장**: 파티션 레벨에서 순서 보장 (토픽 레벨 아님)
- **영속성**: 메시지를 디스크에 저장하여 유실 방지
- **확장성**: 파티션과 컨슈머를 추가하여 처리량 증가
- **고가용성**: 복제를 통한 장애 대응

---

## 핵심 구성 요소

### Producer (프로듀서)
메시지를 카프카에 발행하는 주체입니다.

```kotlin
kafkaTemplate.send(
    topic = "order.created",
    key = orderId.toString(),  // 동일 Key → 같은 파티션 → 순서 보장
    value = event
)
```

### Consumer (컨슈머)
카프카에서 메시지를 읽어오는 주체입니다.

```kotlin
@KafkaListener(topics = ["order.created"], groupId = "payment-service-group")
fun consume(event: OrderCreatedEvent, ack: Acknowledgment) {
    processPayment(event)
    ack.acknowledge()  // 수동 커밋 (At-Least-Once)
}
```

### Offset (오프셋)
컨슈머가 어디까지 읽었는지 기록한 위치입니다.

```
Partition 0: [M0] [M1] [M2] [M3] [M4]
                          ↑
                     Offset 2 (현재 위치)
```

### Broker (브로커)
카프카 클러스터를 구성하는 물리적 서버입니다.

**역할**:
- Producer로부터 메시지 수신 및 저장
- Consumer에게 메시지 전송
- Leader Election 및 복제 관리

### Message (메시지)
```
Message = Key + Value + Headers + Timestamp

Key: 파티셔닝 기준 (orderId, userId 등)
Value: 실제 메시지 내용 (JSON, Avro 등)
Headers: 메타데이터 (traceId 등)
Timestamp: 생성/수신 시간
```

---

## 토픽과 파티션

### Topic (토픽)
메시지를 분류하는 논리적 단위입니다. (데이터베이스의 테이블과 유사)

### Partition (파티션)
실제 메시지가 저장되는 물리적 큐입니다.

```
Topic: order.created (3 Partitions)

Partition 0: [M1] [M4] [M7]  → Consumer A 담당
Partition 1: [M2] [M5] [M8]  → Consumer B 담당
Partition 2: [M3] [M6] [M9]  → Consumer C 담당
```

### 핵심 규칙
1. **하나의 파티션 = 하나의 컨슈머**: 순서 보장을 위함
2. **Key 기반 파티셔닝**: `hash(key) % partition_count`
   - 동일 Key → 항상 같은 파티션 → 순서 보장
3. **파티션 수 설계**: 처리량, 컨슈머 수, 브로커 수 고려

---

## 컨슈머 그룹

### Consumer Group
같은 토픽을 구독하는 컨슈머들의 논리적 그룹입니다.

```
Topic: order.created (3 Partitions)

Consumer Group: payment-service-group
  ├── Consumer A (Partition 0, 1)
  └── Consumer B (Partition 2)

Consumer Group: analytics-service-group
  ├── Consumer C (Partition 0)
  ├── Consumer D (Partition 1)
  └── Consumer E (Partition 2)
```

### 핵심 기능
- **그룹 내**: 메시지는 한 번만 처리 (중복 방지)
- **그룹 간**: 독립적으로 메시지 소비 (여러 서비스가 동일 이벤트 처리)
- **수평 확장**: 컨슈머 추가로 처리량 증가

### Rebalancing (리밸런싱)
컨슈머 추가/삭제 시 파티션을 재분배하는 과정입니다.

```
Before: Consumer A (P0, P1, P2, P3)  ← 과부하
After:  Consumer A (P0, P1) + Consumer B (P2, P3)  ← 균형
```

**주의**: 리밸런싱 중에는 메시지 처리가 중단됩니다.

---

## 고급 개념

### 1. Replication (복제)

**Replication Factor**: 파티션 복제본 개수

```
Replication Factor = 3 (권장)

Broker 1: Partition 0 (Leader)
Broker 2: Partition 0 (Follower)
Broker 3: Partition 0 (Follower)

→ Broker 2대 장애까지 견딤
```

### 2. Auto-Commit vs Manual-Commit

**Auto-Commit (자동 커밋)**
- 5초마다 자동으로 오프셋 커밋
- 장점: 간편함
- 단점: 처리 실패 시에도 커밋되어 메시지 유실 가능

**Manual-Commit (수동 커밋) ✅ 권장**
- 처리 성공 후 명시적 커밋
- 장점: 메시지 유실 방지
- 단점: 중복 처리 가능 → 멱등성 보장 필요

### 3. DLQ (Dead Letter Queue)

처리 실패한 메시지를 별도 토픽으로 보내는 패턴입니다.

```
Topic: order.created
  → Consumer 처리 실패 (3회 재시도)
  → DLQ Topic: order.created.dlq
  → 수동 확인 및 재처리
```

### 4. Retention (리텐션)

메시지 보관 정책입니다.

```yaml
# 시간 기반 (일반적)
retention-ms: 604800000  # 7일

# 크기 기반
retention-bytes: 10737418240  # 10GB

# 정책
cleanup-policy: delete   # 오래된 메시지 삭제
cleanup-policy: compact  # 최신 값만 유지 (Key 기반)
```

### 5. Idempotency (멱등성)

**문제**: Kafka는 At-Least-Once 보장 → 중복 처리 가능

**해결 방법**:
1. **Idempotency Key**: 외부 API 호출 시 중복 방지
2. **상태 조건 체크**: `WHERE status = 'TEMPORARY_RESERVED'`
3. **처리 이력 저장**: Redis/DB에 처리 여부 기록
4. **유니크 제약조건**: DB 레벨에서 중복 방지

### 6. Zero-Payload vs Full-Payload

**Zero-Payload (권장)**
```kotlin
// 메시지: ID만
data class ProductUpdatedEvent(val productId: Long)

// Consumer: DB 조회
val product = productRepository.findById(event.productId)
```

**장점**: 메시지 크기 최소화, 데이터 일관성, 민감정보 보호

**Full-Payload**
```kotlin
// 메시지: 전체 데이터
data class ProductUpdatedEvent(
    val productId: Long,
    val name: String,
    val price: Long,
    // ... 모든 필드
)
```

**장점**: DB 조회 불필요 (빠름), DB 부하 감소

---

## 실무 적용

### 배포 모드

**ZooKeeper (기존)**
- 검증된 안정성
- 별도 ZooKeeper 운영 필요

**KRaft (신규, Kafka 3.3+)**
- ZooKeeper 불필요
- 빠른 메타데이터 변경
- 더 많은 파티션 지원

### 토픽 설계

```bash
# 토픽 생성
kafka-topics --create \
  --topic order.created \
  --partitions 3 \              # 파티션 수 = 컨슈머 수
  --replication-factor 3 \      # 복제본 3개 (권장)
  --bootstrap-server localhost:9092
```

### Best Practice

**✅ 권장 사항**
- Replication Factor ≥ 3 (프로덕션)
- Manual Commit 사용 (중요 데이터)
- DLQ 구현 (실패 처리)
- Idempotency 보장 (중복 방지)
- Zero-Payload 선호 (일관성)
- Retention 정책 설정 (디스크 관리)

**❌ 피해야 할 것**
- 단일 브로커 운영
- Auto-Commit (중요 데이터)
- Full-Payload (민감 정보)
- 파티션 수 과다 설정

### 모니터링 지표

```
필수 지표:
- Consumer Lag (지연)
- Producer/Consumer Throughput (처리량)
- Broker Disk Usage (디스크 사용량)
- Partition Leader 분산

도구:
- Kafka UI (개발)
- Prometheus + Grafana (프로덕션)
```

### 도입 체크리스트

**카프카가 적합한 경우**
- 대용량 메시지 처리 (초당 수천 건 이상)
- 여러 시스템 간 느슨한 결합 필요
- 메시지 재처리 필요
- 이벤트 기반 아키텍처

**카프카가 과한 경우**
- 소규모 시스템 (초당 수십 건)
- 단순 요청-응답 패턴
- 실시간 동기 응답 필요
- 운영 리소스 부족

---

## 참고 자료

- [Apache Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Confluent 문서](https://docs.confluent.io/)
- [Spring Kafka 문서](https://spring.io/projects/spring-kafka)
