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

# Queue System Improvement
## 대기열 시스템 개선 (1순위 개선사항 적용)

### 기존 구현 분석
- **자료구조**: Redis Sorted Set (WAITING, ACTIVE) ✅
- **원자성 보장**: Lua 스크립트 사용 ✅
- **자동화**: 스케줄러 기반 활성화/만료 처리 ✅

### 개선사항

#### 1. 중복 토큰 발급 방지 🔴 (심각도: 높음)
**문제점**: 동일 사용자가 여러 번 요청 시 매번 새 토큰 생성 → 대기열 공정성 파괴

**해결 방법**:
- Redis HASH `putIfAbsent` 사용하여 원자적 토큰 생성
- `findOrCreateTokenAtomic()` 메서드로 중복 생성 방지

**구현**:
```kotlin
fun findOrCreateTokenAtomic(userId: Long): QueueTokenModel {
    // 1. 기존 토큰 확인
    val existingToken = getTokenEntity(userId)
    if (existingToken != null) {
        return existingToken.toModel()
    }

    // 2. 원자적으로 토큰 생성 (putIfAbsent)
    val tokenKey = "queue:token:$userId"
    val saved = stringRedisTemplate.opsForHash<String, String>()
        .putIfAbsent(tokenKey, "userId", userId.toString())

    // 3. 이미 저장되었다면 기존 토큰 반환
    if (saved == false) {
        return getTokenEntity(userId)?.toModel()
            ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    // 4. 나머지 데이터 저장
    saveTokenEntity(entity)
    addToWaitingQueue(userId)

    return newToken
}
```

**효과**:
- ✅ Race condition 방지
- ✅ 대기열 공정성 보장
- ✅ 동일 사용자 중복 진입 차단

#### 2. Token 매핑 메모리 누수 해결 🔴 (심각도: 높음)
**문제점**: `queue:token_to_userid:{token}` 매핑이 영구 보존 → 시간이 지날수록 Redis 메모리 부족

**해결 방법**:
- 만료된 토큰 삭제 시 매핑도 함께 삭제
- Lua 스크립트에 매핑 삭제 로직 추가

**Lua 스크립트 개선**:
```lua
-- remove_expired_active_tokens.lua
for i, userId in ipairs(expiredUserIds) do
    -- ACTIVE Queue에서 제거
    redis.call('ZREM', activeKey, userId)

    -- Token Entity에서 token 조회
    local tokenKey = 'queue:token:' .. userId
    local token = redis.call('HGET', tokenKey, 'token')

    -- Token → UserId 매핑 삭제 (메모리 누수 방지) ⭐ 추가
    if token then
        redis.call('DEL', 'queue:token_to_userid:' .. token)
    end

    -- Token Entity Hash 삭제
    redis.call('DEL', tokenKey)
end
```

**Application 레이어 개선**:
```kotlin
fun expireQueueToken(queueTokenModel: QueueTokenModel): QueueTokenModel {
    queueTokenModel.expire()
    redisQueueRepository.removeFromActiveQueue(queueTokenModel.userId)
    redisQueueRepository.removeTokenMapping(queueTokenModel.token)  // ⭐ 추가
    return redisQueueRepository.update(queueTokenModel)
}

fun removeTokenMapping(token: String) {
    redisTemplate.delete("queue:token_to_userid:$token")
}
```

**효과**:
- ✅ 메모리 사용량 일정 유지
- ✅ 만료된 데이터 자동 정리
- ✅ Redis 안정성 향상

### 개선 전후 비교

| 항목 | 개선 전 | 개선 후 |
|------|---------|---------|
| **중복 토큰** | 동일 유저 여러 토큰 생성 가능 | 원자적 생성으로 1개만 보장 |
| **메모리 누수** | 매핑 데이터 영구 보존 | 만료 시 자동 삭제 |
| **공정성** | 중복 진입으로 순위 왜곡 | 1인 1토큰으로 공정성 보장 |
| **Redis 안정성** | 시간 경과에 따라 메모리 증가 | 일정 메모리 사용량 유지 |

### 향후 개선 계획 (2~3순위)
- N+1 쿼리 최적화 (Redis Pipeline 사용)
- 동적 배치 크기 조정 (트래픽 패턴에 따라 조정)
- EXPIRED 상태 추적 (감사 로그 및 분석용)

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
