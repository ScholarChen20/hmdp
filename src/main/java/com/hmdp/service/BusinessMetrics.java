package com.hmdp.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
    private final Counter seckillRequests;
    private final Counter seckillRejected;
    private final Counter seckillPublished;
    private final Counter seckillConsumed;
    private final Counter seckillFailures;

    @Autowired
    public BusinessMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider.getIfAvailable());
    }

    public BusinessMetrics(MeterRegistry registry) {
        seckillRequests = counter(registry, "hmdp_seckill_requests_total");
        seckillRejected = counter(registry, "hmdp_seckill_rejected_total");
        seckillPublished = counter(registry, "hmdp_seckill_messages_published_total");
        seckillConsumed = counter(registry, "hmdp_seckill_messages_consumed_total");
        seckillFailures = counter(registry, "hmdp_seckill_failures_total");
    }

    public void seckillRequest() { seckillRequests.increment(); }
    public void seckillRejected() { seckillRejected.increment(); }
    public void seckillPublished() { seckillPublished.increment(); }
    public void seckillConsumed() { seckillConsumed.increment(); }
    public void seckillFailure() { seckillFailures.increment(); }

    private Counter counter(MeterRegistry registry, String name) {
        return registry == null
                ? Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Counter.builder(name).register(registry);
    }
}
