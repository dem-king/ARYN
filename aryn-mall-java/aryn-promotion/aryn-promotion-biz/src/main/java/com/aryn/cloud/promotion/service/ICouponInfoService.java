
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;

public interface ICouponInfoService extends IService<CouponInfo> {

	/**
	 * 查询详情
	 * @param id
	 * @return
	 */
	CouponInfo getCouponById(String id);

	/**
	 * 分页查询优惠券
	 * @param page
	 * @param couponInfo
	 * @param couponGoods
	 * @param couponUser
	 * @return
	 */
	IPage<CouponInfo> getPage(Page page, CouponInfo couponInfo, CouponGoods couponGoods, CouponUser couponUser);

	/**
	 * 分页查询后台优惠券
	 * @param page
	 * @param couponInfo
	 * @return
	 */
	IPage<CouponInfo> getAdminPage(Page page, CouponInfo couponInfo);

}
