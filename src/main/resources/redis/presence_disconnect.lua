-- KEYS[1] session zset, ARGV: member, now
-- returns remaining sessions, or -1 if member was already gone
local removed = redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
if removed == 0 then
    return -1
end
return redis.call('ZCARD', KEYS[1])
