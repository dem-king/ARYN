package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.vo.CouponTemplateInfoVO;

/**
 * 优惠券模板查询。
 */
public interface RemoteCouponInfoService {

	/**
	 * 查询未删除的优惠券模板，不存在返回 null。
	 */
	CouponTemplateInfoVO getTemplate(String id);

}
