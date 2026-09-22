package com.hmdp.appointment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.appointment.dto.CreateHomeServiceAppointmentRequest;
import com.hmdp.appointment.entity.HomeServiceAppointment;

import java.util.List;

public interface IHomeServiceAppointmentService extends IService<HomeServiceAppointment> {
    HomeServiceAppointment create(Long userId, CreateHomeServiceAppointmentRequest request, String source);
    List<HomeServiceAppointment> listByUserId(Long userId);
    boolean cancelByUserId(Long userId, Long appointmentId);
    boolean confirmByShop(Long shopId, Long appointmentId);
    boolean rejectByShop(Long shopId, Long appointmentId);
    boolean completeByShop(Long shopId, Long appointmentId);
    int timeoutPendingAppointments();
}
