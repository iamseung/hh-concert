-- Redis Lua Script: Activate Waiting Users
-- 대기열에서 상위 N명을 ACTIVE로 원자적으로 이동
--
-- KEYS[1]: WAITING Queue Key (ZSET)
-- KEYS[2]: ACTIVE Queue Key (ZSET)
-- ARGV[1]: 활성화할 사용자 수
-- ARGV[2]: 만료 시간 (ACTIVE score)
-- ARGV[3]: 현재 시간 (activatedAt)
-- ARGV[4]: 만료 시간 문자열 (expiresAt)
--
-- Returns: 활성화된 사용자 ID 목록 (JSON array)

local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local count = tonumber(ARGV[1])
local expiryScore = tonumber(ARGV[2])
local activatedAt = ARGV[3]
local expiresAt = ARGV[4]

-- 1. WAITING Queue에서 상위 N명 조회 (score 낮은 순)
local userIds = redis.call('ZRANGE', waitingKey, 0, count - 1)

if #userIds == 0 then
    return '[]'
end

-- 2. 원자적으로 이동 및 업데이트
for i, userId in ipairs(userIds) do
    -- 2-1. WAITING에서 제거
    redis.call('ZREM', waitingKey, userId)

    -- 2-2. ACTIVE에 추가 (만료 시간을 score로)
    redis.call('ZADD', activeKey, expiryScore, userId)

    -- 2-3. Token Entity Hash 업데이트
    local tokenKey = 'queue:token:' .. userId
    redis.call('HSET', tokenKey, 'status', 'ACTIVE')
    redis.call('HSET', tokenKey, 'activatedAt', activatedAt)
    redis.call('HSET', tokenKey, 'expiresAt', expiresAt)
    redis.call('HSET', tokenKey, 'updatedAt', activatedAt)
end

-- 3. 활성화된 사용자 ID 목록을 JSON으로 반환
return cjson.encode(userIds)
