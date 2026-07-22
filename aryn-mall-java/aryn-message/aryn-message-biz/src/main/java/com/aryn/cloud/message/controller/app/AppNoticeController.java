package com.aryn.cloud.message.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.notice.NoticeInboxQuery;
import com.aryn.cloud.message.api.dto.notice.NoticeReplyRequest;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxItemVO;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxPageVO;
import com.aryn.cloud.message.service.NoticeService;
import com.aryn.cloud.message.service.NoticeReplyService;
import com.aryn.cloud.message.api.vo.conversation.ChatMessageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端本人通知收件箱。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/notice")
@Tag(description = "message-notice-app", name = "商城通知收件箱")
public class AppNoticeController {

	private final NoticeService noticeService;
	private final NoticeReplyService noticeReplyService;

	@GetMapping
	@Operation(summary = "通知收件箱")
	public Result<NoticeInboxPageVO> inbox(NoticeInboxQuery query) {
		ArynUser user = currentUser();
		return Result.success(noticeService.inbox(user.getTenantId(), MessageIdentityType.MALL_USER, user.getUserId(), query));
	}

	@GetMapping("/{id}")
	@Operation(summary = "通知详情")
	public Result<NoticeInboxItemVO> detail(@PathVariable String id) {
		ArynUser user = currentUser();
		return Result.success(noticeService.inboxDetail(user.getTenantId(), MessageIdentityType.MALL_USER,
				user.getUserId(), id));
	}

	@GetMapping("/unread/count")
	@Operation(summary = "通知未读数")
	public Result<Long> unreadCount() {
		ArynUser user = currentUser();
		return Result.success(noticeService.unreadCount(user.getTenantId(), MessageIdentityType.MALL_USER,
				user.getUserId()));
	}

	@PostMapping("/{id}/read")
	@Operation(summary = "标记通知已读")
	public Result<Void> markRead(@PathVariable String id) {
		ArynUser user = currentUser();
		noticeService.markRead(user.getTenantId(), MessageIdentityType.MALL_USER, user.getUserId(), id);
		return Result.success();
	}

	@PostMapping("/read-all")
	@Operation(summary = "全部标记已读")
	public Result<Void> markAllRead() {
		ArynUser user = currentUser();
		noticeService.markAllRead(user.getTenantId(), MessageIdentityType.MALL_USER, user.getUserId());
		return Result.success();
	}

	@PostMapping("/{id}/hide")
	@Operation(summary = "隐藏通知")
	public Result<Void> hide(@PathVariable String id) {
		ArynUser user = currentUser();
		noticeService.hide(user.getTenantId(), MessageIdentityType.MALL_USER, user.getUserId(), id);
		return Result.success();
	}

	@PostMapping("/{id}/reply")
	@Operation(summary = "从通知发起客服咨询")
	public Result<ChatMessageVO> reply(@PathVariable String id, @Valid @RequestBody NoticeReplyRequest request) {
		ArynUser user = currentUser();
		return Result.success(noticeReplyService.replyAsMember(user.getTenantId(), user.getUserId(), user.getNickname(),
				user.getAvatar(), id, request.getClientMessageId()));
	}

	private ArynUser currentUser() {
		return SecurityUtils.requireUser(DeviceTypeEnum.TOC);
	}

}
