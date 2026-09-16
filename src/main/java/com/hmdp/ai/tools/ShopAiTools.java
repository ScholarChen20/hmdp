package com.hmdp.ai.tools;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShopAiTools {
    private final IShopService shopService;

    public ShopAiTools(IShopService shopService) { this.shopService = shopService; }

    @Tool("????ID?????????????????????????")
    public String getShopById(@P(value = "??ID", required = true) Long shopId) {
        return format(shopService.getById(shopId));
    }

    @Tool("???????????????5?????")
    public String searchShopsByName(@P(value = "????????", required = true) String keyword) {
        if (StrUtil.isBlank(keyword)) return "??????????";
        List<Shop> shops = shopService.lambdaQuery().like(Shop::getName, keyword).last("LIMIT 5").list();
        if (shops.isEmpty()) return "???????";
        return shops.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    private String format(Shop shop) {
        if (shop == null) return "??????";
        return String.format("??ID:%d???:%s???:%s???:%s?????:%s???:%.1f???:%d????ID:%d",
                shop.getId(), shop.getName(), shop.getAddress(), shop.getArea(), shop.getOpenHours(),
                shop.getScore() == null ? 0D : shop.getScore() / 10D,
                shop.getAvgPrice() == null ? 0L : shop.getAvgPrice(), shop.getTypeId());
    }
}
