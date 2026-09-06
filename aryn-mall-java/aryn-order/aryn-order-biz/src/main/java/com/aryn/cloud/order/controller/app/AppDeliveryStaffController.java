package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.vo.DeliveryStaffVO;
import com.aryn.cloud.order.security.DeliveryAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 配送员当前身份信息。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/delivery/staff")
@Tag(description = "app-delivery-staff", name = "配送员身份-API")
public class AppDeliveryStaffController {

	private final DeliveryAccessGuard deliveryAccessGuard;

	@Operation(summary = "当前配送员信息")
	@GetMapping("/me")
	public Result<DeliveryStaffVO> me() {
		DeliveryStaff staff = deliveryAccessGuard.requireCurrentStaff();
		DeliveryStaffVO vo = new DeliveryStaffVO();
		BeanUtils.copyProperties(staff, vo);
		return Result.success(vo);
	}

}
