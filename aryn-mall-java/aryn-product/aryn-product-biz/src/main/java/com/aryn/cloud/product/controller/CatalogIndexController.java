package com.aryn.cloud.product.controller;
import com.aryn.cloud.product.support.CatalogIndexService;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/catalog-index") @RequiredArgsConstructor
public class CatalogIndexController {
    private final CatalogIndexService service;
    private void platform(){var user=SecurityUtils.getUser();if(user==null || !Boolean.TRUE.equals(user.getPlatformAdmin()) || user.getDeviceType()!=DeviceTypeEnum.TOB)throw new SecurityException("仅平台管理员可管理目录索引");}
    @GetMapping @SaCheckPermission("upms:ai-config:query") public Result<?> list(){platform();return Result.success(service.status());}
    @PostMapping @SaCheckPermission("upms:ai-config:edit") public Result<?> create(){platform();return Result.success(service.create());}
    @PostMapping("/{id}/retry") @SaCheckPermission("upms:ai-config:edit") public Result<?> retry(@PathVariable String id){platform();service.retry(id);return Result.success();}
    public record Activation(String generation,long expectedRevision){ }
    @PostMapping("/activate") @SaCheckPermission("upms:ai-config:activate") public Result<?> activate(@RequestBody Activation command){platform();service.activate(command.generation(),command.expectedRevision());return Result.success();}
}
