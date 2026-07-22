package com.aryn.cloud.message.controller.staff;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageCursorQuery;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageSendRequest;
import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.dto.conversation.ConversationReadRequest;
import com.aryn.cloud.message.api.dto.assignment.ConversationClaimRequest;
import com.aryn.cloud.message.api.dto.assignment.ConversationCloseRequest;
import com.aryn.cloud.message.api.dto.assignment.ConversationTransferRequest;
import com.aryn.cloud.message.api.dto.conversation.StaffInitiateConversationRequest;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ChatMessagePageVO;
import com.aryn.cloud.message.api.vo.conversation.ChatMessageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.service.ChatMessageService;
import com.aryn.cloud.message.service.ConversationService;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 工作人员本人会话接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/staff/conversation")
@Tag(description = "message-conversation-staff", name = "工作人员会话")
public class StaffConversationController {

	private final ConversationService conversationService;
	private final ChatMessageService chatMessageService;
	private final ConversationAssignmentService assignmentService;

	@GetMapping("/waiting")
	@Operation(summary = "待领取会话池")
	public Result<List<ConversationVO>> waiting(@RequestParam(defaultValue = "DEFAULT") String queueCode,
			@RequestParam(defaultValue = "50") int limit) {
		ArynUser user = currentUser();
		return Result.success(assignmentService.waiting(user.getTenantId(), queueCode, limit));
	}

	@PostMapping("/initiate")
	@Operation(summary = "客服主动联系会员")
	@SaCheckPermission("message:conversation:initiate")
	public Result<ConversationVO> initiate(@Valid @RequestBody StaffInitiateConversationRequest request) {
		ArynUser user = currentUser();
		return Result.success(conversationService.initiateCustomerService(user.getTenantId(), user.getUserId(),
				request.getCustomerId(), request.getQueueCode(), request.getContextPayload()));
	}

	@GetMapping
	@Operation(summary = "本人会话列表")
	public Result<ConversationInboxPageVO> inbox(ConversationInboxQuery query) {
		ArynUser user = currentUser();
		return Result.success(conversationService.inbox(user.getTenantId(), MessageIdentityType.SYS_USER,
				user.getUserId(), query));
	}

	@GetMapping("/{id}")
	@Operation(summary = "会话详情")
	public Result<ConversationVO> detail(@PathVariable String id) {
		ArynUser user = currentUser();
		return Result.success(conversationService.get(user.getTenantId(), MessageIdentityType.SYS_USER,
				user.getUserId(), id));
	}

	@GetMapping("/{id}/messages")
	@Operation(summary = "会话消息游标页")
	public Result<ChatMessagePageVO> messages(@PathVariable String id, ChatMessageCursorQuery query) {
		ArynUser user = currentUser();
		return Result.success(chatMessageService.page(user.getTenantId(), MessageIdentityType.SYS_USER,
				user.getUserId(), id, query));
	}

	@PostMapping("/{id}/messages")
	@Operation(summary = "发送会话消息")
	public Result<ChatMessageVO> send(@PathVariable String id, @Valid @RequestBody ChatMessageSendRequest request) {
		ArynUser user = currentUser();
		return Result.success(chatMessageService.send(user.getTenantId(), MessageIdentityType.SYS_USER,
				user.getUserId(), user.getNickname(), user.getAvatar(), id, request));
	}

	@PostMapping("/{id}/read")
	@Operation(summary = "更新会话已读游标")
	public Result<Void> markRead(@PathVariable String id, @Valid @RequestBody ConversationReadRequest request) {
		ArynUser user = currentUser();
		conversationService.markRead(user.getTenantId(), MessageIdentityType.SYS_USER, user.getUserId(), id,
				request.getLastReadSeq());
		return Result.success();
	}

	@PostMapping("/{id}/claim")
	@Operation(summary = "领取待分配会话")
	public Result<Void> claim(@PathVariable String id, @RequestBody(required = false) ConversationClaimRequest request) {
		ArynUser user = currentUser();
		assignmentService.claim(user.getTenantId(), id, user.getUserId(), request == null ? null : request.getReason());
		return Result.success();
	}

	@PostMapping("/{id}/transfer")
	@Operation(summary = "转交会话")
	public Result<Void> transfer(@PathVariable String id, @Valid @RequestBody ConversationTransferRequest request) {
		ArynUser user = currentUser();
		assignmentService.transfer(user.getTenantId(), id, user.getUserId(), request.getTargetStaffId(),
				request.getReason(), false);
		return Result.success();
	}

	@PostMapping("/{id}/close")
	@Operation(summary = "关闭会话")
	public Result<Void> close(@PathVariable String id, @RequestBody(required = false) ConversationCloseRequest request) {
		ArynUser user = currentUser();
		assignmentService.close(user.getTenantId(), id, MessageIdentityType.SYS_USER, user.getUserId(),
				request == null ? null : request.getReason(), false);
		return Result.success();
	}

	private ArynUser currentUser() {
		return SecurityUtils.requireUser(DeviceTypeEnum.TOB);
	}

}
