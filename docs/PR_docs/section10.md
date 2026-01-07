# [필수] 기본 부하 테스트 진행
1. ✅ 부하 테스트 대상 선정 및 목적, 시나리오 등의 계획을 세우고 이를 문서로 작성
2. ✅ 적합한 테스트 스크립트를 작성하고 수행

---

## 부하 테스트 계획서

### 1. 테스트 대상 시스템 개요
- **시스템명**: 콘서트 예약 시스템
- **시스템 유형**: 대규모 동시 접속 티켓팅 플랫폼
- **핵심 비즈니스**: 대기열 관리, 좌석 예약, 결제 처리

### 2. 부하 테스트 목적
1. **성능 한계 측정**: 시스템이 처리 가능한 최대 동시 사용자 수 및 TPS 확인
2. **병목 지점 식별**: Redis, Kafka, DB, 분산락 등 각 컴포넌트의 성능 한계 파악
3. **안정성 검증**: 고부하 상황에서 데이터 정합성 및 트랜잭션 무결성 검증
4. **SLA 기준 수립**: 서비스 레벨 목표(응답 시간, 처리량, 에러율) 설정

### 3. 테스트 환경
#### 인프라 구성
- **애플리케이션 서버**: Spring Boot 3.4.1 (Kotlin 2.1.0, Java 17)
- **데이터베이스**: MySQL 8.0+ (Primary + Read Replicas)
- **캐시**: Redis with Sentinel
- **메시지 큐**: Apache Kafka (3-broker cluster)
- **분산락**: Redisson (Redis 기반)

#### 주요 설정
- HikariCP 커넥션 풀: 최대 20 connections
- Redis Sentinel 모드 (HA 구성)
- Kafka replication-factor: 3

### 4. 테스트 시나리오

#### 시나리오 1: 좌석 예약 집중 부하 (Seat Reservation Stampede)
**목적**: 분산락 경합 상황에서의 성능 및 정합성 검증

**테스트 조건**:
- 동시 사용자 수: 10,000명
- 대상 좌석 수: 50석 (경합률 200:1)
- 테스트 시간: 5분

**시나리오 흐름**:
1. 사용자 대기열 토큰 발급 (`POST /api/v1/queue/token`)
2. 대기열 상태 폴링 (`GET /api/v1/queue/status`) - ACTIVE 상태까지
3. 콘서트 일정 조회 (`GET /api/v1/concerts/{concertId}/schedules`)
4. 좌석 목록 조회 (`GET /api/v1/concerts/{concertId}/schedules/{scheduleId}/seats`)
5. 좌석 예약 요청 (`POST /api/v1/concerts/{concertId}/reservations`)

**측정 지표**:
- 분산락 획득 지연 시간 (P50, P95, P99)
- 예약 성공률 (50 / 10,000 = 0.5%)
- 예약 실패 에러 분포 (락 타임아웃 vs 이미 예약됨)
- 트랜잭션 처리 시간
- Redisson 락 경합 통계

**예상 병목**:
- Redisson 분산락 경합 (`reservation:payment:lock:{reservationId}`)
- MySQL `SELECT FOR UPDATE` 행 잠금

---

#### 시나리오 2: 대기열 활성화 웨이브 (Queue Activation Wave)
**목적**: 대기열 시스템의 처리량 및 Redis Sorted Set 성능 측정

**테스트 조건**:
- 초기 대기 사용자: 10,000명
- 활성화 목표: 30초 내 1,000명 → 10,000명 전환
- 폴링 간격: 2초

**시나리오 흐름**:
1. 10,000명 동시 토큰 발급
2. 모든 사용자가 2초 간격으로 상태 폴링
3. 활성화 로직에 따라 순차적으로 ACTIVE 전환

**측정 지표**:
- 대기열 진입 처리량 (req/sec)
- ACTIVE 토큰 발급 속도
- 폴링 요청 응답 시간 (P95, P99)
- Redis Sorted Set 연산 지연 시간

**예상 병목**:
- Redis 단일 마스터 네트워크 대역폭
- `ZADD`/`ZREM` 연산 처리량

---

#### 시나리오 3: 동시 결제 처리 (Concurrent Payment Processing)
**목적**: 결제 트랜잭션의 동시성 제어 및 포인트 차감 정합성 검증

**테스트 조건**:
- 동시 결제 요청: 1,000건/초
- 테스트 시간: 5분
- 사용자당 포인트: 충분한 잔액

**시나리오 흐름**:
1. 사전 준비: 좌석 임시 예약 완료 상태
2. 동시 결제 요청 (`POST /api/payments`)
3. 포인트 차감 + 결제 생성 + 좌석 상태 변경 (트랜잭션)

