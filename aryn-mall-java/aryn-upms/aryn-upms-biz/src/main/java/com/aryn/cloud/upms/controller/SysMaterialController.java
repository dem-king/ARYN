
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.service.ISysMaterialGroupService;
import com.aryn.cloud.upms.service.ISysMaterialService;
import com.aryn.cloud.upms.support.PrivateFileUrlSigner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 素材
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:30
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/material")
@Tag(description = "material", name = "素材")
public class SysMaterialController {

	private final ISysMaterialService materialService;

	private final PrivateFileUrlSigner privateFileUrlSigner;

	private final ISysMaterialGroupService materialGroupService;

	@Operation(summary = "素材列表")
	@SaCheckPermission("upms:material:page")
	@GetMapping("/page")
	public Result<IPage<SysMaterial>> page(Page page, SysMaterial material) {
		IPage<SysMaterial> iPage = materialService.getPage(page, material);
		iPage.getRecords().forEach(record -> {
			if ("delivery-evidence".equals(record.getBizTag())) {
				try {
					record.setUrl(privateFileUrlSigner.sign(record.getUrl()));
				}
				catch (Exception e) {
					// 签名不可用时保留原 URL：下载端会拒绝匿名访问，但列表本身不应整体失败
					log.warn("私有素材URL签名失败 materialId={}", record.getId(), e);
				}
			}
		});
		return Result.success(iPage);
	}

	@SysLog("新增素材")
	@Operation(summary = "素材新增")
	@SaCheckPermission("upms:material:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SysMaterial material) {
		// groupId 为空 / '-1' / 指向不存在或已删除分组时落到默认分组，与上传接口同一口径
		material.setGroupId(materialGroupService.resolveGroupIdForSave(ArynTenantContextHolder.getTenantId(),
				material.getGroupId()));
		return Result.success(materialService.save(material));
	}

	@SysLog("修改素材")
	@Operation(summary = "素材修改")
	@SaCheckPermission("upms:material:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody SysMaterial material) {
		// 私有凭证的 URL 由上传时落库，页面回显的是带过期时间的签名 URL；
		// 编辑时保留原 URL 与业务归属，避免把 24h 签名快照写回数据库或把私有素材改成公开素材。
		SysMaterial existing = material.getId() == null ? null : materialService.getById(material.getId());
		if (existing != null && "delivery-evidence".equals(existing.getBizTag())) {
			material.setUrl(existing.getUrl());
			material.setBizTag(existing.getBizTag());
		}
		// 只有请求里真的带了 groupId 才解析：前端只改名称/其它字段时 groupId 可能为空，
		// 直接回填默认分组会把素材从原分组悄悄挪走（MP updateById 又只更新非空字段，语义更含糊）
		if (material.getGroupId() != null && !material.getGroupId().isBlank()) {
			material.setGroupId(materialGroupService.resolveGroupIdForSave(
					ArynTenantContextHolder.getTenantId(), material.getGroupId()));
		}
		return Result.success(materialService.updateById(material));
	}

	@SysLog("删除素材")
	@Operation(summary = "素材删除")
	@SaCheckPermission("upms:material:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(materialService.removeById(id));
	}

}
