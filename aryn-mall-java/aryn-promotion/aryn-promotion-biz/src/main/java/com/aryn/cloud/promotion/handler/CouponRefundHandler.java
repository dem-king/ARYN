
package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.service.ICouponUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CouponRefundHandler implements PromotionRefundEventHandler {

	private final ICouponUserService couponUserService;

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		if (!StringUtils.hasText(event.getCouponUserId())) {
			return;
		}
		// 更新用户优惠券状态
		CouponUserReqDTO couponUserReqDTO = new CouponUserReqDTO();
		couponUserReqDTO.setId(event.getCouponUserId());
		couponUserReqDTO.setCouponUserStatusEnum(CouponUserStatusEnum.STATUS_0);
		couponUserService.updateCouponUserStatus(couponUserReqDTO);
	}

}
