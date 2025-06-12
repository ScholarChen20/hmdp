-- 比较线程标示与锁中标示是否一致
if(redis.call("GET", KEYS[1]) == ARGV[1]) then
    -- 释放锁
  return redis.call("DEL", KEYS[1])
end
return 0