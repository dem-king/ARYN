package com.aryn.cloud.message.controller.staff;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.conversation.StaffDirectConversationRequest;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.service.StaffDirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作人员一对一私信入口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/staff/direct")
@Tag(description = "message-staff-direct", name = "工作人员私信")
public class StaffDirectController {

	private final StaffDirectService staffDirectService;

	@PostMapping
	@Operation(summary = "创建或获取一对一私信")
	@SaCheckPermission("message:staff:direct")
	public Result<ConversationVO> getOrCreate(@Valid @RequestBody StaffDirectConversationRequest request) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(staffDirectService.getOrCreate(user.getTenantId(), user.getUserId(), user.getNickname(),
				user.getAvatar(), request.getTargetStaffId()));
	}

}
