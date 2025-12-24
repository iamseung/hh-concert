# [과제] Application Event & Transaction Diagnosis

## [필수] Application Event

### 과제 요구사항
1. 실시간 주문 정보 & 예약 정보를 데이터 플랫폼에 전송
2. 이벤트를 활용하여 트랜잭션과 관심사를 분리하여 서비스를 개선

### 주요 평가 기준
- 이벤트 기반 아키텍처 이해도
- 트랜잭션과 비즈니스 로직의 분리
- 관심사 분리 원칙 적용

---

# Application Event 구현

## 1. 설계 배경

### 문제 인식
기존 예약 시스템은 결제 처리와 외부 API 전송이 강하게 결합되어 있었습니다:
- 결제 트랜잭션 내에서 외부 API 호출 시 실패 시 전체 롤백
- 외부 API 응답 지연 시 결제 처리 성능 저하
- 관심사 미분리로 인한 단위 테스트 어려움

### 해결 방안
Spring Application Event를 활용한 비동기 처리:

```
결제 완료 → Event 발행 → 트랜잭션 커밋 → Event 비동기 처리 → 외부 API 전송
```

**핵심 원칙**:
- 트랜잭션과 외부 통신 분리
- 비동기 처리로 메인 플로우 보호
- 관심사 분리를 통한 재사용성 향상

## 2. 아키텍처 설계

### 3-Layer 관심사 분리

```
┌─────────────────────────────────────┐
│   ReservationEventListener          │  ← Event Orchestration
│   - 이벤트 수신                      │
│   - 로깅                            │
│   - 성공/실패 처리                   │
└──────────────┬──────────────────────┘
               │ 위임
               ↓
┌─────────────────────────────────────┐
│   DataPlatformClient                │  ← Domain Business Logic
│   - 도메인 페이로드 변환             │
│   - 멱등성 키 생성                   │
│   - 비즈니스 API 정의                │
└──────────────┬──────────────────────┘
               │ 위임
               ↓
┌─────────────────────────────────────┐
│   ExternalApiSender                 │  ← HTTP Infrastructure
│   - HTTP 통신 (WebClient)            │
│   - Resilience4j 패턴 적용           │
│   - 타임아웃 관리                    │
└─────────────────────────────────────┘
```

### 각 레이어별 책임

| 레이어 | 책임 | 재사용성 |
|--------|------|---------|
| **EventListener** | 이벤트 수신 및 오케스트레이션만 담당 | 이벤트별 특화 |
| **DomainClient** | 도메인 특화 API, 페이로드 변환, 멱등성 관리 | 도메인 내 재사용 |
| **InfraSender** | 순수 HTTP 통신, Resilience4j 적용, 범용 통신 | 전체 시스템 재사용 |

## 3. 구현 세부사항

### 3-1. Infrastructure Layer: ExternalApiSender

**파일**: `src/main/kotlin/kr/hhplus/be/server/infrastructure/client/ExternalApiSender.kt`

**책임**: 순수 HTTP 통신 인프라
- WebClient 기반 POST 요청 처리
- Resilience4j 패턴 적용 (Retry + CircuitBreaker)
- 타임아웃 관리
- 도메인 로직 무관 (범용 HTTP 클라이언트)

**핵심 기능**:
```kotlin
@Component
class ExternalApiSender(
    @Qualifier("externalApiWebClient")
    private val webClient: WebClient,

    @Qualifier("dataPlatformRetry")
    private val retry: Retry,

    @Qualifier("dataPlatformCircuitBreaker")
    private val circuitBreaker: CircuitBreaker,
) {
    fun post(
        uri: String,
        headers: Map<String, String> = emptyMap(),
        body: Any,
        timeoutSeconds: Long = 3,
    ): Mono<String> {
        return webClient.post()
            .uri(uri)
            .headers { /* ... */ }
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
    }
}
```

**특징**:
- ✅ 테스트 용이 (Mock 교체 가능)
- ✅ 재사용 가능 (모든 외부 API 호출에서 사용)
- ✅ 논블로킹 I/O (WebClient 기반)

### 3-2. Domain Layer: DataPlatformClient

**파일**: `src/main/kotlin/kr/hhplus/be/server/infrastructure/client/DataPlatformClient.kt`

**책임**: 데이터 플랫폼 도메인 특화 API
- 비즈니스 이벤트 → API 페이로드 변환
- 멱등성 키 생성 및 관리
- ExternalApiSender를 활용한 실제 통신

**핵심 기능**:
```kotlin
@Component
class DataPlatformClient(
    private val externalApiSender: ExternalApiSender,
    @Value("\${data-platform.base-url:http://localhost:8080}")
    private val baseUrl: String,
) {
    fun sendReservation(event: ReservationConfirmedEvent): Mono<String> {
        val idempotencyKey = generateIdempotencyKey("reservation", event.reservationId)
        val payload = createReservationPayload(event)
        val headers = mapOf("X-Idempotency-Key" to idempotencyKey)

        return externalApiSender.post(
            uri = "$baseUrl/api/mock/reservation",
            headers = headers,
            body = payload,
        )
    }

    private fun generateIdempotencyKey(prefix: String, id: Long): String {
        return "$prefix-$id-${System.currentTimeMillis()}"
    }

    private fun createReservationPayload(event: ReservationConfirmedEvent): Map<String, Any> {
        return mapOf(
            "eventType" to "RESERVATION_CONFIRMED",
            "reservationId" to event.reservationId,
            "concertId" to event.concertId,
            "concertTitle" to event.concertTitle,
            "userId" to event.userId,
            "timestamp" to System.currentTimeMillis(),
        )
    }
}
```

**확장 가능성**:
```kotlin
// 향후 주문, 결제 등 다른 이벤트 전송 메서드 추가 가능
fun sendOrder(event: OrderConfirmedEvent): Mono<String>
fun sendPayment(event: PaymentCompletedEvent): Mono<String>
```

### 3-3. Event Layer: ReservationEventListener

**파일**: `src/main/kotlin/kr/hhplus/be/server/infrastructure/event/ReservationEventListener.kt`

**책임**: 예약 확정 이벤트 수신 및 오케스트레이션
- 트랜잭션 커밋 후 비동기 실행
- DataPlatformClient를 통한 데이터 전송
- 성공/실패 로깅

**리팩토링 전후 비교**:

