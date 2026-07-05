
package com.aryn.cloud.notify.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.notify.api.dto.NotifyBroadcastDTO;
import com.aryn.cloud.notify.api.entity.NotifyBroadcast;
import com.aryn.cloud.notify.service.INotifyBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 消息群发
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/notifybroadcast")
@Tag(description = "admin-notify-broadcast", name = "管理端-消息群发-API")
public class NotifyBroadcastController {

	private final INotifyBroadcastService notifyBroadcastService;

	@Operation(summary = "发送群发消息")
	@SaCheckPermission("notify:broadcast:send")
	@PostMapping("/send")
	public Result<Void> send(@RequestBody NotifyBroadcastDTO dto) {
		dto.setOperatorId(SecurityUtils.getUserId());
		notifyBroadcastService.send(dto);
		return Result.success();
	}

	@Operation(summary = "群发记录列表")
	@SaCheckPermission("notify:broadcast:list")
	@GetMapping("/page")
	public Result<IPage<NotifyBroadcast>> page(Page<NotifyBroadcast> page, NotifyBroadcast broadcast) {
		return Result.success(notifyBroadcastService.page(page, broadcast));
	}

	@Operation(summary = "群发记录详情")
	@SaCheckPermission("notify:broadcast:info")
	@GetMapping("/{id}")
	public Result<NotifyBroadcast> info(@PathVariable String id) {
		return Result.success(notifyBroadcastService.getById(id));
	}

}
