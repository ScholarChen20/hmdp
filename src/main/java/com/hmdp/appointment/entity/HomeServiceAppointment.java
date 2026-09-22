package com.hmdp.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_home_service_appointment")
public class HomeServiceAppointment {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long shopId;
    private String serviceType;
    private LocalDateTime appointmentTime;
    private String contactName;
    private String contactPhone;
    private String serviceAddress;
    private String remark;
    /** 0 pending, 1 confirmed, 2 user cancelled, 3 completed, 4 rejected, 5 timed out */
    private Integer status;
    private String source;
    private String idempotencyKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
