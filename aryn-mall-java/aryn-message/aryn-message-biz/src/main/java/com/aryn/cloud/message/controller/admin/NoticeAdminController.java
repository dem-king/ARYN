package com.aryn.cloud.message.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.vo.notice.NoticeVO;
import com.aryn.cloud.message.service.NoticeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台通知管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notice")
@Tag(description = "message-notice-admin", name = "通知管理")
public class NoticeAdminController {

	private final NoticeService noticeService;

	@GetMapping("/page")
	@Operation(summary = "通知分页")
	@SaCheckPermission("message:notice:page")
	public Result<IPage<NoticeVO>> page(Page<?> page, @RequestParam(required = false) String status) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(noticeService.page(user.getTenantId(), status, page));
	}

	@GetMapping("/{id}")
	@Operation(summary = "通知详情")
	@SaCheckPermission("message:notice:get")
	public Result<NoticeVO> get(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(noticeService.get(user.getTenantId(), id));
	}

	@PostMapping
	@SysLog("新增通知草稿")
	@Operation(summary = "新增通知草稿")
	@SaCheckPermission("message:notice:add")
	public Result<NoticeVO> create(@Valid @RequestBody NoticeSaveRequest request) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(noticeService.createDraft(user.getTenantId(), user.getUserId(), user.getNickname(), request));
	}

	@PutMapping("/{id}")
	@SysLog("修改通知草稿")
	@Operation(summary = "修改通知草稿")
	@SaCheckPermission("message:notice:edit")
	public Result<NoticeVO> update(@PathVariable String id, @Valid @RequestBody NoticeSaveRequest request) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(noticeService.updateDraft(user.getTenantId(), user.getUserId(), id, request));
	}

	@PostMapping("/{id}/publish")
	@SysLog("发布通知")
	@Operation(summary = "发布通知")
	@SaCheckPermission("message:notice:publish")
	public Result<Void> publish(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		noticeService.publish(user.getTenantId(), user.getUserId(), id);
		return Result.success();
	}

	@DeleteMapping("/{id}/publish")
	@SysLog("撤回通知")
	@Operation(summary = "撤回通知")
	@SaCheckPermission("message:notice:revoke")
	public Result<Void> revoke(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		noticeService.revoke(user.getTenantId(), user.getUserId(), id);
		return Result.success();
	}

}
