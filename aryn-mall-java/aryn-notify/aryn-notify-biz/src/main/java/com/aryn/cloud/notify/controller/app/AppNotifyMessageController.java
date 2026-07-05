
package com.aryn.cloud.notify.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.notify.api.entity.NotifyMessage;
import com.aryn.cloud.notify.api.vo.NotifyMessageVO;
import com.aryn.cloud.notify.api.vo.NotifyUnreadCountVO;
import com.aryn.cloud.notify.service.INotifyMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端 消息通知
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/notifymessage")
@Tag(description = "app-notify", name = "C端-消息通知-API")
public class AppNotifyMessageController {

	private final INotifyMessageService notifyMessageService;

	@Operation(summary = "分页查询消息列表")
	@GetMapping("/page")
	public Result<IPage<NotifyMessageVO>> page(
			@RequestParam(required = false) Integer notifyType,
			@RequestParam(defaultValue = "1") Long current,
			@RequestParam(defaultValue = "20") Long size) {
		String userId = SecurityUtils.getUserId();
		Page<NotifyMessage> page = new Page<>(current, size);
		return Result.success(notifyMessageService.pageUserMessages(page, userId, notifyType));
	}

	@Operation(summary = "获取未读消息数")
	@GetMapping("/unread-count")
	public Result<NotifyUnreadCountVO> unreadCount() {
		String userId = SecurityUtils.getUserId();
		return Result.success(notifyMessageService.getUnreadCount(userId));
	}

	@Operation(summary = "标记消息已读")
	@PutMapping("/read/{messageId}")
	public Result<Void> markAsRead(@PathVariable String messageId) {
		String userId = SecurityUtils.getUserId();
		notifyMessageService.markAsRead(userId, messageId);
		return Result.success();
	}

	@Operation(summary = "全部已读")
	@PutMapping("/read-all")
	public Result<Void> markAllAsRead(@RequestParam(required = false) Integer notifyType) {
		String userId = SecurityUtils.getUserId();
		notifyMessageService.markAllAsRead(userId, notifyType);
		return Result.success();
	}

	@Operation(summary = "删除消息")
	@DeleteMapping("/{messageId}")
	public Result<Void> delete(@PathVariable String messageId) {
		String userId = SecurityUtils.getUserId();
		notifyMessageService.deleteMessage(userId, messageId);
		return Result.success();
	}

}
