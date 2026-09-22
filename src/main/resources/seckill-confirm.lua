local reservationKey = KEYS[1]
local reservationIndexKey = KEYS[2]
local orderId = ARGV[1]
local status = redis.call("hget", reservationKey, "status")

if not status then
    return 0
end
if status == "CONFIRMED" then
    return 1
end
if status == "COMPENSATED" then
    return 0
end

redis.call("hset", reservationKey, "status", "CONFIRMED", "confirmedAt", redis.call("time")[1])
redis.call("zrem", reservationIndexKey, orderId)
return 1