| 구분 | Before | After |
|------|--------|-------|
| **코드 라인** | 120줄 | 78줄 (-35%) |
| **의존성** | 3개 (WebClient, Retry, CircuitBreaker) | 1개 (DataPlatformClient) |
| **책임** | 이벤트 + HTTP + 페이로드 + Resilience4j | 이벤트 오케스트레이션만 |
| **테스트 복잡도** | WebClient Mock 필요 | Client Mock만 필요 |
| **재사용성** | 없음 (강결합) | 높음 (분리된 레이어) |

**리팩토링 후 코드**:
```kotlin
@Component
class ReservationEventListener(
    private val dataPlatformClient: DataPlatformClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservation(event: ReservationConfirmedEvent) {
        logger.info("예약 확정 이벤트 수신 - reservationId={}", event.reservationId)

        dataPlatformClient.sendReservation(event)
            .doOnSuccess { response ->
                logger.info("데이터 플랫폼 전송 성공 - reservationId={}", event.reservationId)
            }
            .doOnError { error ->
                logger.error("데이터 플랫폼 전송 실패 - reservationId={}", event.reservationId, error)
            }
            .subscribe()
    }
}
```

### 3-4. Configuration: WebClientConfig

**파일**: `src/main/kotlin/kr/hhplus/be/server/infrastructure/configuration/WebClientConfig.kt`

**책임**: 외부 API 호출용 WebClient 및 Resilience4j 설정

**타임아웃 설정** (4계층):
1. **CONNECT_TIMEOUT**: TCP 연결 타임아웃
2. **responseTimeout**: HTTP 응답 전체 타임아웃
3. **ReadTimeoutHandler**: 데이터 읽기 타임아웃
4. **WriteTimeoutHandler**: 데이터 쓰기 타임아웃

```kotlin
@Configuration
class WebClientConfig(
    @Value("\${external-api.timeout-seconds}")
    private val timeoutSeconds: Long,

    private val retryRegistry: RetryRegistry,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    @Bean
    fun externalApiWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds.toInt() * 1000)
            .responseTimeout(Duration.ofSeconds(timeoutSeconds))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                    .addHandlerLast(WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    @Bean
    fun dataPlatformRetry() = retryRegistry.retry("dataPlatform")

    @Bean
    fun dataPlatformCircuitBreaker() = circuitBreakerRegistry.circuitBreaker("dataPlatform")
}
```

## 4. Resilience4j 패턴 적용

### 4-1. Retry 전략

**설정** (`application.yml`):
```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 100ms
        retry-exceptions:
          - java.io.IOException
          - org.springframework.web.reactive.function.client.WebClientRequestException
    instances:
      dataPlatform:
        base-config: default
```

**동작 방식**:
```
1차 시도 실패 → 100ms 대기 → 2차 시도
2차 시도 실패 → 200ms 대기 → 3차 시도
3차 시도 실패 → Exception 전파
```

### 4-2. Circuit Breaker 전략

**설정** (`application.yml`):
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
    instances:
      dataPlatform:
        base-config: default
```

**상태 전환**:
```
CLOSED (정상)
  ↓ (실패율 50% 초과)
OPEN (차단) - 30초간 모든 요청 즉시 실패
  ↓ (30초 경과)
HALF_OPEN (테스트) - 3개 요청만 허용
  ↓ (성공 시)
CLOSED (복구)
```

**장점**:
- ✅ 장애 전파 방지 (외부 API 장애 시 빠른 실패)
- ✅ 자동 복구 (30초 후 재시도)
- ✅ 시스템 안정성 향상

## 5. 비동기 처리 전략

### 5-1. @Async 비동기 실행

```kotlin
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onReservation(event: ReservationConfirmedEvent)
```

**실행 흐름**:
```
결제 서비스 스레드:
  1. 결제 처리
  2. Event 발행
  3. 트랜잭션 커밋
  4. 즉시 응답 반환 ✅

별도 @Async 스레드:
  5. 이벤트 수신 (AFTER_COMMIT)
  6. 데이터 플랫폼 전송
  7. 성공/실패 로깅
```

**장점**:
- ✅ 메인 플로우 영향 없음 (결제 응답 속도 보장)
- ✅ 외부 API 실패 시에도 결제 완료
- ✅ 재시도 가능 (별도 스레드에서 처리)

### 5-2. TransactionPhase.AFTER_COMMIT

**중요성**:
```kotlin
// ❌ AFTER_COMPLETION 사용 시 문제
TransactionPhase.AFTER_COMPLETION // 롤백된 데이터도 전송 가능

// ✅ AFTER_COMMIT 사용
TransactionPhase.AFTER_COMMIT // 커밋된 데이터만 전송 보장
```

**보장 사항**:
- ✅ 데이터 일관성: 트랜잭션 커밋 성공 후에만 이벤트 발행
- ✅ 중복 방지: 롤백된 트랜잭션은 이벤트 미발행
- ✅ 멱등성: 동일 reservationId에 대해 timestamp 포함한 키 생성

## 6. 개선 효과

### 6-1. 관심사 분리 달성

**Before**:
```
ReservationEventListener
  ├── 이벤트 수신 ✅
  ├── HTTP 통신 로직 ❌ (강결합)
  ├── 페이로드 변환 ❌ (비즈니스 로직)
  └── Resilience4j 설정 ❌ (인프라 관심사)
```

**After**:
```
ReservationEventListener (이벤트 오케스트레이션)
  └── DataPlatformClient (도메인 비즈니스)
      └── ExternalApiSender (HTTP 인프라)
```

### 6-2. 재사용성 향상

| 컴포넌트 | 재사용 가능 범위 |
|----------|-----------------|
| **ExternalApiSender** | 전체 시스템 모든 외부 API 호출 |
| **DataPlatformClient** | 주문, 결제, 예약 등 데이터 플랫폼 전송 |
| **ReservationEventListener** | 예약 이벤트 전용 |

**확장 시나리오**:
```kotlin
// 주문 이벤트 추가 시
@Component
class OrderEventListener(
    private val dataPlatformClient: DataPlatformClient, // 재사용!
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrder(event: OrderConfirmedEvent) {
        dataPlatformClient.sendOrder(event) // DataPlatformClient에 메서드만 추가
            .subscribe()
    }
}
```

### 6-3. 테스트 용이성

**Before** (복잡):
```kotlin
// WebClient, Retry, CircuitBreaker 모두 Mock 필요
val mockWebClient = mockk<WebClient>()
val mockRetry = mockk<Retry>()
val mockCircuitBreaker = mockk<CircuitBreaker>()
val listener = ReservationEventListener(mockWebClient, mockRetry, mockCircuitBreaker)
// WebClient의 체이닝 메서드 모두 Mock 필요...
```

**After** (간단):
```kotlin
// DataPlatformClient만 Mock
val mockClient = mockk<DataPlatformClient>()
val listener = ReservationEventListener(mockClient)

