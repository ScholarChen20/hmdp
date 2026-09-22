package com.hmdp.ai.service;

public final class AiRequestContext {
    private static final ThreadLocal<Boolean> APPOINTMENT_CONFIRMATION = new ThreadLocal<>();
    private static final ThreadLocal<String> FALLBACK_ERROR_TYPE = new ThreadLocal<>();

    private AiRequestContext() {
    }

    public static void setAppointmentConfirmation(boolean confirmed) {
        APPOINTMENT_CONFIRMATION.set(confirmed);
    }

    public static boolean isAppointmentConfirmed() {
        return Boolean.TRUE.equals(APPOINTMENT_CONFIRMATION.get());
    }

    public static void markFallback(String errorType) {
        FALLBACK_ERROR_TYPE.set(errorType);
    }

    public static boolean isFallback() {
        return FALLBACK_ERROR_TYPE.get() != null;
    }

    public static String getFallbackErrorType() {
        return FALLBACK_ERROR_TYPE.get();
    }

    public static void clearAppointmentConfirmation() {
        APPOINTMENT_CONFIRMATION.remove();
    }

    public static void clear() {
        APPOINTMENT_CONFIRMATION.remove();
        FALLBACK_ERROR_TYPE.remove();
    }
}
