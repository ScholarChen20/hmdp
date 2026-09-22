package com.hmdp.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.appointment.entity.HomeServiceAppointment;
import org.apache.ibatis.annotations.Insert;

public interface HomeServiceAppointmentMapper extends BaseMapper<HomeServiceAppointment> {

    @Insert("INSERT IGNORE INTO tb_home_service_appointment "
            + "(id, user_id, shop_id, service_type, appointment_time, contact_name, contact_phone, "
            + "service_address, remark, status, source, idempotency_key, create_time, update_time) "
            + "VALUES (#{id}, #{userId}, #{shopId}, #{serviceType}, #{appointmentTime}, #{contactName}, "
            + "#{contactPhone}, #{serviceAddress}, #{remark}, #{status}, #{source}, #{idempotencyKey}, "
            + "#{createTime}, #{updateTime})")
    int insertIgnore(HomeServiceAppointment appointment);
}