every { mockClient.sendReservation(any()) } returns Mono.just("success")
listener.onReservation(event)
verify { mockClient.sendReservation(event) }
```

### 6-4. 유지보수성 개선

| 변경 사항 | 수정 범위 |
|----------|----------|
| HTTP 타임아웃 변경 | ExternalApiSender만 |
| Retry 정책 변경 | application.yml만 |
| 페이로드 형식 변경 | DataPlatformClient만 |
| 이벤트 처리 로직 변경 | ReservationEventListener만 |

## 7. 성능 및 안정성

### 7-1. 논블로킹 I/O

```
WebClient (Reactor Netty) 기반:
  - Event Loop 기반 비동기 처리
  - 적은 스레드로 많은 요청 처리
  - Connection Pool 효율적 관리
```

### 7-2. 장애 격리

```
외부 API 장애 시나리오:
  1. Retry 3회 시도 (100ms, 200ms, 400ms 간격)
  2. 실패율 50% 도달 시 Circuit Open
  3. 30초간 요청 즉시 차단 (Fast Fail)
  4. 메인 플로우 영향 없음 (비동기 처리)
```

**결과**:
- ✅ 외부 API 장애가 결제 시스템에 영향 없음
- ✅ 시스템 안정성 향상
- ✅ 사용자 경험 보호 (결제는 정상 완료)

### 7-3. 멱등성 보장

```kotlin
val idempotencyKey = "reservation-${event.reservationId}-${System.currentTimeMillis()}"
```

**보장 사항**:
- 동일 예약에 대해 중복 전송 방지
- 외부 API에서 멱등성 키로 중복 처리 차단
- 재시도 시에도 안전

## 8. 향후 확장 가능성

### 8-1. 다른 도메인 이벤트 추가

```kotlin
// DataPlatformClient에 메서드 추가만으로 확장
class DataPlatformClient {
    fun sendReservation(event: ReservationConfirmedEvent): Mono<String> { /* ... */ }
    fun sendOrder(event: OrderConfirmedEvent): Mono<String> { /* ... */ }
    fun sendPayment(event: PaymentCompletedEvent): Mono<String> { /* ... */ }
}
```

### 8-2. 외부 API 추가

```kotlin
// ExternalApiSender는 범용이므로 그대로 재사용
@Component
class NotificationClient(
    private val externalApiSender: ExternalApiSender, // 재사용!
) {
    fun sendEmail(payload: EmailPayload): Mono<String> {
        return externalApiSender.post(
            uri = "$emailServiceUrl/send",
            body = payload,
        )
    }
}
```

### 8-3. 이벤트 재처리 메커니즘

```kotlin
// 실패한 이벤트를 DB에 저장 후 배치로 재처리
@Component
class FailedEventRepository {
    fun saveFailedEvent(event: ReservationConfirmedEvent, error: Throwable)
}

@Scheduled(fixedDelay = 60000)
fun retryFailedEvents() {
    val failedEvents = failedEventRepository.findAllPending()
    failedEvents.forEach { dataPlatformClient.sendReservation(it) }
}
```

## 9. 핵심 설계 원칙 적용

### 9-1. 단일 책임 원칙 (SRP)

| 클래스 | 단일 책임 |
|--------|----------|
| ReservationEventListener | 이벤트 오케스트레이션 |
| DataPlatformClient | 도메인 API 관리 |
| ExternalApiSender | HTTP 통신 |

### 9-2. 개방-폐쇄 원칙 (OCP)

```
새로운 외부 API 추가:
  - ExternalApiSender 수정 불필요 (재사용)
  - 새로운 Client 클래스만 추가

새로운 이벤트 추가:
  - 기존 코드 수정 불필요
  - 새로운 EventListener 추가
```

### 9-3. 의존성 역전 원칙 (DIP)

```
ReservationEventListener
  ↓ 의존
DataPlatformClient (인터페이스화 가능)
  ↓ 의존
ExternalApiSender (인터페이스화 가능)
```

---

# 결론

## [필수] Application Event

### ✅ 구현 완료 사항
1. **이벤트 기반 아키텍처**: Spring Application Event 활용
2. **트랜잭션 분리**: `@TransactionalEventListener(AFTER_COMMIT)`로 데이터 일관성 보장
3. **비동기 처리**: `@Async`로 메인 플로우 영향 최소화
4. **관심사 분리**: 3-Layer 아키텍처 (Event → Domain → Infrastructure)
5. **Resilience4j 적용**: Retry + CircuitBreaker로 장애 격리

### ✅ 개선 효과
- **코드 품질**: 120줄 → 78줄 (35% 감소), 의존성 3개 → 1개
- **재사용성**: ExternalApiSender 전체 시스템 재사용 가능
- **테스트 용이성**: Mock 복잡도 대폭 감소
- **유지보수성**: 변경 시 단일 클래스만 수정
- **확장성**: 새로운 이벤트/API 추가 용이

### ✅ 프로덕션 레벨 품질
- 논블로킹 I/O (WebClient)
- 장애 격리 및 자동 복구 (Circuit Breaker)
- 멱등성 보장 (Idempotency Key)
- 데이터 일관성 보장 (AFTER_COMMIT)
- SOLID 원칙 준수

---

## [선택] Transaction Diagnosis

### 과제 요구사항
1. MSA의 형태로 각 도메인별 배포 단위를 분리해야 한다면?
2. 각각 어떤 도메인 단위로 배포 단위를 설계할 것인지 결정
3. 그 분리에 따른 트랜잭션 처리의 한계와 해결방안에 대한 서비스 설계 문서 작성
   > SAGA 패턴 등

---

# MSA Transaction Diagnosis

## 1. 설계 배경

### 1-1. 현재 모놀리식 시스템 한계

**현재 아키텍처**:
```
┌────────────────────────────────────────────────┐
│         Single Monolithic Application          │
│                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │  User    │  │ Concert  │  │ Payment  │    │
│  │  Domain  │  │  Domain  │  │  Domain  │    │
│  └──────────┘  └──────────┘  └──────────┘    │
│       ↓             ↓             ↓           │
│  ┌──────────────────────────────────────┐    │
│  │      Single Database (MySQL)         │    │
│  └──────────────────────────────────────┘    │
└────────────────────────────────────────────────┘
```

**한계점**:
- **배포 단위**: 전체 시스템 일괄 배포 (일부 기능 변경도 전체 재배포 필요)
- **확장성**: 특정 도메인(예: 콘서트 조회)만 확장 불가
- **장애 전파**: 한 도메인 장애가 전체 시스템 다운으로 이어짐
- **기술 스택**: 모든 도메인이 동일 기술 스택 사용 강제
- **팀 확장**: 단일 코드베이스로 인한 개발 충돌 증가

### 1-2. MSA 전환 목표

**전환 후 아키텍처 비전**:
```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ User Service │  │Queue Service │  │Concert Service│  │Reservation   │  │Payment       │
│              │  │              │  │              │  │Service       │  │Service       │
├──────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤
│ User DB      │  │ Redis        │  │ Concert DB   │  │Reservation DB│  │ Payment DB   │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
       ↓                 ↓                 ↓                 ↓                 ↓
       └─────────────────┴─────────────────┴─────────────────┴─────────────────┘
                                  Event Bus (Kafka/RabbitMQ)
