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
import org.springframework.scheduling.annotation.Scheduled;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

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
        if (baseMapper.insertIgnore(appointment) == 0) {
            if (StrUtil.isBlank(request.getIdempotencyKey())) {
                throw new IllegalStateException("预约创建失败");
            }
            return lambdaQuery().eq(HomeServiceAppointment::getUserId, userId)
                    .eq(HomeServiceAppointment::getIdempotencyKey, request.getIdempotencyKey()).one();
        }
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
                .eq(HomeServiceAppointment::getUserId, userId)
                .in(HomeServiceAppointment::getStatus, 0, 1)
                .set(HomeServiceAppointment::getStatus, 2).update();
    }

    @Override
    @Transactional
    public boolean confirmByShop(Long shopId, Long appointmentId) {
        return transition(appointmentId, shopId, 0, 1);
    }

    @Override
    @Transactional
    public boolean rejectByShop(Long shopId, Long appointmentId) {
        return transition(appointmentId, shopId, 0, 4);
    }

    @Override
    @Transactional
    public boolean completeByShop(Long shopId, Long appointmentId) {
        return transition(appointmentId, shopId, 1, 3);
    }

    private boolean transition(Long appointmentId, Long shopId, int from, int to) {
        return lambdaUpdate().eq(HomeServiceAppointment::getId, appointmentId)
                .eq(HomeServiceAppointment::getShopId, shopId)
                .eq(HomeServiceAppointment::getStatus, from)
                .set(HomeServiceAppointment::getStatus, to).update();
    }

    @Override
    @Scheduled(fixedDelayString = "${ai.appointment.timeout-scan-delay-ms:60000}")
    @Transactional
    public int timeoutPendingAppointments() {
        LambdaUpdateWrapper<HomeServiceAppointment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HomeServiceAppointment::getStatus, 0)
                .lt(HomeServiceAppointment::getAppointmentTime, LocalDateTime.now())
                .set(HomeServiceAppointment::getStatus, 5);
        return baseMapper.update(null, wrapper);
    }
}
