package com.hmdp.ai.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiMetrics {
    private final Counter requests;
    private final Counter failures;
    private final Counter rateLimited;
    private final Counter safetyRejected;
    private final Counter faqFallback;

    @Autowired
    public AiMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider.getIfAvailable());
    }

    public AiMetrics(MeterRegistry registry) {
        requests = counter(registry, "hmdp_ai_requests_total");
        failures = counter(registry, "hmdp_ai_failures_total");
        rateLimited = counter(registry, "hmdp_ai_rate_limited_total");
        safetyRejected = counter(registry, "hmdp_ai_safety_rejected_total");
        faqFallback = counter(registry, "hmdp_ai_faq_fallback_total");
    }

    public void request() { requests.increment(); }
    public void failure() { failures.increment(); }
    public void rateLimited() { rateLimited.increment(); }
    public void safetyRejected() { safetyRejected.increment(); }
    public void faqFallback() { faqFallback.increment(); }

    private Counter counter(MeterRegistry registry, String name) {
        return registry == null ? Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Counter.builder(name).register(registry);
    }
}