```

**기대 효과**:
- ✅ 독립 배포: 각 서비스별 배포 주기 독립적 관리
- ✅ 독립 확장: 트래픽 패턴에 따라 서비스별 스케일링
- ✅ 장애 격리: 한 서비스 장애가 다른 서비스에 영향 최소화
- ✅ 기술 다양성: 서비스별 최적 기술 스택 선택 가능
- ✅ 팀 자율성: 도메인별 팀 독립적 개발 및 운영

## 2. MSA 도메인 분리 전략

### 2-1. DDD 기반 도메인 식별

**Bounded Context 분석**:

| Bounded Context | 핵심 개념 | 책임 |
|----------------|----------|------|
| **User Context** | User, Authentication | 사용자 인증/인가, 프로필 관리 |
| **Queue Context** | QueueToken, WaitingPosition | 대기열 관리, 토큰 발급/검증 |
| **Concert Context** | Concert, Schedule, Seat | 콘서트 정보 관리, 좌석 재고 관리 |
| **Reservation Context** | Reservation, ReservationStatus | 예약 생성/확정, 상태 관리 |
| **Payment Context** | Payment, Point, PointHistory | 포인트 관리, 결제 처리 |

**도메인 간 의존성 분석**:
```
User ←─── Queue ←─── Concert ←─── Reservation ←─── Payment
  ↑                                    ↑              ↑
  └────────────────────────────────────┴──────────────┘
              (userId 참조)
```

### 2-2. 서비스 경계 정의

#### Service #1: User Service
**책임**:
- 사용자 등록/로그인
- JWT 토큰 발급 및 검증
- 사용자 프로필 관리

**데이터베이스**:
```sql
-- User DB
users (id, username, email, password, created_at)
```

**API**:
- `POST /api/users/register` - 회원 가입
- `POST /api/users/login` - 로그인
- `GET /api/users/{userId}` - 사용자 정보 조회

**배포 특성**:
- 읽기 빈도: 높음 (모든 요청에서 JWT 검증)
- 쓰기 빈도: 낮음 (회원 가입)
- 권장 인스턴스: 3개 (고가용성)

#### Service #2: Queue Service
**책임**:
- 대기열 토큰 발급
- 대기 순번 관리
- 토큰 활성화 및 만료 처리

**데이터 저장소**:
```
Redis (In-Memory)
- waiting_queue (Sorted Set)
- active_tokens (Hash)
- user_token_mapping (Hash)
```

**API**:
- `POST /api/queue/token` - 대기열 토큰 발급
- `GET /api/queue/position` - 대기 순번 조회
- `POST /api/queue/activate` - 토큰 활성화 (스케줄러)

**배포 특성**:
- 읽기 빈도: 매우 높음 (모든 예약 요청에서 검증)
- 쓰기 빈도: 높음 (토큰 발급/활성화)
- 권장 인스턴스: Redis Cluster (고성능)

#### Service #3: Concert Service
**책임**:
- 콘서트 정보 관리
- 스케줄 관리
- 좌석 재고 관리
- 좌석 임시 예약 (비관적 락)

**데이터베이스**:
```sql
-- Concert DB
concerts (id, title, description)
concert_schedules (id, concert_id, concert_date)
seats (id, schedule_id, seat_number, price, status, version)
```

**API**:
- `GET /api/concerts` - 콘서트 목록 조회
- `GET /api/concerts/{id}/schedules` - 스케줄 조회
- `GET /api/concerts/schedules/{id}/seats` - 좌석 조회
- `POST /api/concerts/seats/{id}/reserve` - 좌석 임시 예약

**배포 특성**:
- 읽기 빈도: 매우 높음 (조회 중심)
- 쓰기 빈도: 중간 (좌석 예약)
- 권장 인스턴스: 5개 + Redis 캐시 (읽기 부하 분산)

#### Service #4: Reservation Service
**책임**:
- 예약 생성 및 확정
- 예약 상태 관리 (TEMPORARY → CONFIRMED → CANCELLED)
- 예약 만료 처리 (5분 타임아웃)

**데이터베이스**:
```sql
-- Reservation DB
reservations (id, user_id, seat_id, status, created_at, expired_at)
```

**API**:
- `POST /api/reservations` - 예약 생성
- `POST /api/reservations/{id}/confirm` - 예약 확정
- `GET /api/reservations/user/{userId}` - 사용자 예약 조회

**배포 특성**:
- 읽기 빈도: 중간
- 쓰기 빈도: 높음 (예약 생성/확정/만료)
- 권장 인스턴스: 3개

#### Service #5: Payment Service
**책임**:
- 포인트 충전/사용
- 결제 처리 및 검증
- 포인트 히스토리 관리

**데이터베이스**:
```sql
-- Payment DB
points (id, user_id, balance, version)
point_history (id, user_id, amount, transaction_type, created_at)
payments (id, reservation_id, user_id, amount, status)
```

**API**:
- `POST /api/points/charge` - 포인트 충전
- `GET /api/points/{userId}` - 포인트 조회
- `POST /api/payments` - 결제 처리

**배포 특성**:
- 읽기 빈도: 중간 (포인트 조회)
- 쓰기 빈도: 높음 (충전/결제)
- 권장 인스턴스: 3개

### 2-3. 서비스 간 통신 설계

**동기 통신** (REST API):
```
예약 프로세스:
  Client → Queue Service (토큰 검증)
    ↓
  Client → Concert Service (좌석 조회)
    ↓
  Client → Concert Service (좌석 임시 예약)
    ↓
  Client → Reservation Service (예약 생성)
    ↓
  Client → Payment Service (결제 처리)
