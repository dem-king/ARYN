
package com.aryn.cloud.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.pay.api.dto.PayConfigRevisionCreateReqDTO;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.vo.PayConfigCheckResultVO;
import com.aryn.cloud.pay.api.vo.PayConfigMaterialUploadVO;
import com.aryn.cloud.pay.api.vo.PayConfigRevisionVO;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.PayConfigCheckService;
import com.aryn.cloud.pay.service.PayConfigRevisionService;
import com.aryn.cloud.pay.service.PayCredentialService;
import com.aryn.cloud.pay.service.PayLegacyMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 支付配置
 *
 * @author 雨滴kian
 * @since 2022/3/18 10:11
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/payconfig")
@Tag(description = "payconfig", name = "支付配置")
public class PayConfigController {

	private final IPayConfigService payConfigService;

	private final PayCredentialService credentialService;

	private final PayConfigRevisionService revisionService;

	private final PayConfigCheckService checkService;

	private final PayLegacyMigrationService legacyMigrationService;

	@Operation(summary = "支付配置列表")
	@SaCheckPermission("pay:payconfig:page")
	@GetMapping("/page")
	public Result page(Page page, PayConfig payConfig) {
		return Result.success(payConfigService.pageSafe(page, payConfig));
	}

	@Operation(summary = "支付配置查询")
	@SaCheckPermission("pay:payconfig:get")
	@GetMapping("/{id}")
	public Result detail(@PathVariable("id") String id) {
		return Result.success(payConfigService.getDetail(id));
	}

	@Operation(summary = "支付配置新增（仅路由，不含密钥）")
	@SaCheckPermission("pay:payconfig:add")
	@SysLog("新增支付配置")
	@PostMapping
	public Result add(@RequestBody @Valid PayConfig payConfig) {
		boolean saved = payConfigService.saveConfig(payConfig);
		return Result.success(saved ? payConfig.getId() : null);
	}

	@Operation(summary = "支付配置编辑（仅路由，不含密钥）")
	@SaCheckPermission("pay:payconfig:edit")
	@SysLog("编辑支付配置")
	@PutMapping
	public Result edit(@RequestBody PayConfig payConfig) {
		return Result.success(payConfigService.updateConfig(payConfig));
	}

	@Operation(summary = "支付配置删除")
	@SaCheckPermission("pay:payconfig:del")
	@SysLog("删除支付配置")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		return Result.success(payConfigService.deleteConfig(id));
	}

	@Operation(summary = "上传支付资料")
	@SaCheckPermission("pay:payconfig:material")
	@SysLog("上传支付资料")
	@PostMapping("/{id}/materials")
	public Result uploadMaterial(@PathVariable("id") String id, @RequestParam("purpose") String purpose,
			@RequestParam("file") MultipartFile file) {
		PayConfigMaterialUploadVO uploaded = credentialService.uploadMaterial(id, purpose, file);
		return Result.success(uploaded);
	}

	@Operation(summary = "支付资料列表（不含私钥与密文）")
	@SaCheckPermission("pay:payconfig:get")
	@GetMapping("/{id}/materials")
	public Result listMaterials(@PathVariable("id") String id) {
		return Result.success(credentialService.listMaterials(id));
	}

	@Operation(summary = "创建支付身份版本（草稿）")
	@SaCheckPermission("pay:payconfig:material")
	@SysLog("创建支付身份版本")
	@PostMapping("/{id}/revisions")
	public Result createRevision(@PathVariable("id") String id,
			@RequestBody @Valid PayConfigRevisionCreateReqDTO request) {
		return Result.success(revisionService.createRevision(id, request));
	}

	@Operation(summary = "身份版本列表")
	@SaCheckPermission("pay:payconfig:get")
	@GetMapping("/{id}/revisions")
	public Result listRevisions(@PathVariable("id") String id) {
		return Result.success(revisionService.listRevisions(id));
	}

	@Operation(summary = "身份版本详情")
	@SaCheckPermission("pay:payconfig:get")
	@GetMapping("/{id}/revisions/{revisionId}")
	public Result getRevision(@PathVariable("id") String id, @PathVariable("revisionId") String revisionId) {
		return Result.success(revisionService.getRevision(id, revisionId));
	}

	@Operation(summary = "启用支付身份版本")
	@SaCheckPermission("pay:payconfig:enable")
	@SysLog("启用支付渠道")
	@PostMapping("/{id}/revisions/{revisionId}/publish")
	public Result publish(@PathVariable("id") String id, @PathVariable("revisionId") String revisionId,
			@RequestParam(value = "expectedVersion", required = false) Integer expectedVersion) {
		return Result.success(revisionService.publish(id, revisionId, expectedVersion));
	}

	@Operation(summary = "停用支付配置")
	@SaCheckPermission("pay:payconfig:enable")
	@SysLog("停用支付渠道")
	@PostMapping("/{id}/disable")
	public Result disable(@PathVariable("id") String id,
			@RequestParam(value = "expectedVersion", required = false) Integer expectedVersion) {
		revisionService.disable(id, expectedVersion);
		return Result.success();
	}

	@Operation(summary = "本地资料检查（仅证明材料内部一致）")
	@SaCheckPermission("pay:payconfig:check")
	@SysLog("检查支付资料")
	@PostMapping("/{id}/revisions/{revisionId}/check")
	public Result check(@PathVariable("id") String id, @PathVariable("revisionId") String revisionId) {
		PayConfigCheckResultVO result = checkService.check(id, revisionId);
		return Result.success(result);
	}

	@Operation(summary = "旧明文配置迁移（可重入，冲突阻断）")
	@SaCheckPermission("pay:payconfig:migrate")
	@SysLog("迁移旧支付配置")
	@PostMapping("/legacy/migrate")
	public Result migrateLegacy() {
		return Result.success(legacyMigrationService.migrate());
	}

	@Operation(summary = "旧证书上传入口已停用")
	@SaCheckPermission("pay:payconfig:edit")
	@Deprecated
	@PostMapping("/cert/upload")
	public Result uploadFile(MultipartFile file) {
		return Result.fail("该入口已停用：支付资料必须通过 /payconfig/{id}/materials 加密上传，不允许服务器路径");
	}

}
