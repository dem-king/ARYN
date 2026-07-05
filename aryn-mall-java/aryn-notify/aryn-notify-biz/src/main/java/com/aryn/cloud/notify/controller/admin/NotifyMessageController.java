
package com.aryn.cloud.notify.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.notify.api.entity.NotifyMessage;
import com.aryn.cloud.notify.service.INotifyMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 消息记录查看
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/notifymessage")
@Tag(description = "admin-notify-message", name = "管理端-消息记录-API")
public class NotifyMessageController {

	private final INotifyMessageService notifyMessageService;

	@Operation(summary = "分页查询消息记录")
	@SaCheckPermission("notify:message:list")
	@GetMapping("/page")
	public Result<IPage<NotifyMessage>> page(Page<NotifyMessage> page, NotifyMessage notifyMessage) {
		return Result.success(notifyMessageService.pageAdminMessages(page, notifyMessage));
	}

}
