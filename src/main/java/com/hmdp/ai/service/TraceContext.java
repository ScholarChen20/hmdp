package com.hmdp.ai.service;

import org.slf4j.MDC;

public final class TraceContext {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    private TraceContext() { }

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isEmpty() ? "-" : traceId;
    }

    public static void set(String traceId) { MDC.put(MDC_KEY, traceId); }
    public static void clear() { MDC.remove(MDC_KEY); }
}
