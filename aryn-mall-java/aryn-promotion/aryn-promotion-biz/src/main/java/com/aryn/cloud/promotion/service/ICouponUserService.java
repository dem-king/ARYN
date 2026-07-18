
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.promotion.api.vo.CouponUserVO;

import java.util.List;

public interface ICouponUserService extends IService<CouponUser> {

	IPage<CouponUser> getPage(Page page, CouponUser couponUser);

	CouponUser receive(CouponUser couponUser);

	boolean rollBackCoupon(String couponUserId);

	IPage<CouponUser> getApiPage(Page page, CouponUser couponUser);

	Boolean updateCouponUserStatus(CouponUserReqDTO request);

	CouponUserRespVO getCouponUserById(String id, String userId);

	boolean grantMemberBenefitCoupon(String couponTemplateId, String userId, String sourceId);

	List<CouponUser> getExpireCouponList();

	/**
	 * 根据优惠券id查询优惠券领取数量
	 * @param couponIds
	 * @return
	 */
	List<CouponUserVO> getCountByCouponIds(String[] couponIds);

}
