package com.hmdp.appointment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.appointment.dto.CreateHomeServiceAppointmentRequest;
import com.hmdp.appointment.entity.HomeServiceAppointment;
import com.hmdp.appointment.mapper.HomeServiceAppointmentMapper;
import com.hmdp.appointment.service.IHomeServiceAppointmentService;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HomeServiceAppointmentServiceImpl extends ServiceImpl<HomeServiceAppointmentMapper, HomeServiceAppointment>
        implements IHomeServiceAppointmentService {
    private final IShopService shopService;
    private final RedisIdWorker redisIdWorker;

    public HomeServiceAppointmentServiceImpl(IShopService shopService, RedisIdWorker redisIdWorker) {
        this.shopService = shopService;
        this.redisIdWorker = redisIdWorker;
    }

    @Override
    @Transactional
    public HomeServiceAppointment create(Long userId, CreateHomeServiceAppointmentRequest request, String source) {
        if (request.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("????????????");
        }
        Shop shop = shopService.getById(request.getShopId());
        if (shop == null) {
            throw new IllegalArgumentException("?????");
        }
        if (StrUtil.isNotBlank(request.getIdempotencyKey())) {
            HomeServiceAppointment existing = lambdaQuery().eq(HomeServiceAppointment::getUserId, userId)
                    .eq(HomeServiceAppointment::getIdempotencyKey, request.getIdempotencyKey()).one();
            if (existing != null) return existing;
        }
        HomeServiceAppointment appointment = new HomeServiceAppointment();
        BeanUtil.copyProperties(request, appointment);
        appointment.setId(redisIdWorker.nexId("home-appointment"));
        appointment.setUserId(userId);
        appointment.setStatus(0);
        appointment.setSource(source);
        appointment.setCreateTime(LocalDateTime.now());
        appointment.setUpdateTime(LocalDateTime.now());
        save(appointment);
        return appointment;
    }

    @Override
    public List<HomeServiceAppointment> listByUserId(Long userId) {
        return lambdaQuery().eq(HomeServiceAppointment::getUserId, userId)
                .orderByDesc(HomeServiceAppointment::getCreateTime).list();
    }

    @Override
    @Transactional
    public boolean cancelByUserId(Long userId, Long appointmentId) {
        return lambdaUpdate().eq(HomeServiceAppointment::getId, appointmentId)
                .eq(HomeServiceAppointment::getUserId, userId).eq(HomeServiceAppointment::getStatus, 0)
                .set(HomeServiceAppointment::getStatus, 2).update();
    }
}