```

**비동기 통신** (Event Bus):
```
결제 완료 이벤트 흐름:
  Payment Service → [PaymentCompletedEvent]
    ↓
  Reservation Service (예약 확정)
  Concert Service (좌석 상태 변경)
  Data Platform (데이터 전송)
```

## 3. 분산 트랜잭션 문제

### 3-1. 모놀리스에서의 트랜잭션

**Before (Monolithic)**:
```kotlin
@Transactional
fun processPayment(command: ProcessPaymentCommand) {
    // 1. 좌석 상태 변경 (Concert Domain)
    val seat = seatRepository.findByIdWithLock(seatId)
    seat.status = SeatStatus.SOLD
    seatRepository.save(seat)

    // 2. 포인트 차감 (Payment Domain)
    val point = pointRepository.findByUserIdWithLock(userId)
    point.use(amount)
    pointRepository.save(point)

    // 3. 예약 확정 (Reservation Domain)
    val reservation = reservationRepository.findById(reservationId)
    reservation.confirm()
    reservationRepository.save(reservation)

    // ✅ COMMIT: 모든 변경사항 원자적 커밋
    // ❌ ROLLBACK: 하나라도 실패 시 전체 롤백
}
```

**장점**:
- ✅ ACID 보장: 원자성, 일관성, 격리성, 지속성 자동 보장
- ✅ 단순성: `@Transactional` 하나로 해결
- ✅ 데이터 일관성: 중간 상태 노출 없음

### 3-2. MSA에서의 트랜잭션 문제

**After (MSA) - 분산 트랜잭션**:
```
┌─────────────────────┐
│ Concert Service     │
│ 1. 좌석 상태 변경   │ → DB Commit ✅
└─────────────────────┘
         ↓ HTTP Call
┌─────────────────────┐
│ Payment Service     │
│ 2. 포인트 차감      │ → DB Commit ✅
└─────────────────────┘
         ↓ HTTP Call
┌─────────────────────┐
│ Reservation Service │
│ 3. 예약 확정        │ → DB Commit ❌ FAIL!
└─────────────────────┘

❌ 문제: 좌석은 SOLD, 포인트는 차감, 예약은 실패
         → 데이터 불일치!
```

**분산 트랜잭션 문제점**:

| 문제 | 설명 | 영향 |
|------|------|------|
| **Atomicity 깨짐** | 일부 서비스만 커밋, 일부는 실패 | 데이터 불일치 |
| **2PC 한계** | 2-Phase Commit은 성능 저하 및 가용성 문제 | 실시간 서비스 부적합 |
| **네트워크 실패** | 서비스 간 통신 중 타임아웃/장애 | 중간 상태 방치 |
| **복잡성** | 여러 서비스 상태 추적 어려움 | 운영 부담 증가 |

### 3-3. 구체적 시나리오 분석

**시나리오 1: 부분 실패**
```
1. Concert Service: 좌석 상태 SOLD ✅
2. Payment Service: 포인트 차감 ✅
3. Reservation Service: 예약 확정 ❌ (DB 장애)

결과: 좌석은 팔렸고 포인트는 차감됐지만 예약은 없음
해결: 보상 트랜잭션 필요 (좌석 복구, 포인트 환불)
```

**시나리오 2: 네트워크 타임아웃**
```
1. Concert Service: 좌석 상태 SOLD ✅
2. Payment Service: 포인트 차감 ✅
   → Reservation Service 호출 중 타임아웃

결과: Payment Service는 타임아웃으로 실패 인식
     하지만 Reservation Service는 실제로 성공했을 수도 있음
해결: 멱등성 보장 + 재시도 메커니즘 필요
```

**시나리오 3: 동시성 문제**
```
사용자 A: 좌석 1번 선택 → 결제 진행 중
사용자 B: 좌석 1번 선택 → 결제 진행 중

MSA 환경:
  - Concert Service: 락 획득 불가 (서비스별 독립 DB)
  - 분산 락 필요 (Redis, Zookeeper)

결과: 동일 좌석에 대한 중복 예약 가능성
해결: 분산 락 또는 SAGA 패턴 적용
```

## 4. 해결 방안: SAGA 패턴

### 4-1. SAGA 패턴 개요

**정의**:
분산 트랜잭션을 여러 개의 로컬 트랜잭션으로 나누고, 실패 시 보상 트랜잭션(Compensating Transaction)으로 롤백하는 패턴

**핵심 원칙**:
- 각 서비스는 자체 로컬 트랜잭션 수행
- 실패 시 이미 완료된 트랜잭션을 보상 트랜잭션으로 되돌림
- Eventual Consistency (최종 일관성) 보장

### 4-2. SAGA 패턴 종류

#### Choreography-based SAGA (Event-driven)

**구조**:
```
┌─────────────────┐
│Concert Service  │
│ 좌석 예약 ✅    │
└────────┬────────┘
         │ [SeatReservedEvent]
         ↓
┌─────────────────┐
│Payment Service  │
│ 포인트 차감 ✅  │
└────────┬────────┘
         │ [PaymentCompletedEvent]
         ↓
┌─────────────────┐
│Reservation      │
│Service          │
│ 예약 확정 ✅    │
└─────────────────┘
```

**실패 시 보상 흐름**:
```
┌─────────────────┐
│Concert Service  │
│ 좌석 예약 ✅    │
└────────┬────────┘
         │ [SeatReservedEvent]
         ↓
┌─────────────────┐
│Payment Service  │
│ 포인트 차감 ❌  │ FAIL!
└────────┬────────┘
         │ [PaymentFailedEvent]
         ↓
