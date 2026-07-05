
package com.aryn.cloud.notify.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.notify.api.dto.NotifyTemplateDTO;
import com.aryn.cloud.notify.api.entity.NotifyTemplate;
import com.aryn.cloud.notify.service.INotifyTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 消息模板
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/notifytemplate")
@Tag(description = "admin-notify-template", name = "管理端-消息模板-API")
public class NotifyTemplateController {

	private final INotifyTemplateService notifyTemplateService;

	@Operation(summary = "分页查询模板列表")
	@SaCheckPermission("notify:template:list")
	@GetMapping("/page")
	public Result<IPage<NotifyTemplate>> page(Page<NotifyTemplate> page, NotifyTemplate template) {
		return Result.success(notifyTemplateService.page(page, template));
	}

	@Operation(summary = "模板详情")
	@SaCheckPermission("notify:template:info")
	@GetMapping("/{id}")
	public Result<NotifyTemplate> info(@PathVariable String id) {
		return Result.success(notifyTemplateService.getById(id));
	}

	@Operation(summary = "新增模板")
	@SaCheckPermission("notify:template:add")
	@PostMapping
	public Result<Void> save(@RequestBody NotifyTemplateDTO dto) {
		notifyTemplateService.save(dto);
		return Result.success();
	}

	@Operation(summary = "修改模板")
	@SaCheckPermission("notify:template:edit")
	@PutMapping
	public Result<Void> update(@RequestBody NotifyTemplateDTO dto) {
		notifyTemplateService.update(dto);
		return Result.success();
	}

	@Operation(summary = "删除模板")
	@SaCheckPermission("notify:template:del")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		notifyTemplateService.delete(id);
		return Result.success();
	}

}
