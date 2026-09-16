package com.hmdp.appointment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class CreateHomeServiceAppointmentRequest {
    @NotNull(message = "??????")
    private Long shopId;
    @NotBlank(message = "????????")
    @Size(max = 64, message = "????????64???")
    private String serviceType;
    @NotNull(message = "????????")
    private LocalDateTime appointmentTime;
    @NotBlank(message = "???????")
    @Size(max = 32, message = "???????32???")
    private String contactName;
    @NotBlank(message = "????????")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "?????????")
    private String contactPhone;
    @NotBlank(message = "????????")
    @Size(max = 255, message = "????????255???")
    private String serviceAddress;
    @Size(max = 255, message = "??????255???")
    private String remark;
    @Size(max = 64, message = "???????64???")
    private String idempotencyKey;
}