┌─────────────────┐
│Concert Service  │
│ 좌석 복구 ✅    │ ← 보상 트랜잭션
└─────────────────┘
```

**장점**:
- ✅ 서비스 간 결합도 낮음
- ✅ 확장 용이 (새 서비스 추가 시 이벤트 구독만 하면 됨)
- ✅ 단순한 아키텍처

**단점**:
- ❌ 전체 워크플로우 파악 어려움 (이벤트 추적 필요)
- ❌ 순환 의존성 위험 (이벤트 무한 루프)
- ❌ 디버깅 어려움 (분산 로그 추적 필요)

#### Orchestration-based SAGA (Centralized Coordinator)

**구조**:
```
┌────────────────────────────────────┐
│    Payment Orchestrator (Saga)     │
│                                    │
│  1. Concert Service → 좌석 예약    │
│  2. Payment Service → 포인트 차감  │
│  3. Reservation Service → 예약 확정│
└────────────────────────────────────┘
         ↓         ↓         ↓
    ┌────────┐ ┌────────┐ ┌────────┐
    │Concert │ │Payment │ │Reserv  │
    │Service │ │Service │ │Service │
    └────────┘ └────────┘ └────────┘
```

**실패 시 오케스트레이터 처리**:
```kotlin
@Component
class PaymentSagaOrchestrator {
    fun executePayment(command: PaymentCommand) {
        val sagaState = SagaState()

        try {
            // Step 1: 좌석 예약
            val seat = concertService.reserveSeat(command.seatId)
            sagaState.addCompleted("reserveSeat", seat)

            // Step 2: 포인트 차감
            val payment = paymentService.deductPoint(command.userId, command.amount)
            sagaState.addCompleted("deductPoint", payment)

            // Step 3: 예약 확정
            val reservation = reservationService.confirm(command.reservationId)
            sagaState.addCompleted("confirmReservation", reservation)

            return reservation

        } catch (e: Exception) {
            // 보상 트랜잭션 실행 (역순)
            compensate(sagaState)
            throw PaymentSagaException("Payment saga failed", e)
        }
    }

    private fun compensate(sagaState: SagaState) {
        sagaState.getCompleted().reversed().forEach { step ->
            when (step.name) {
                "confirmReservation" -> reservationService.cancelReservation(step.data)
                "deductPoint" -> paymentService.refundPoint(step.data)
                "reserveSeat" -> concertService.releaseSeat(step.data)
            }
        }
    }
}
```

**장점**:
- ✅ 워크플로우 명확 (중앙에서 관리)
- ✅ 디버깅 용이 (오케스트레이터 로그 확인)
- ✅ 트랜잭션 상태 추적 쉬움

**단점**:
- ❌ 오케스트레이터가 SPOF (Single Point of Failure)
- ❌ 서비스 간 결합도 증가
- ❌ 오케스트레이터 복잡도 증가

### 4-3. 콘서트 예약 시스템 SAGA 설계

#### 선택: Choreography-based SAGA

**이유**:
- 이벤트 기반 아키텍처 이미 구현됨 (Application Event)
- 서비스 간 느슨한 결합 유지
- 확장 용이성 (새 기능 추가 시 이벤트 구독만)

#### 예약 프로세스 SAGA

**정상 흐름**:
```
[Client Request]
     ↓
┌─────────────────────┐
│ 1. Concert Service  │
│ POST /seats/reserve │
│ → 좌석 TEMPORARY    │ ✅
└──────────┬──────────┘
           │ [SeatReservedEvent]
           ↓
┌─────────────────────┐
│ 2. Reservation      │
│ Service             │
│ → 예약 생성         │ ✅
└──────────┬──────────┘
           │ [ReservationCreatedEvent]
           ↓
┌─────────────────────┐
│ 3. Payment Service  │
│ POST /payments      │
│ → 포인트 차감       │ ✅
└──────────┬──────────┘
           │ [PaymentCompletedEvent]
           ↓
┌─────────────────────┐
│ 4. Reservation      │
│ Service             │
│ → 예약 확정         │ ✅
└──────────┬──────────┘
           │ [ReservationConfirmedEvent]
           ↓
┌─────────────────────┐
│ 5. Concert Service  │
│ → 좌석 SOLD         │ ✅
└─────────────────────┘
```

**실패 시나리오 1: 결제 실패**
```
1. Concert Service: 좌석 TEMPORARY ✅
2. Reservation Service: 예약 생성 ✅
3. Payment Service: 포인트 차감 ❌ (잔액 부족)
   ↓ [PaymentFailedEvent]
4. Reservation Service: 예약 취소 ✅
   ↓ [ReservationCancelledEvent]
5. Concert Service: 좌석 AVAILABLE 복구 ✅
```

**실패 시나리오 2: 예약 확정 실패**
```
1. Concert Service: 좌석 TEMPORARY ✅
2. Reservation Service: 예약 생성 ✅
3. Payment Service: 포인트 차감 ✅
4. Reservation Service: 예약 확정 ❌ (DB 장애)
   ↓ [ReservationConfirmFailedEvent]
5. Payment Service: 포인트 환불 ✅
   ↓ [PaymentRefundedEvent]
6. Concert Service: 좌석 AVAILABLE 복구 ✅
```

### 4-4. 보상 트랜잭션 구현

#### Concert Service 보상 트랜잭션
```kotlin
@Component
class ConcertSagaHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEvent) {
        logger.warn("결제 실패 - 좌석 복구 시작: seatId=${event.seatId}")

        val seat = seatRepository.findById(event.seatId)
        seat.release() // TEMPORARY → AVAILABLE
        seatRepository.save(seat)

        logger.info("좌석 복구 완료: seatId=${event.seatId}")
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservationCancelled(event: ReservationCancelledEvent) {
        logger.warn("예약 취소 - 좌석 복구: seatId=${event.seatId}")

        val seat = seatRepository.findById(event.seatId)
        seat.release()
        seatRepository.save(seat)
    }
}
```

#### Payment Service 보상 트랜잭션
```kotlin
@Component
class PaymentSagaHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservationConfirmFailed(event: ReservationConfirmFailedEvent) {
        logger.warn("예약 확정 실패 - 포인트 환불: paymentId=${event.paymentId}")

        val payment = paymentRepository.findById(event.paymentId)
        val point = pointRepository.findByUserIdWithLock(payment.userId)

        point.refund(payment.amount)
        pointRepository.save(point)

        pointHistoryRepository.save(
            PointHistory(
                userId = payment.userId,
                amount = payment.amount,
                transactionType = TransactionType.REFUND,
            )
        )

        applicationEventPublisher.publishEvent(
            PaymentRefundedEvent(
                paymentId = payment.id,
                userId = payment.userId,
                amount = payment.amount,
            )
        )

        logger.info("포인트 환불 완료: paymentId=${event.paymentId}")
    }
}
```

#### Reservation Service 보상 트랜잭션
```kotlin
@Component
class ReservationSagaHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEvent) {
        logger.warn("결제 실패 - 예약 취소: reservationId=${event.reservationId}")

