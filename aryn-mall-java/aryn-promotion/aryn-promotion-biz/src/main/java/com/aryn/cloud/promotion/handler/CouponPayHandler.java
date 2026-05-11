
package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.service.ICouponUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CouponPayHandler implements PromotionPayEventHandler {

	private final ICouponUserService couponUserService;

	@Override
	public void handle(OrderPaySuccessEvent event) {
		if (!StringUtils.hasText(event.getCouponUserId())) {
			return;
		}
		// 更新用户优惠券状态
		CouponUserReqDTO couponUserReqDTO = new CouponUserReqDTO();
		couponUserReqDTO.setId(event.getCouponUserId());
		couponUserReqDTO.setCouponUserStatusEnum(CouponUserStatusEnum.STATUS_1);
		couponUserService.updateCouponUserStatus(couponUserReqDTO);
	}

}
