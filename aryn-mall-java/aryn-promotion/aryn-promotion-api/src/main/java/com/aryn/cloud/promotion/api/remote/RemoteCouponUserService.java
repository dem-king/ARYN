
package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;

/**
 * @author 雨滴kian
 */
public interface RemoteCouponUserService {

	/**
	 * 修改用户优惠券状态
	 * @param couponUserReqDTO
	 * @return
	 */
	boolean updateCouponUserStatus(CouponUserReqDTO couponUserReqDTO);

	/**
	 * 查询用户优惠券信息
	 * @param id
	 * @return
	 */
	CouponUserRespVO getById(String id, String userId);

	boolean reserveCoupon(String id, String userId, String orderId);

	boolean releaseCoupon(String id, String orderId);

	boolean grantMemberBenefitCoupon(String couponTemplateId, String userId, String sourceId);

}
