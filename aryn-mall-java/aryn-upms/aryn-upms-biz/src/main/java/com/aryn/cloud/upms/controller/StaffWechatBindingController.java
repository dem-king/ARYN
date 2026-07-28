package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.aryn.cloud.upms.api.remote.RemoteDeliveryStaffService;
import com.aryn.cloud.upms.api.remote.RemoteStaffWechatBindingService;
import com.aryn.cloud.user.api.remote.RemoteMiniAppGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前配送员工绑定配送小程序 OpenID。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/staff/wechat-binding")
public class StaffWechatBindingController {

	private final RemoteDeliveryStaffService deliveryStaffService;
	private final RemoteStaffWechatBindingService bindingService;
	@DubboReference
	private final RemoteMiniAppGateway miniAppGateway;

	@PostMapping
	@SaCheckPermission("order:delivery:execute")
	public Result<SysUserWechatBinding> bind(@Valid @RequestBody BindingRequest request) {
		ArynUser staff = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		if (!deliveryStaffService.isEligible(staff.getTenantId(), staff.getUserId())) {
			throw new ArynBusinessException("当前员工无商城配送权限");
		}
		String openId = miniAppGateway.exchangeOpenId(request.getAppId(), request.getJsCode());
		return Result.success(bindingService.bind(staff.getTenantId(), staff.getUserId(), request.getAppId(), openId));
	}

	@Data
	public static class BindingRequest {
		@NotBlank
		private String appId;
		@NotBlank
		private String jsCode;
	}
}
