package com.aryn.cloud.message.controller.staff;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.agent.AgentConfigRequest;
import com.aryn.cloud.message.api.dto.agent.AgentPresenceRequest;
import com.aryn.cloud.message.api.vo.agent.AgentVO;
import com.aryn.cloud.message.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客服坐席配置、状态和心跳接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/staff/agent")
@Tag(description = "message-agent", name = "客服坐席")
public class AgentController {

	private final AgentService agentService;

	@PutMapping("/config")
	@Operation(summary = "配置客服坐席")
	@SaCheckPermission("message:service:agent")
	public Result<AgentVO> saveConfig(@Valid @RequestBody AgentConfigRequest request) {
		ArynUser user = currentUser();
		return Result.success(agentService.saveConfig(user.getTenantId(), user.getUserId(), request));
	}

	@GetMapping("/self")
	@Operation(summary = "本人客服坐席信息")
	public Result<AgentVO> self() {
		ArynUser user = currentUser();
		return Result.success(agentService.get(user.getTenantId(), user.getUserId()));
	}

	@PostMapping("/presence")
	@Operation(summary = "更新本人客服实时状态")
	public Result<AgentVO> presence(@Valid @RequestBody AgentPresenceRequest request) {
		ArynUser user = currentUser();
		return Result.success(agentService.setPresence(user.getTenantId(), user.getUserId(), request.getStatus()));
	}

	@PostMapping("/heartbeat")
	@Operation(summary = "客服心跳")
	public Result<Void> heartbeat() {
		ArynUser user = currentUser();
		agentService.heartbeat(user.getTenantId(), user.getUserId());
		return Result.success();
	}

	private ArynUser currentUser() {
		return SecurityUtils.requireUser(DeviceTypeEnum.TOB);
	}

}
