package com.aryn.cloud.upms.controller;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.service.impl.AiConfigServiceImpl;
import com.aryn.cloud.common.core.util.Result;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
/** 禁止记录原始请求；HTTP不提供运行凭证/解密接口。 */
@RestController @RequestMapping("/ai-config") @RequiredArgsConstructor
public class AiConfigController {
    private final AiConfigServiceImpl service;
    private final com.aryn.cloud.upms.service.impl.AiLegacyBootstrapService bootstrap;
    public record Bootstrap(long expectedRevision,boolean embedding){ }
    @PostMapping("/bootstrap") @SaCheckPermission("upms:ai-config:edit") public Result<?> bootstrap(@RequestBody Bootstrap input){return Result.success(bootstrap.importDraft(input.expectedRevision(),input.embedding()));}
    @GetMapping @SaCheckPermission("upms:ai-config:query")
    public Result<?> current(){return Result.success(service.current());}
    @GetMapping("/versions") @SaCheckPermission("upms:ai-config:query")
    public Result<?> versions(){return Result.success(service.versions());}
    @GetMapping("/versions/{version}/tests")
    @SaCheckPermission("upms:ai-config:query")
    public Result<java.util.List<AiConfigTestResult>> testResults(@PathVariable String version) {
        return Result.success(service.testResults(version));
    }
    @PutMapping("/draft") @SaCheckPermission("upms:ai-config:edit")
    public Result<?> save(@RequestBody AiConfigSaveCommand command){return Result.success(service.save(command));}
    @PostMapping("/versions/{version}/models/{model}/test") @SaCheckPermission("upms:ai-config:test")
    public Result<?> test(@PathVariable String version,@PathVariable String model){return Result.success(service.test(version,model));}
    public record Activation(String version,long expectedRevision) { }
    @PostMapping("/versions/activate") @SaCheckPermission("upms:ai-config:activate")
    public Result<?> activate(@RequestBody Activation input){return Result.success(service.activate(input.version(),input.expectedRevision(),false));}
    @PostMapping("/versions/rollback") @SaCheckPermission("upms:ai-config:activate")
    public Result<?> rollback(@RequestBody Activation input){return Result.success(service.activate(input.version(),input.expectedRevision(),true));}
    public record Emergency(String providerId,boolean disabled,long expectedRevision) { }
    @PostMapping("/emergency") @SaCheckPermission("upms:ai-config:activate")
    public Result<?> emergency(@RequestBody Emergency input){return Result.success(service.emergency(input.providerId(),input.disabled(),input.expectedRevision()));}
    @GetMapping("/calls") @SaCheckPermission("upms:ai-config:query")
    public Result<?> calls(@RequestParam(required=false) String tenantId){return Result.success(service.logs(tenantId));}
}