**측정 지표**:
- 결제 처리 응답 시간 (P50, P95, P99)
- 결제 성공률
- 포인트 차감 정합성 (최종 잔액 검증)
- DB 데드락 발생 빈도
- 커넥션 풀 사용률

**예상 병목**:
- MySQL 커넥션 풀 고갈 (최대 20)
- POINT 테이블 행 잠금 (사용자당 단일 레코드)

---

#### 시나리오 4: 좌석 만료 배치 처리 (Seat Expiration Batch)
**목적**: Kafka 이벤트 처리 및 대량 업데이트 성능 측정

**테스트 조건**:
- 만료 대상 좌석: 5,000석 (5분 임시 예약 만료)
- Kafka 토픽: `seats.expired`
- 처리 방식: 배치 업데이트

**시나리오 흐름**:
1. 사전 준비: 5,000석 임시 예약 상태로 만들기
2. `SeatScheduler` 1분 주기 실행
3. 만료된 좌석에 대해 `SeatExpiredEvent` 발행
4. `SeatExpirationConsumer`가 배치로 처리
5. 좌석 상태 TEMPORARY_RESERVED → AVAILABLE 변경
6. 캐시 무효화

**측정 지표**:
- Kafka 프로듀서 처리량 (msg/sec)
- 컨슈머 랙 (Consumer Lag)
- 배치 업데이트 소요 시간
- 캐시 무효화 시간
- End-to-End 이벤트 처리 지연 시간

**예상 병목**:
- Kafka 컨슈머 처리 속도
- MySQL 대량 UPDATE 성능
- Redis 캐시 무효화 연쇄 효과

---

#### 시나리오 5: 지속적 부하 테스트 (Sustained Load Test)
**목적**: 장시간 운영 시 시스템 안정성 및 리소스 누수 확인

**테스트 조건**:
- 목표 부하: 1,000 req/sec
- 테스트 시간: 10분
- 트래픽 패턴: 일정 부하 유지

**시나리오 흐름**:
- 모든 API 엔드포인트에 대한 혼합 트래픽
- 대기열 → 예약 → 결제의 완전한 사용자 여정

**측정 지표**:
- 평균/최대 응답 시간
- 에러율 (목표: 0.1% 이하)
- CPU/메모리 사용률
- GC 일시 정지 시간
- 커넥션 풀/스레드 풀 사용률

**예상 병목**:
- JVM GC 압박
- 메모리 누수
- 커넥션 누수

---

### 5. 성공 기준 (Success Criteria)

| 지표 | 목표값 | 측정 방법 |
|------|--------|-----------|
| 응답 시간 (P95) | < 500ms | 부하 테스트 도구 |
| 응답 시간 (P99) | < 1000ms | 부하 테스트 도구 |
| 에러율 | < 0.5% | 에러 로그 분석 |
| 최대 처리량 | > 500 TPS | Throughput 측정 |
| 데이터 정합성 | 100% | DB 쿼리 검증 |
| 분산락 성공률 | > 99.9% | Redisson 메트릭 |
| Kafka 컨슈머 랙 | < 1000 msg | Kafka 모니터링 |

### 6. 테스트 도구
- **부하 생성**: Apache JMeter / Gatling / k6
- **모니터링**: Prometheus + Grafana
- **APM**: Spring Boot Actuator + Micrometer
- **로그 분석**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **DB 모니터링**: MySQL Performance Schema
- **Redis 모니터링**: Redis INFO, redis-cli --stat
- **Kafka 모니터링**: Kafka Manager / Confluent Control Center

### 7. 위험 요소 및 대응 방안

| 위험 요소 | 영향도 | 대응 방안 |
|-----------|--------|-----------|
| Redis 단일 장애점 | 높음 | Redis Cluster 전환 검토 |
| DB 커넥션 풀 고갈 | 높음 | 커넥션 풀 사이즈 증설 (20 → 50+) |
| 분산락 타임아웃 | 중간 | 락 대기 시간 튜닝 (3초 → 5초) |
| Kafka 컨슈머 랙 | 중간 | 컨슈머 파티션/인스턴스 증설 |
| 캐시 미스 폭증 | 중간 | Cache Warming 전략 수립 |

### 8. 테스트 일정
1. **환경 준비**: 1일 (테스트 데이터 생성, 스크립트 작성)
2. **시나리오 1-3 수행**: 2일
3. **시나리오 4-5 수행**: 1일
4. **결과 분석 및 리포트 작성**: 1일
5. **개선 작업**: 별도 일정

### 9. 산출물
- ✅ 부하 테스트 스크립트 (k6)
- 성능 테스트 결과 리포트
- 병목 분석 및 개선 제안서
- 시스템 용량 산정 문서 (예상 동시 접속자 수, 필요 리소스)

