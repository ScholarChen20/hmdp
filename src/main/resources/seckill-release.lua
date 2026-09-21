local reservationKey = KEYS[1]
local reservationIndexKey = KEYS[2]
local stockKey = KEYS[3]
local orderKey = KEYS[4]
local orderId = ARGV[1]
local userId = ARGV[2]
local status = redis.call("hget", reservationKey, "status")

if not status then
    return 0
end
if status == "CONFIRMED" or status == "COMPENSATED" then
    return 0
end

redis.call("incrby", stockKey, 1)
redis.call("srem", orderKey, userId)
redis.call("hset", reservationKey, "status", "COMPENSATED", "compensatedAt", redis.call("time")[1])
redis.call("zrem", reservationIndexKey, orderId)
return 1
