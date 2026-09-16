package com.hmdp.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.service.AiRequestContext;
import com.hmdp.appointment.dto.CreateHomeServiceAppointmentRequest;
import com.hmdp.appointment.entity.HomeServiceAppointment;
import com.hmdp.appointment.service.IHomeServiceAppointmentService;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AppointmentAiTools {
    private static final String DRAFT_KEY_PREFIX = "ai:appointment:confirm:";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;
    private final IHomeServiceAppointmentService appointmentService;

    public AppointmentAiTools(StringRedisTemplate stringRedisTemplate, AiProperties aiProperties,
                              IHomeServiceAppointmentService appointmentService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.aiProperties = aiProperties;
        this.appointmentService = appointmentService;
    }

    @Tool("?????????????????????????????????????????????????????????")
    public String prepareHomeServiceAppointment(
            @P(value = "??ID?????????????", required = true) Long shopId,
            @P(value = "???????????", required = true) String serviceType,
            @P(value = "??????? yyyy-MM-dd HH:mm", required = true) String appointmentTime,
            @P(value = "?????", required = true) String contactName,
            @P(value = "11?????????", required = true) String contactPhone,
            @P(value = "??????", required = true) String serviceAddress,
            @P(value = "????", required = false) String remark) {
        CreateHomeServiceAppointmentRequest request = new CreateHomeServiceAppointmentRequest();
        request.setShopId(shopId);
        request.setServiceType(serviceType);
        request.setContactName(contactName);
        request.setContactPhone(contactPhone);
        request.setServiceAddress(serviceAddress);
        request.setRemark(remark);
        try {
            request.setAppointmentTime(LocalDateTime.parse(appointmentTime, DATE_TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            return "???????????? yyyy-MM-dd HH:mm";
        }
        String invalid = validate(request);
        if (invalid != null) return invalid;
        request.setIdempotencyKey(UUID.randomUUID().toString().replace("-", ""));
        stringRedisTemplate.opsForValue().set(draftKey(), JSONUtil.toJsonStr(request),
                aiProperties.getAppointment().getConfirmationTtlMinutes(), TimeUnit.MINUTES);
        return String.format("??????????ID=%d???=%s???=%s????=%s???=%s???=%s???????????????????",
                shopId, serviceType, appointmentTime, contactName, maskPhone(contactPhone), serviceAddress);
    }

    @Tool("???????????????????????????????????????")
    public String submitPendingHomeServiceAppointment() {
        if (!AiRequestContext.isAppointmentConfirmed()) return "?????????????????????????????????????";
        String value = stringRedisTemplate.opsForValue().get(draftKey());
        if (StrUtil.isBlank(value)) return "???????????????????????????";
        CreateHomeServiceAppointmentRequest request = JSONUtil.toBean(value, CreateHomeServiceAppointmentRequest.class);
        try {
            HomeServiceAppointment appointment = appointmentService.create(UserHolder.getUser().getId(), request, "AI_CHAT");
            stringRedisTemplate.delete(draftKey());
            return "??????????" + appointment.getId() + "????????????";
        } catch (IllegalArgumentException e) {
            return "???????" + e.getMessage();
        }
    }

    private String draftKey() { return DRAFT_KEY_PREFIX + UserHolder.getUser().getId(); }

    private String validate(CreateHomeServiceAppointmentRequest request) {
        if (request.getShopId() == null || StrUtil.hasBlank(request.getServiceType(), request.getContactName(),
                request.getContactPhone(), request.getServiceAddress()) || request.getAppointmentTime() == null) return "???????";
        if (!request.getContactPhone().matches("^1[3-9]\\d{9}$")) return "?????????";
        if (request.getAppointmentTime().isBefore(LocalDateTime.now())) return "????????????";
        return null;
    }

    private String maskPhone(String phone) { return phone.substring(0, 3) + "****" + phone.substring(7); }
}
