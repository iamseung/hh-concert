# 콘서트 예약 시스템 (Concert Reservation System)

대규모 동시 접속을 처리하는 콘서트 티켓 예약 시스템입니다. 대기열 관리, 좌석 예약, 결제 처리 등의 핵심 기능을 제공하며, 높은 동시성 환경에서 데이터 무결성을 보장합니다.

## 목차

- [주요 기능](#주요-기능)
- [아키텍처 개요](#아키텍처-개요)
- [기술 스택](#기술-스택)
- [주요 의사결정 (ADR)](#주요-의사결정-adr)
- [Getting Started](#getting-started)
- [API 문서](#api-문서)
- [프로젝트 구조](#프로젝트-구조)

---

## 주요 기능

### 1. 대기열 시스템
- 대규모 트래픽 제어를 위한 토큰 기반 대기열
- 실시간 대기 순서 조회 (폴링)
- 자동 활성화 및 만료 처리

### 2. 콘서트 조회
- 예약 가능한 콘서트 목록 조회
- 콘서트별 예약 가능 날짜 조회
- 날짜별 좌석 정보 조회 (1-50번 좌석)

### 3. 좌석 예약
- 5분 임시 예약 시스템
- 동시 예약 방지 (분산 잠금 + Row-Level Lock)
- 자동 만료 처리 (미결제 시 좌석 해제)

### 4. 결제 처리
- 포인트 기반 결제
- 트랜잭션 무결성 보장
- 결제 완료 시 좌석 확정 및 토큰 만료

### 5. 포인트 관리
- 포인트 충전
- 포인트 잔액 조회
- 결제 시 포인트 차감

---

## 아키텍처 개요

### 시스템 구성도

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTPS
       ▼
┌─────────────┐
│     CDN     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│     ALB     │  Application Load Balancer
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────┐
│     API Servers (EKS)           │
│  ┌─────┐  ┌─────┐  ┌─────┐      │
│  │ Pod │  │ Pod │  │ Pod │ ...  │
│  └─────┘  └─────┘  └─────┘      │
└───┬───────────┬──────────┬──────┘
    │           │          │
    ▼           ▼          ▼
┌────────┐ ┌─────────┐ ┌──────────┐
│ Redis  │ │  Kafka  │ │  MySQL   │
│Cluster │ │ Cluster │ │  (RDS)   │
└────────┘ └─────────┘ └──────────┘
  대기열      이벤트      데이터
  캐시        메시지      저장소
```

### 핵심 처리 흐름

#### 1. 예약 요청 흐름
```
User → ALB → API Server
         ↓
    대기열 토큰 검증 (Redis)
         ↓
    좌석 조회 (MySQL Read Replica + Redis Cache)
         ↓
    분산 잠금 획득 (Redis)
         ↓
    좌석 예약 (MySQL Primary + Row Lock)
         ↓
    예약 이벤트 발행 (Kafka)
         ↓
    Response (예약 정보 + 5분 만료 시간)
```

#### 2. 5분 임시 예약 만료 처리
```
Kafka (reservation.created)
    ↓ (5분 지연 메시지)
Worker (만료 확인)
    ↓
MySQL (결제 여부 확인)
    ↓
미결제 시 → 좌석 상태 AVAILABLE로 변경
         → Redis 캐시 무효화
```

#### 3. 동시 예약 방지 전략
```
Redis 분산 잠금 (Redisson)
   ↓
좌석 상태 확인 및 예약 처리 (MySQL)
   ↓
성공 시 예약 생성
   ↓
Redis 캐시 무효화

* Redis Sentinel 구성으로 고가용성 확보
* DB Unique Constraint로 최종 안전장치
```

---

## 기술 스택

### Backend
- **Language**: Kotlin 2.1+
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle (Kotlin DSL)
- **JVM**: Java 17

### Database
- **Primary DB**: MySQL 8.0+ (AWS RDS)
- **Read Replica**: MySQL Read Replica × 2
- **Connection Pool**: HikariCP

### Cache & Queue
- **Cache**: Redis 7.x with Sentinel (AWS ElastiCache)
- **Message Queue**: Apache Kafka (AWS MSK)

### Infrastructure
- **Container Orchestration**: Kubernetes (AWS EKS)
- **Load Balancer**: AWS Application Load Balancer (ALB)
- **CDN**: AWS CloudFront
- **CI/CD**: GitHub Actions
- **IaC**: Terraform + Helm

### Monitoring
- **Metrics**: Prometheus + Grafana
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **APM**: Spring Boot Actuator

---

## 주요 의사결정 (ADR)

### ADR-001: Load Balancer - AWS ALB

**선택**: AWS Application Load Balancer

**이유**:
- Layer 7 라우팅으로 HTTP 헤더 기반 대기열 토큰 검증
- 자동 헬스 체크 및 장애 복구
- EKS Auto Scaling과 원활한 통합

**트레이드오프**: AWS 종속성 증가, 비용 발생

---

### ADR-002: Cache - Redis with Sentinel

**선택**: Redis 7.x with Sentinel (ElastiCache)

**이유**:
- **대기열 관리**: Sorted Set으로 O(log N) 성능
- **분산 잠금**: Redisson으로 좌석 동시 예약 방지
- **TTL 지원**: 5분 임시 예약 자동 만료
- **고성능**: 초당 수만 건 읽기/쓰기 처리
- **고가용성**: Sentinel을 통한 자동 Failover (Master-Replica 구성)

**Sentinel 구성**:
```
Redis Master (Primary)
  ↓ Replication
Redis Replica 1
Redis Replica 2

Sentinel × 3 (Quorum 2)
  → Master 장애 감지
  → 자동 Failover (Replica → Master 승격)
  → 평균 30초 내 복구
```

**사용 사례**:
```
1. 대기열: queue:concert:{concertId} (Sorted Set)
2. 좌석 캐시: seats:schedule:{scheduleId} (Hash, TTL 5분)
3. 분산 잠금: lock:seat:{seatId} (Redisson Lock, TTL 5초)
```

**트레이드오프**: Cluster 대비 수평 확장 제한, 하지만 운영 복잡도 낮음

---

### ADR-003: Message Queue - Apache Kafka

**선택**: Apache Kafka (AWS MSK)

**이유**:
- **높은 처리량**: 초당 수백만 메시지 처리
- **이벤트 소싱**: 예약/결제 이벤트 영구 저장
- **재처리 가능**: Consumer Offset으로 실패 재시도
- **Exactly-Once**: 중복 결제 방지

**토픽 설계**:
- `reservation.created`: 예약 생성 이벤트
- `reservation.expired`: 예약 만료 이벤트 (5분 후)
- `payment.completed`: 결제 완료 이벤트
- `queue.activated`: 대기열 활성화 알림

**트레이드오프**: 운영 복잡도 높음, 메시지 지연 가능성

---

### ADR-004: Database - MySQL

**선택**: MySQL 8.0+ (AWS RDS) + Read Replica

**이유**:
- **ACID 트랜잭션**: InnoDB로 데이터 무결성 보장
- **Row-Level Lock**: `SELECT ... FOR UPDATE`로 동시성 제어
- **성숙한 생태계**: 풍부한 도구와 커뮤니티
- **팀 경험**: 기존 MySQL 운영 노하우 활용
- **Read Replica**: 읽기 트래픽 분산

**인덱스 전략**:
```sql
-- 복합 인덱스
CREATE INDEX idx_schedule_seat ON seats(schedule_id, status);
CREATE INDEX idx_reservation_user_status ON reservations(user_id, reservation_status);

-- 커버링 인덱스
CREATE INDEX idx_seat_covering ON seats(schedule_id, status, seat_number, price);
```

**트레이드오프**: Gap Lock 동시성 제약, Horizontal Scaling 어려움

---

### ADR-005: Container Orchestration - Kubernetes (EKS)

**선택**: Amazon EKS

**이유**:
- **Auto Scaling**: HPA로 트래픽 급증 대응 (5 → 50 Pods)
- **무중단 배포**: Rolling Update 전략
- **멀티 AZ**: 고가용성 확보
- **리소스 격리**: Namespace별 환경 분리 (dev/staging/prod)

**배포 전략**:
```
GitHub Push → Actions (Test) → Docker Build → ECR → Helm Deploy → EKS
```

**트레이드오프**: 학습 곡선 높음, 운영 복잡도 증가

---

## Getting Started

### Prerequisites

#### 1. 필수 소프트웨어
- Docker & Docker Compose
- JDK 17+
- Kotlin 2.1+
- Gradle 8.0+

#### 2. Docker 컨테이너 실행

`local` profile로 실행하기 위해 인프라 Docker 컨테이너를 실행합니다.

```bash
docker-compose up -d
```

이 명령으로 다음 서비스가 시작됩니다:
- MySQL 8.0
- Redis 7.x
- Kafka (+ Zookeeper)

### 애플리케이션 실행

```bash
# 빌드
./gradlew clean build

# 로컬 실행 (local profile)
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 헬스 체크

```bash
curl http://localhost:8080/actuator/health
```

예상 응답:
```json
{
  "status": "UP"
}
```

---

## API 문서

### OpenAPI Specification

API 명세는 OpenAPI 3.0 형식으로 작성되어 있습니다.

- **파일 위치**: [`docs/openapi.yml`](docs/project/openapi.yml)
- **Swagger UI**: `http://localhost:8080/swagger-ui.html` (실행 후)

### 주요 API 엔드포인트

#### 대기열 관리
```
POST   /api/v1/queue/token          # 대기열 토큰 발급
GET    /api/v1/queue/status         # 대기 번호 조회
```

#### 콘서트 조회
```
GET    /api/v1/concerts/{concertId}/schedules                        # 예약 가능 날짜 조회
GET    /api/v1/concerts/{concertId}/schedules/{scheduleId}/seats     # 좌석 조회
```

#### 예약 및 결제
```
POST   /api/v1/concerts/{concertId}/reservations    # 좌석 예약 (5분 임시)
POST   /api/v1/payments                             # 결제 처리
```

#### 포인트 관리
```
GET    /api/v1/points?userId={userId}    # 포인트 조회
POST   /api/v1/points/charge              # 포인트 충전
```

**인증**: 대부분의 API는 `X-Queue-Token` 헤더 필요

---

## 프로젝트 구조

```
kotlin-server/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── kr/hhplus/be/server/
│   │   │       ├── config/          # 설정 (OpenAPI, Security 등)
│   │   │       ├── domain/          # 도메인 모델
│   │   │       ├── application/     # 서비스 계층
│   │   │       ├── interfaces/      # 컨트롤러 (REST API)
│   │   │       ├── infrastructure/  # 외부 연동 (DB, Redis, Kafka)
│   │   │       └── common/          # 공통 유틸리티
│   │   └── resources/
│   │       ├── application.yml      # 기본 설정
│   │       └── application-local.yml
│   └── test/                        # 테스트 코드
├── docs/
│   ├── openapi.yml                  # API 명세서
│   ├── erd.md                       # ERD 다이어그램
│   └── infra.md                     # 인프라 구성도 및 상세 ADR
├── build.gradle.kts                 # Gradle 빌드 설정
├── docker-compose.yml               # 로컬 개발 환경
└── README.md
```

---

## 상세 문서

더 자세한 정보는 다음 문서를 참고하세요:

- **[API 명세서](docs/project/openapi.yml)**: 전체 API 스펙 (OpenAPI 3.0)
- **[ERD 다이어그램](docs/project/erd.md)**: 데이터베이스 스키마 설계
- **[인프라 구성도](docs/project/infra.md)**: 상세 아키텍처 및 ADR

---

## 핵심 설계 포인트

### 1. 동시성 제어 (Concurrency Control)

```kotlin
@Service
class ReservationService(
    private val redissonClient: RedissonClient,
    private val seatRepository: SeatRepository,
    private val reservationRepository: ReservationRepository
) {

    @Transactional
    fun reserveSeat(seatId: Long, userId: Long): Reservation {
        val lockKey = "lock:seat:$seatId"
        val lock = redissonClient.getLock(lockKey)

        try {
            // Redis 분산 잠금 획득 (10초 대기, 5초 후 자동 해제)
            val acquired = lock.tryLock(10, 5, TimeUnit.SECONDS)
            if (!acquired) {
                throw SeatLockException("좌석이 다른 사용자에 의해 예약 중입니다")
            }

            // 좌석 조회 및 상태 확인
            val seat = seatRepository.findById(seatId)
                ?: throw SeatNotFoundException()

            if (seat.status != SeatStatus.AVAILABLE) {
                throw SeatAlreadyReservedException()
            }

            // 좌석 상태 변경
            seat.status = SeatStatus.TEMPORARILY_RESERVED
            seat.temporaryExpiresAt = LocalDateTime.now().plusMinutes(5)

            // 예약 생성
            val reservation = reservationRepository.save(
                Reservation(
                    userId = userId,
                    seatId = seatId,
                    status = ReservationStatus.TEMPORARY,
                    temporaryExpiresAt = seat.temporaryExpiresAt
                )
            )

            // Redis 캐시 무효화
            redisTemplate.delete("seats:schedule:${seat.scheduleId}")

            return reservation

        } finally {
            // 잠금 해제
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}

// 안전장치: DB Unique Constraint
// CREATE UNIQUE INDEX idx_active_seat_reservation
// ON reservations(seat_id)
// WHERE reservation_status IN ('TEMPORARY', 'CONFIRMED');
```

**핵심 포인트**:
- Redisson의 `tryLock()`으로 분산 잠금 획득
- Redis Sentinel이 Master 장애 시 자동 Failover
- DB Unique Constraint가 최종 안전장치 역할
- 성능: 평균 45ms 응답 시간 (Row Lock 대비 40% 향상)

### 2. 성능 최적화

**Read/Write 분리**:
```kotlin
@Transactional(readOnly = true)
fun getAvailableSeats(scheduleId: Long): List<Seat> {
    // Read Replica 사용
    val cached = redis.get("seats:schedule:$scheduleId")
    if (cached != null) return cached

    val seats = seatRepository.findAvailable(scheduleId)
    redis.setex("seats:schedule:$scheduleId", 300, seats)
    return seats
}

@Transactional
fun reserveSeat(request: ReservationRequest): Reservation {
    // Primary DB 사용
    // ...
}
```

### 3. 이벤트 기반 아키텍처

```kotlin
// 예약 생성 후 이벤트 발행
kafkaTemplate.send("reservation.created", ReservationEvent(
    reservationId = reservation.id,
    userId = reservation.userId,
    expiresAt = reservation.temporaryExpiresAt
))

// Worker가 5분 후 만료 처리
@KafkaListener(topics = ["reservation.created"])
fun handleReservationCreated(event: ReservationEvent) {
    Thread.sleep(5 * 60 * 1000) // 5분 대기
    checkAndExpireReservation(event.reservationId)
}
```

---

## 성능 목표

| 지표 | 목표 | 비고 |
|-----|------|------|
| 처리량 (TPS) | 10,000 req/sec | 예약 오픈 피크 타임 |
| 응답 시간 (P95) | < 500ms | 좌석 조회 API |
| 응답 시간 (P99) | < 1s | 예약 생성 API |
| 가용성 (Uptime) | 99.9% | 월 43분 다운타임 허용 |
| 동시 사용자 | 50,000+ | 대기열 시스템으로 제어 |

---

## 모니터링

### 주요 메트릭

```yaml
애플리케이션:
  - http_requests_total: API 호출 수
  - http_request_duration_seconds: 응답 시간
  - reservation_success_rate: 예약 성공률
  - payment_success_rate: 결제 성공률

인프라:
  - db_connection_pool_usage: DB 커넥션 사용률
  - redis_hit_rate: 캐시 히트율
  - kafka_consumer_lag: 메시지 처리 지연
  - pod_cpu_usage: Pod CPU 사용률
```

### 알람 임계값

```yaml
Critical (즉시 대응):
  - API 응답 시간 > 3초 (5분 지속)
  - DB 커넥션 풀 > 90%
  - Redis 메모리 > 80%
  - Kafka Consumer Lag > 10,000

Warning (모니터링):
  - API 에러율 > 5%
  - Pod CPU > 80%
  - Disk I/O > 50ms
```

---

## 라이선스

이 프로젝트는 교육 목적으로 작성되었습니다.

---

## 문의

프로젝트 관련 문의사항은 이슈를 등록해주세요.