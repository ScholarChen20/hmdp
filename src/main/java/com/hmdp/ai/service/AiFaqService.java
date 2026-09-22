package com.hmdp.ai.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class AiFaqService {
    private final Map<String, String> faq = new LinkedHashMap<>();

    public AiFaqService() {
        faq.put("营业时间", "商家的营业时间请以商家详情页显示为准，节假日可能会临时调整。");
        faq.put("优惠券", "优惠券需要在有效期内使用，具体使用门槛和规则以券面说明为准。");
        faq.put("预约", "预约流程是选择商家和服务后提交预约信息，等待商家确认；预约成功后请按约定时间到店或等待上门服务。");
        faq.put("退款", "订单退款规则以订单详情和商家活动规则为准，如需人工处理请联系平台客服。");
        faq.put("登录", "登录可以使用手机号和短信验证码完成，验证码请勿提供给任何人。");
    }

    public Optional<String> answer(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return faq.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
