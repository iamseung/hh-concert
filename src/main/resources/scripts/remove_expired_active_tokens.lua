-- Redis Lua Script: Remove Expired Active Tokens
-- ACTIVE Queue에서 만료된 토큰을 원자적으로 제거
--
-- KEYS[1]: ACTIVE Queue Key (ZSET)
-- ARGV[1]: 현재 시간 (score)
--
-- Returns: 제거된 사용자 ID 목록 (JSON array 형태의 문자열)

local activeKey = KEYS[1]
local now = tonumber(ARGV[1])

-- 1. 만료된 사용자 조회 (score가 now보다 작은 것들)
local expiredUserIds = redis.call('ZRANGEBYSCORE', activeKey, 0, now)

if #expiredUserIds == 0 then
    return '[]'
end

-- 2. 원자적으로 제거
for i, userId in ipairs(expiredUserIds) do
    -- 2-1. ACTIVE Queue에서 제거
    redis.call('ZREM', activeKey, userId)

    -- 2-2. Token Entity Hash에서 token 값 조회
    local tokenKey = 'queue:token:' .. userId
    local token = redis.call('HGET', tokenKey, 'token')

    -- 2-3. Token → UserId 매핑 삭제 (메모리 누수 방지)
    if token then
        redis.call('DEL', 'queue:token_to_userid:' .. token)
    end

    -- 2-4. Token Entity Hash 삭제
    redis.call('DEL', tokenKey)
end

-- 3. 제거된 사용자 ID 목록 반환 (JSON array 형태)
return cjson.encode(expiredUserIds)
