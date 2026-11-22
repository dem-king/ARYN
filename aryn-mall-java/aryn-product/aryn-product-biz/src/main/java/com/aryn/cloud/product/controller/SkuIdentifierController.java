package com.aryn.cloud.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.service.impl.SkuIdentifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/sku-identifiers")
@RequiredArgsConstructor
public class SkuIdentifierController {
    private final SkuIdentifierService service;
    @GetMapping @SaCheckPermission("product:goodsspu:get")
    public Result<?> list(@RequestParam String skuId) { return Result.success(service.list(skuId)); }
    @PostMapping @SaCheckPermission("product:goodsspu:edit")
    public Result<?> register(@RequestBody List<SkuIdentifierService.Input> inputs) { return Result.success(service.register(inputs, SecurityUtils.getUser().getUserId())); }
    public record Review(long expectedRevision, boolean approved) { }
    @PostMapping("/{id}/review") @SaCheckPermission("product:goodsspu:edit")
    public Result<?> review(@PathVariable String id, @RequestBody Review input) {
        service.review(id, input.expectedRevision(), input.approved(), SecurityUtils.getUser().getUserId()); return Result.success();
    }
}
