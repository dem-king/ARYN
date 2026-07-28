package com.aryn.cloud.order.delivery.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.vo.DeliveryAvailabilityVO;
import com.aryn.cloud.order.delivery.service.DeliveryAreaService;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/app")
@Tag(name = "商城配送可用性", description = "客户商城配送可用性")
public class AppDeliveryAvailabilityController {

	private final DeliveryAreaService deliveryAreaService;

	@DubboReference
	private final RemoteUserAddressService remoteUserAddressService;

	@GetMapping("/availability")
	@Operation(summary = "查询商城配送可用性")
	public Result<DeliveryAvailabilityVO> availability(@RequestParam String addressId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		UserAddress address = remoteUserAddressService.getById(addressId, user.getUserId());
		if (address == null) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50002.getCode(),
					MallErrorCodeEnum.ERROR_50002.getMsg());
		}
		return Result.success(deliveryAreaService.availability(address));
	}

}
