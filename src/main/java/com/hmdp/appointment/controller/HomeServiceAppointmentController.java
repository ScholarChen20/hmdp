package com.hmdp.appointment.controller;

import com.hmdp.appointment.dto.CreateHomeServiceAppointmentRequest;
import com.hmdp.appointment.service.IHomeServiceAppointmentService;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/appointments/home-service")
public class HomeServiceAppointmentController {
    private final IHomeServiceAppointmentService appointmentService;

    public HomeServiceAppointmentController(IHomeServiceAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public Result create(@Valid @RequestBody CreateHomeServiceAppointmentRequest request) {
        return Result.ok(appointmentService.create(UserHolder.getUser().getId(), request, "MANUAL"));
    }

    @GetMapping("/me")
    public Result mine() {
        return Result.ok(appointmentService.listByUserId(UserHolder.getUser().getId()));
    }

    @PutMapping("/{id}/cancel")
    public Result cancel(@PathVariable Long id) {
        return appointmentService.cancelByUserId(UserHolder.getUser().getId(), id)
                ? Result.ok() : Result.fail("????????????????????");
    }
}