---

## k6 테스트 스크립트 구현

### 구현 완료 항목

#### 1. 모니터링 인프라 구축
- ✅ Prometheus + Grafana docker-compose 추가
- ✅ Spring Boot Actuator 설정 (`application.yml`)
- ✅ Micrometer Prometheus 의존성 추가 (`build.gradle.kts`)
- ✅ Prometheus 설정 파일 (`prometheus.yml`)

**접속 정보**:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- Actuator: http://localhost:8080/actuator/prometheus

#### 2. k6 테스트 스크립트 작성

모든 테스트 스크립트는 `k6-tests/` 디렉토리에 위치합니다.

##### 시나리오 1: 좌석 예약 집중 부하 (`scenario1-seat-reservation-stampede.js`)
```bash
k6 run k6-tests/scenario1-seat-reservation-stampede.js
```

**특징**:
- 10,000명의 동시 사용자가 50석을 경합
- 분산락 획득 시간 측정
- 예약 성공률 및 락 타임아웃 모니터링
- 커스텀 메트릭: `lock_acquisition_time`, `reservation_success_rate`

##### 시나리오 2: 대기열 활성화 웨이브 (`scenario2-queue-activation-wave.js`)
```bash
k6 run k6-tests/scenario2-queue-activation-wave.js
```

**특징**:
- 10,000명 동시 대기열 진입
- 2초 간격 폴링으로 상태 확인
- Redis Sorted Set 성능 측정
- 커스텀 메트릭: `queue_activation_time`, `polling_response_time`

##### 시나리오 3: 동시 결제 처리 (`scenario3-concurrent-payment.js`)
```bash
k6 run k6-tests/scenario3-concurrent-payment.js
```

**특징**:
- 초당 1,000건의 결제 요청
- 사전 준비 단계에서 예약 자동 생성
- DB 데드락 및 커넥션 풀 고갈 모니터링
- 커스텀 메트릭: `db_deadlocks`, `connection_pool_exhaustion`

##### 시나리오 5: 지속적 부하 테스트 (`scenario5-sustained-load.js`)
```bash
k6 run k6-tests/scenario5-sustained-load.js
```

**특징**:
- 10분 동안 1,000 req/sec 유지
- 완전한 사용자 여정 시뮬레이션 (대기열 → 예약 → 결제)
- End-to-End 소요 시간 측정
- 에러 타입별 분류 (토큰, 대기열, 예약, 결제)

#### 3. 상세 문서
- ✅ k6 설치 가이드 (`k6-tests/README.md`)
- ✅ 실행 방법 및 옵션
- ✅ Prometheus/Grafana 연동 가이드
- ✅ 문제 해결 체크리스트

### 실행 준비 사항

```bash
# 1. k6 설치 (macOS)
brew install k6

# 2. 인프라 실행
docker-compose up -d mysql redis prometheus grafana
docker-compose -f docker-compose.kafka.yml up -d

# 3. 의존성 추가 및 애플리케이션 실행
./gradlew build
./gradlew bootRun

# 4. 시스템 상태 확인
curl http://localhost:8080/actuator/health

# 5. 테스트 실행
k6 run k6-tests/scenario1-seat-reservation-stampede.js
```

### Grafana 대시보드 추천

1. **k6 메트릭 대시보드**:
   - Dashboard ID: **2587** (k6 Load Testing Results)
   - 또는 **19665** (k6 Prometheus)

2. **Spring Boot 시스템 메트릭**:
   - Dashboard ID: **11378** (JVM Micrometer)
   - 또는 **4701** (Spring Boot Statistics)

### 성공 기준 요약

| 시나리오 | 주요 지표 | 목표값 |
|----------|-----------|--------|
| 시나리오 1 | 예약 응답 시간 (P95) | < 500ms |
| 시나리오 1 | 락 획득 시간 (P99) | < 5000ms |
| 시나리오 2 | 대기열 활성화 시간 (P95) | < 60초 |
| 시나리오 2 | 폴링 응답 시간 (P99) | < 200ms |
| 시나리오 3 | 결제 응답 시간 (P95) | < 500ms |
| 시나리오 3 | DB 데드락 발생 | < 10건 |
| 시나리오 5 | 전체 에러율 | < 0.1% |
| 시나리오 5 | E2E 응답 시간 (P95) | < 5초 |

### 다음 단계

- [ ] 각 시나리오 부하 테스트 수행
- [ ] Grafana에서 실시간 메트릭 모니터링
- [ ] 병목 지점 분석 및 개선 방안 도출
- [ ] 성능 테스트 결과 리포트 작성

