package com.aryn.cloud.message.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.assignment.ConversationCloseRequest;
import com.aryn.cloud.message.api.dto.assignment.ConversationTransferRequest;
import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationAssignmentLogVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.message.service.ConversationService;
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

import java.util.List;

/** 客服主管会话操作接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/conversation")
@Tag(description = "message-conversation-admin", name = "客服会话管理")
public class ConversationAdminController {

	private final ConversationAssignmentService assignmentService;
	private final ConversationService conversationService;

	@GetMapping("/page")
	@Operation(summary = "主管全量会话（含已关闭）")
	@SaCheckPermission("message:service:supervisor")
	public Result<ConversationInboxPageVO> page(ConversationInboxQuery query) {
		ArynUser user = currentUser();
		return Result.success(conversationService.adminInbox(user.getTenantId(), query));
	}

	@GetMapping("/{id}/assignment-logs")
	@Operation(summary = "会话分配审计日志")
	@SaCheckPermission("message:service:supervisor")
	public Result<List<ConversationAssignmentLogVO>> assignmentLogs(@PathVariable String id) {
		ArynUser user = currentUser();
		return Result.success(conversationService.assignmentLogs(user.getTenantId(), id));
	}

	@PostMapping("/{id}/assign")
	@Operation(summary = "主管强制分配或转交")
	@SaCheckPermission("message:service:supervisor")
	public Result<Void> assign(@PathVariable String id, @Valid @RequestBody ConversationTransferRequest request) {
		ArynUser user = currentUser();
		assignmentService.transfer(user.getTenantId(), id, user.getUserId(), request.getTargetStaffId(),
				request.getReason(), true);
		return Result.success();
	}

	@PostMapping("/{id}/close")
	@Operation(summary = "主管关闭会话")
	@SaCheckPermission("message:service:supervisor")
	public Result<Void> close(@PathVariable String id, @RequestBody(required = false) ConversationCloseRequest request) {
		ArynUser user = currentUser();
		assignmentService.close(user.getTenantId(), id, MessageIdentityType.SYS_USER, user.getUserId(),
				request == null ? null : request.getReason(), true);
		return Result.success();
	}

	private ArynUser currentUser() {
		return SecurityUtils.requireUser(DeviceTypeEnum.TOB);
	}

}