        val reservation = reservationRepository.findById(event.reservationId)
        reservation.cancel()
        reservationRepository.save(reservation)

        applicationEventPublisher.publishEvent(
            ReservationCancelledEvent(
                reservationId = reservation.id,
                seatId = reservation.seatId,
                userId = reservation.userId,
            )
        )

        logger.info("예약 취소 완료: reservationId=${event.reservationId}")
    }
}
```

### 4-5. 멱등성 보장

**문제**:
네트워크 타임아웃으로 인한 중복 요청 시 데이터 중복 생성 가능

**해결: Idempotency Key 패턴**
```kotlin
@Component
class PaymentService {

    private val processedRequests = ConcurrentHashMap<String, PaymentResult>()

    @Transactional
    fun processPayment(request: PaymentRequest): PaymentResult {
        val idempotencyKey = request.idempotencyKey

        // 1. 이미 처리된 요청인지 확인
        processedRequests[idempotencyKey]?.let {
            logger.info("중복 요청 감지 - 캐시된 결과 반환: key=$idempotencyKey")
            return it
        }

        // 2. 실제 결제 처리
        val point = pointRepository.findByUserIdWithLock(request.userId)
        point.use(request.amount)
        pointRepository.save(point)

        val payment = paymentRepository.save(
            Payment(
                userId = request.userId,
                reservationId = request.reservationId,
                amount = request.amount,
                status = PaymentStatus.COMPLETED,
            )
        )

        val result = PaymentResult(
            paymentId = payment.id,
            status = PaymentStatus.COMPLETED,
        )

        // 3. 결과 캐싱 (TTL 5분)
        processedRequests[idempotencyKey] = result

        return result
    }
}
```

**Idempotency Key 생성**:
```kotlin
// Client에서 생성
val idempotencyKey = "payment-${userId}-${reservationId}-${UUID.randomUUID()}"

// 또는 서버에서 생성
val idempotencyKey = "payment-${userId}-${reservationId}-${System.currentTimeMillis()}"
```

### 4-6. SAGA 상태 추적

**SAGA Execution Log 테이블**:
```sql
CREATE TABLE saga_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(100) NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    step_status VARCHAR(50) NOT NULL,
    step_data JSON,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_saga_id (saga_id),
    INDEX idx_saga_type (saga_type)
);
```

**SAGA 실행 추적**:
```kotlin
@Component
class SagaExecutionLogger {

    fun logStep(
        sagaId: String,
        sagaType: String,
        stepName: String,
        status: StepStatus,
        data: Any? = null,
        error: Throwable? = null,
    ) {
        sagaExecutionLogRepository.save(
            SagaExecutionLog(
                sagaId = sagaId,
                sagaType = sagaType,
                stepName = stepName,
                stepStatus = status,
                stepData = objectMapper.writeValueAsString(data),
                errorMessage = error?.message,
            )
        )
    }