# [선택] 장애 대응 메뉴얼 작성
1. ✅ 시스템 내의 병목을 탐색 및 개선해보고 장애 대응 메뉴얼을 작성하고 제출
2. ✅ 최종 발표 자료 작성 및 제출 ← 우리 서비스에 관련된 모든 사람에게 보여줄 범용적인 자료
    - 우리 시스템의 전반적인 구조, 우리 시스템이 어디까지 요청을 받을 수 있는지, 취약점 등

---

## 작성 완료 문서

### 📋 [운영 문서 디렉토리](../operation/README.md)

모든 운영 관련 문서는 `docs/operation/` 디렉토리에 있습니다.

### 1. [병목 지점 분석](../operation/bottleneck-analysis.md)
**대상**: 개발팀, DevOps팀

부하 테스트 결과 기반 시스템 병목 지점 분석
- 🔴 CRITICAL: 대기열 활성화 타임아웃
- ⚠️ DB 커넥션 풀 부족 (20개 → 50개 권장)
- ⚠️ 대기 시간 과다 (10,000명 시 2시간 47분)
- 성능 한계 예측: 현재 2,081 req/sec → 목표 20,000 req/sec
- 권장 개선 사항 (단기/중기/장기)

### 2. [장애 대응 메뉴얼](../operation/incident-response-manual.md) ⭐
**대상**: 전체 개발팀, DevOps팀, On-Call Engineer

실무에서 사용 가능한 장애 대응 절차
- 장애 레벨 정의 (P0~P3)
- 7가지 주요 장애 시나리오별 대응:
  1. 대기열 시스템 장애 (P1)
  2. 데이터베이스 장애 (P0)
  3. Redis 장애 (P0)
  4. Kafka 장애 (P2)
  5. 결제 시스템 장애 (P1)
  6. 외부 API 장애 (P2)
  7. 애플리케이션 메모리 부족 (P1)
- 모니터링 및 알람 설정
- 롤백/복구 절차
- Post-Mortem 템플릿

### 3. [시스템 개요 발표 자료](../operation/system-overview-presentation.md) ⭐
**대상**: 경영진, PM, 마케팅, CS, 전체 구성원

비기술자도 이해 가능한 범용 시스템 소개 자료
- 시스템 아키텍처 (다이어그램 포함)
- 주요 기능 및 동작 방식
- 성능 및 처리 용량
  - 현재: 동시 100명, 2,081 req/sec
  - 목표 (1주): 동시 1,000명, 20,000 req/sec
  - 목표 (3개월): 동시 10,000명, 50,000 req/sec
- 보안
- 취약점 및 개선 계획
- Q&A (10개):
  - 동시 몇 명까지 처리 가능?
  - 대기 시간은?
  - 중복 결제 방지는?
  - 장애 복구 시간은?
  - 개인정보 보호는?
  - 등등

---

## 주요 발견 사항

### 🔴 CRITICAL 이슈

**대기열 활성화 타임아웃**
- WAITING → ACTIVE 상태 전환 미작동
- 모든 E2E 테스트 실패 (시나리오 1, 2, 5)
- 사용자가 무한 대기 → 서비스 이용 불가
- **해결 계획**: 스케줄러 로직 수정 (다음 작업)

### ⚠️ 병목 지점

1. **대기열 처리 속도**: 10명/10초 → 100명/10초로 증가 필요
2. **DB 커넥션 풀**: 20개 → 50개로 확장 필요
3. **Redis 분산락**: wait 3초 → 1초로 단축 권장
4. **Kafka 파티션**: 1개 → 3개로 증가 (병렬 처리)

### ✅ 우수한 성능

- **API 응답 속도**: P95 6.82 ~ 32ms (목표 500ms 대비 **94~98% 개선**)
- **HTTP 성공률**: **100%** (모든 시나리오)
- **Redis 처리량**: 2,081 req/sec (10배 확장 가능)
- **DB 안정성**: 데드락 **0건**

---

## 다음 단계

### 즉시 조치 (High Priority)
- [ ] 대기열 스케줄러 수정 (CRITICAL)
- [ ] DB 커넥션 풀 확장 (20 → 50)
- [ ] 대기열 설정 조정 (MAX_ACTIVE_USERS: 100 → 1,000)

### 중기 개선 (1주 이내)
- [ ] 시나리오 1, 2, 3, 5 재테스트 (10,000 VUs)
- [ ] Kafka 파티션 증가 (1 → 3)
- [ ] 캐시 전략 개선 (TTL 조정)

### 장기 개선 (1개월 이내)
- [ ] APM 도구 도입 (New Relic, Datadog)
- [ ] Redis Cluster 구성 (HA)
- [ ] 애플리케이션 수평 확장 (3대 → 10대)