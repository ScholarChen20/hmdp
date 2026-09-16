package com.hmdp.ai.service;

public final class AiRequestContext {
    private static final ThreadLocal<Boolean> APPOINTMENT_CONFIRMATION = new ThreadLocal<>();

    private AiRequestContext() {
    }

    public static void setAppointmentConfirmation(boolean confirmed) {
        APPOINTMENT_CONFIRMATION.set(confirmed);
    }

    public static boolean isAppointmentConfirmed() {
        return Boolean.TRUE.equals(APPOINTMENT_CONFIRMATION.get());
    }

    public static void clear() {
        APPOINTMENT_CONFIRMATION.remove();
    }
}
