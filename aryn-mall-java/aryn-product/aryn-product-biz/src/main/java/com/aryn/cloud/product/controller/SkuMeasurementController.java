package com.aryn.cloud.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.support.ProductMeasurementRules;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/sku-measurement")
public class SkuMeasurementController {
    public record Preview(ProductMeasurementRules.Measurement measurement, BigDecimal rawQuantity, String rawUnit, boolean approximate) { }
    @PostMapping("/preview")
    @SaCheckPermission("product:goodsspu:get")
    public Result<?> preview(@RequestBody Preview command) {
        return Result.success(ProductMeasurementRules.convert(command.rawQuantity(), command.rawUnit(), command.approximate(), command.measurement()));
    }
}