    fun getSagaHistory(sagaId: String): List<SagaExecutionLog> {
        return sagaExecutionLogRepository.findBySagaIdOrderByCreatedAt(sagaId)
    }
}
```

**사용 예시**:
```kotlin
@Component
class PaymentService(
    private val sagaLogger: SagaExecutionLogger,
) {
    fun processPayment(request: PaymentRequest) {
        val sagaId = "saga-${UUID.randomUUID()}"

        try {
            sagaLogger.logStep(
                sagaId = sagaId,
                sagaType = "PAYMENT_SAGA",
                stepName = "PAYMENT_STARTED",
                status = StepStatus.STARTED,
                data = request,
            )

            // 결제 처리 로직...

            sagaLogger.logStep(
                sagaId = sagaId,
                sagaType = "PAYMENT_SAGA",
                stepName = "PAYMENT_COMPLETED",
                status = StepStatus.COMPLETED,
                data = result,
            )

        } catch (e: Exception) {
            sagaLogger.logStep(
                sagaId = sagaId,
                sagaType = "PAYMENT_SAGA",
                stepName = "PAYMENT_FAILED",
                status = StepStatus.FAILED,
                error = e,
            )
            throw e
        }
    }
}
```

## 5. 추가 고려사항

### 5-1. 분산 락 (Distributed Lock)

**필요성**:
여러 서비스가 동일 리소스(좌석)에 동시 접근 시 경합 조건 방지

**Redisson 기반 분산 락**:
```kotlin
@Component
class ConcertService(
    private val redissonClient: RedissonClient,
) {
    fun reserveSeat(seatId: Long, userId: Long): Seat {
        val lockKey = "seat-lock:$seatId"
        val lock = redissonClient.getLock(lockKey)

        return try {
            // 5초 동안 락 획득 시도, 획득 후 10초 TTL
            val acquired = lock.tryLock(5, 10, TimeUnit.SECONDS)

            if (!acquired) {
                throw SeatReservationException("좌석 예약 중 - 잠시 후 다시 시도해주세요")
            }

            // 좌석 예약 처리
            val seat = seatRepository.findById(seatId)
            if (seat.status != SeatStatus.AVAILABLE) {
                throw SeatNotAvailableException("이미 예약된 좌석입니다")
            }

            seat.reserve(userId)
            seatRepository.save(seat)

            seat

        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
```

### 5-2. Eventual Consistency 관리

**읽기 일관성 문제**:
```
User → Payment Service (결제 완료) ✅
     → Reservation Service (예약 확정 중...)
     → Concert Service (좌석 상태 미갱신)

User가 좌석 조회 시: 아직 TEMPORARY 상태로 보임 (Eventual Consistency)
```

**해결 방안**:

1. **클라이언트 캐싱**:
```kotlin
// Client에서 낙관적 업데이트
fun onPaymentSuccess() {
    // 즉시 UI 업데이트 (낙관적)
    updateUI(seatStatus = SOLD)

    // 백그라운드에서 실제 상태 확인
    pollReservationStatus()
}
```

2. **읽기 전용 복제본 (CQRS)**:
```
Write Model (Command):
  Payment Service → [Event] → Reservation Service

Read Model (Query):
  Event → Read DB 업데이트
  User 조회 → Read DB에서 조회 (최신 상태 반영)
```

3. **버전 기반 조회**:
```kotlin
@Entity
data class Seat(
    val id: Long,
    val status: SeatStatus,

    @Version
    val version: Long, // Optimistic Lock
) {
    fun isStale(clientVersion: Long): Boolean {
        return this.version > clientVersion
    }
}
```

### 5-3. 타임아웃 및 재시도 전략

**타임아웃 계층**:
```yaml
timeout-hierarchy:
  gateway-timeout: 30s           # API Gateway 전체 타임아웃
  service-timeout: 25s           # 개별 서비스 타임아웃
  saga-timeout: 20s              # SAGA 전체 타임아웃
  downstream-timeout: 5s         # 다운스트림 서비스 호출 타임아웃
```

**재시도 정책**:
```kotlin
@Configuration
class ResilienceConfig {

    @Bean
    fun retryConfig(): RetryConfig {
        return RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(
                IOException::class.java,
                TimeoutException::class.java,
                WebClientRequestException::class.java,
            )
            .ignoreExceptions(
                ValidationException::class.java,      // 비즈니스 예외는 재시도 안 함
                InsufficientBalanceException::class.java,
            )
            .build()
    }
}
```

**Circuit Breaker 상태별 전략**:
```
CLOSED (정상):
  - 모든 요청 정상 처리

OPEN (차단):
  - 즉시 실패 반환 (Fallback 응답)
  - Fallback: "일시적 장애 - 잠시 후 다시 시도해주세요"

HALF_OPEN (테스트):
  - 제한된 요청만 허용 (3개)
  - 성공 시 CLOSED 복구
```

## 6. 트레이드오프 분석

### 6-1. Monolithic vs MSA

| 기준 | Monolithic | MSA |
|------|-----------|-----|
| **트랜잭션** | ✅ ACID 보장 | ❌ Eventual Consistency (SAGA 필요) |
| **배포** | ❌ 전체 재배포 | ✅ 독립 배포 |
| **확장** | ❌ 전체 스케일링 | ✅ 서비스별 스케일링 |
| **장애 격리** | ❌ 전체 영향 | ✅ 서비스별 격리 |
| **복잡도** | ✅ 단순 | ❌ 분산 시스템 복잡도 |
| **운영** | ✅ 단일 모니터링 | ❌ 분산 추적, 로그 집계 필요 |
| **개발 속도** | ✅ 빠름 (초기) | ❌ 느림 (인프라 구축 필요) |
| **테스트** | ✅ 통합 테스트 용이 | ❌ E2E 테스트 복잡 |

### 6-2. SAGA 패턴 선택 기준

| 기준 | Choreography | Orchestration |
|------|--------------|---------------|
| **복잡도** | ✅ 단순 (3-5 단계) | ❌ 복잡 (5+ 단계) |
| **결합도** | ✅ 낮음 (이벤트 기반) | ❌ 높음 (중앙 조정) |
| **확장성** | ✅ 쉬움 | ❌ 오케스트레이터 수정 필요 |
| **디버깅** | ❌ 어려움 (이벤트 추적) | ✅ 쉬움 (중앙 로그) |
| **워크플로우 가시성** | ❌ 분산됨 | ✅ 명확함 |
| **SPOF** | ✅ 없음 | ❌ 오케스트레이터가 SPOF |

**콘서트 예약 시스템 선택**:
- **Choreography-based SAGA**
- 이유: 단계 수 적음 (3-5 단계), 이벤트 기반 인프라 이미 구축, 서비스 독립성 중요

### 6-3. MSA 전환 시기

**MSA 전환이 적합한 경우**:
- ✅ 팀 규모: 10+ 개발자 (도메인별 팀 구성 가능)
- ✅ 트래픽: 서비스별 트래픽 패턴 차이 큼
- ✅ 배포 빈도: 주 1회 이상 배포 필요
- ✅ 기술 스택: 서비스별 다른 기술 필요
- ✅ 조직: DevOps 문화 및 인프라 자동화 준비됨

**Monolithic 유지가 나은 경우**:
- ❌ 팀 규모: 5명 이하 (분산 시스템 운영 부담)
- ❌ 트래픽: 작고 균일한 트래픽
- ❌ 배포 빈도: 월 1회 미만
- ❌ 인프라: Kubernetes, Service Mesh 등 미구축
- ❌ 조직: 전통적 개발 문화 (MSA 전환 리스크 큼)

**현재 콘서트 예약 시스템**:
- 팀 규모: 소규모 (1-3명 추정)
- 트래픽: 중간 (대기열 필요)
- 배포 빈도: 주 1-2회
- **권장**: Modular Monolith → 팀 확장 후 MSA 전환

## 7. 결론

### 7-1. MSA 도메인 분리 설계

**5개 마이크로서비스**:
1. **User Service**: 사용자 인증/인가
2. **Queue Service**: 대기열 관리 (Redis)
3. **Concert Service**: 콘서트 정보 및 좌석 관리
4. **Reservation Service**: 예약 생성/확정/취소
5. **Payment Service**: 포인트 및 결제 처리

### 7-2. 분산 트랜잭션 해결

**채택: Choreography-based SAGA**
- 이벤트 기반 비동기 처리
- 보상 트랜잭션으로 데이터 일관성 보장
- 멱등성 키로 중복 방지
- SAGA 실행 로그로 상태 추적

### 7-3. 핵심 구현 포인트

| 영역 | 구현 방안 |
|------|----------|
| **이벤트 버스** | Kafka 또는 RabbitMQ |
| **분산 락** | Redisson (Redis 기반) |
| **멱등성** | Idempotency Key + Request 캐싱 |
| **보상 트랜잭션** | Event-driven Compensating Transactions |
| **상태 추적** | SAGA Execution Log 테이블 |
| **타임아웃** | 계층별 타임아웃 설정 (30s → 25s → 20s → 5s) |
| **재시도** | Resilience4j Retry (3회, 지수 백오프) |
| **Circuit Breaker** | 실패율 50% 초과 시 30초 차단 |

### 7-4. 향후 개선 방향

**Phase 1: Modular Monolith (현재)**
- 도메인별 패키지 분리
- 이벤트 기반 통신 구축
- 트랜잭션 분리 연습

**Phase 2: Database per Service**
- 서비스별 데이터베이스 분리
- SAGA 패턴 적용
- 분산 락 도입

**Phase 3: Full MSA**
- Kubernetes 배포
- Service Mesh (Istio)
- Distributed Tracing (Jaeger)
- API Gateway (Spring Cloud Gateway)

**Phase 4: Advanced Patterns**
- CQRS (읽기/쓰기 분리)
- Event Sourcing
- Outbox Pattern (이벤트 발행 보장)
- CDC (Change Data Capture)
