
package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.promotion.service.ICouponUserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteCouponUserServiceImpl implements RemoteCouponUserService {

	private final ICouponUserService couponUserService;

	@Override
	public boolean updateCouponUserStatus(CouponUserReqDTO request) {
		return couponUserService.updateCouponUserStatus(request);
	}

	@Override
	public CouponUserRespVO getById(String id, String userId) {
		return couponUserService.getCouponUserById(id, userId);
	}

	@Override
	public boolean grantMemberBenefitCoupon(String couponTemplateId, String userId, String sourceId) {
		return couponUserService.grantMemberBenefitCoupon(couponTemplateId, userId, sourceId);
	}

}
