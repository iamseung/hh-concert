#[과제] Redis 기반의 구조 개선
## [필수] Ranking Design
- 콘서트 예약 시나리오
> (인기도) 빠른 매진 랭킹을 Redis 기반으로 개발하고 설계 및 구현

## [선택] Asynchronous Design
- 콘서트 예약 시나리오
> 대기열 기능에 대해 Redis 기반의 설계를 진행하고 적절하게 동작할 수 있도록 하여 제출
> (대기유저 / 활성유저) Set ? Sorted Set

> Redis가 현업에서 어떠 식으로 구현되고 안전하게 서비스 할 수 있는가? (자동복구) 에 대한 고민
> HAProxy

# Ranking Design
## (인기도) 빠른 매진 랭킹을 Redis 기반으로 개발하고 설계 및 구현

### 설계 요구사항
- **랭킹 기준**: 최근 30분간 판매량 (실시간 인기도 추적)
- **랭킹 단위**: 콘서트별 (Concert 단위)
- **갱신 방식**: 하이브리드 (이벤트 기반 실시간 + 배치 주기적 정리)

### Redis 자료구조 설계
| 자료구조 | Key | Value | 용도 |
|---------|-----|-------|------|
| Sorted Set | `concert:ranking` | `concert_id` (member), `판매량` (score) | 랭킹 관리 |
| List | `concert:{concert_id}:sales` | `timestamp` (판매 시각) | 판매 이벤트 타임스탬프 |
| Hash | `concert:{concert_id}:info` | `name`, `title` 등 | 콘서트 메타정보 |

### 핵심 메트릭 계산
```kotlin
// Sliding Window 기반 판매량 추적
최근 N분간 판매량 = COUNT(판매 이벤트)
WHERE timestamp >= NOW() - N분
```

### 하이브리드 갱신 전략

#### 1. 이벤트 기반 (실시간)
- **트리거**: 예약 CONFIRMED 시점
- **처리**:
  1. 판매 이벤트 기록 (`LPUSH`)
  2. 랭킹 점수 증가 (`ZINCRBY`)
  3. 콘서트 메타정보 저장 (`HSET`)
- **장점**: 즉시 랭킹 반영

#### 2. 배치 기반 (주기적 정리)
- **실행 주기**: 매 1분마다 (`@Scheduled`)
- **처리**:
  1. 오래된 판매 이벤트 제거 (30분 이전)
  2. 정확한 판매량 재계산
  3. 랭킹 점수 동기화 (`ZADD`)
- **장점**: Redis-DB 동기화 + Sliding Window 정리

### 구현 레이어

#### Domain Layer
- `RankingModel`: 랭킹 정보 도메인 모델
- `RankingRepository`: 랭킹 저장소 인터페이스
- `RankingService`: 랭킹 비즈니스 로직

#### Infrastructure Layer
- `RedisRankingRepository`: Redis 기반 랭킹 저장소 구현
  - Sorted Set Operations (ZADD, ZREVRANGE, ZINCRBY)
  - List Operations (LPUSH, LTRIM, LRANGE)
  - Hash Operations (HSET, HGET)
- `RankingEventListener`: 예약 확정 이벤트 수신 및 랭킹 업데이트
- `RankingScheduler`: 주기적 랭킹 재계산 배치

#### Application Layer
- `GetRankingUseCase`: 랭킹 조회 UseCase
- `GetRankingCommand/Result`: 요청/응답 DTO

#### Event System
- `ReservationConfirmedEvent`: 예약 확정 이벤트
- 비동기 처리 (`@Async`, `@TransactionalEventListener`)

### API Endpoint
```
GET /api/v1/rankings?limit=10
```

### 성능 최적화
- **O(log N) 랭킹 조회**: Redis Sorted Set 활용
- **메모리 효율성**: List 최대 크기 제한 (1000개)
- **비동기 처리**: 이벤트 핸들러 비동기 실행
- **트랜잭션 후 실행**: `@TransactionalEventListener(AFTER_COMMIT)`

### 장점
- ✅ 실시간성: 예약 즉시 랭킹 반영
- ✅ 정확성: 배치로 DB와 주기적 동기화
- ✅ 확장성: Redis Sorted Set의 O(log N) 성능
- ✅ 인기도 반영: 최근 30분 데이터로 현재 트렌드 추적
