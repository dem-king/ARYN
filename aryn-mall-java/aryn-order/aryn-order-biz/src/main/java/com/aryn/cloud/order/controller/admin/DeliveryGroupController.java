package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.DeliveryGroupCommands.*;
import com.aryn.cloud.order.service.impl.DeliveryGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery-groups")
public class DeliveryGroupController {
    private final DeliveryGroupService service;
    @GetMapping
    @SaCheckPermission("fulfillment:wave:page")
    public Result<?> list(@RequestParam(required = false) String vesselCallId, @RequestParam(required = false) String after) {
        return Result.success(service.list(ArynTenantContextHolder.getTenantId(), StpUtil.getLoginIdAsString(), vesselCallId, after));
    }
    @GetMapping("/{id}")
    @SaCheckPermission("fulfillment:wave:page")
    public Result<?> detail(@PathVariable String id) {
        return Result.success(service.detail(ArynTenantContextHolder.getTenantId(), StpUtil.getLoginIdAsString(), id));
    }
    @GetMapping("/{id}/candidates")
    @SaCheckPermission("fulfillment:wave:page")
    public Result<?> candidates(@PathVariable String id) {
        return Result.success(service.candidates(ArynTenantContextHolder.getTenantId(), StpUtil.getLoginIdAsString(), id));
    }
    @PostMapping
    @SaCheckPermission("fulfillment:wave:save")
    public Result<?> create(@RequestBody Create input) {
        return Result.success(service.create(ArynTenantContextHolder.getTenantId(), StpUtil.getLoginIdAsString(), input));
    }
    @PostMapping("/{id}/commands")
    @SaCheckPermission("fulfillment:wave:save")
    public Result<?> change(@PathVariable String id, @RequestBody Change input) {
        return Result.success(service.change(ArynTenantContextHolder.getTenantId(), StpUtil.getLoginIdAsString(), id, input));
    }
}
