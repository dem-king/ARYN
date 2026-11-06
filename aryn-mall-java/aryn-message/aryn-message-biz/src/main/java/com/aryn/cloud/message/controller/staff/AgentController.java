package com.aryn.cloud.message.controller.staff;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.agent.AgentConfigRequest;
import com.aryn.cloud.message.api.dto.agent.AgentPresenceRequest;
import com.aryn.cloud.message.api.vo.agent.AgentRosterVO;
import com.aryn.cloud.message.api.vo.agent.AgentVO;
import com.aryn.cloud.message.service.AgentService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

	@GetMapping("/config/{staffId}")
	@Operation(summary = "查询指定工作人员的客服坐席配置")
	@SaCheckPermission("message:service:agent")
	public Result<AgentVO> config(@PathVariable String staffId) {
		ArynUser user = currentUser();
		return Result.success(agentService.findConfig(user.getTenantId(), staffId));
	}

	@GetMapping("/candidates")
	@Operation(summary = "客服坐席候选工作人员")
	@SaCheckPermission("message:service:agent")
	public Result<StaffMessageAudiencePageVO> candidates() {
		ArynUser user = currentUser();
		return Result.success(agentService.listCandidates(user.getTenantId()));
	}

	@GetMapping("/roster")
	@Operation(summary = "客服坐席名单（在线状态与负载，供转交/强制分配选人）")
	@SaCheckPermission(value = { "message:service:agent", "message:service:supervisor" }, mode = SaMode.OR)
	public Result<List<AgentRosterVO>> roster() {
		ArynUser user = currentUser();
		return Result.success(agentService.listRoster(user.getTenantId()));
	}

	@GetMapping("/self")
	@Operation(summary = "本人客服坐席信息")
	public Result<AgentVO> self() {
		ArynUser user = currentUser();
		// 未配置坐席返回 null，前端据此展示「未配置」引导；不再抛业务错误。
		return Result.success(agentService.findConfig(user.getTenantId(), user.getUserId()));
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
