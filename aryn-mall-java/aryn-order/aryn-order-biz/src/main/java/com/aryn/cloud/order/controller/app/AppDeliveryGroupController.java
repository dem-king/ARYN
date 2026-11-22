package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.service.impl.DeliveryGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/delivery-groups")
public class AppDeliveryGroupController {
    private final DeliveryGroupService service;
    @GetMapping("/checkout")
    public Result<?> checkout(@RequestParam String vesselId, @RequestParam String vesselCallId) {
        return Result.success(service.checkoutSchedule(ArynTenantContextHolder.getTenantId(),
            SecurityUtils.requireUser(DeviceTypeEnum.TOC).getUserId(), vesselId, vesselCallId));
    }
    @GetMapping("/orders/{orderId}")
    public Result<?> schedule(@PathVariable String orderId) {
        return Result.success(service.buyerSchedule(ArynTenantContextHolder.getTenantId(),
            SecurityUtils.requireUser(DeviceTypeEnum.TOC).getUserId(), orderId));
    }
}
