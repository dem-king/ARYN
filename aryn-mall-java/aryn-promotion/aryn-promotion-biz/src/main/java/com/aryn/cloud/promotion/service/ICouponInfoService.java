
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;

import java.util.List;

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

	/**
	 * 按ID批量查询优惠券（C 端装修手选楼层口径：可见性与列表页一致，
	 * 游客只回有库存的券，登录用户保留本人已领取的券）
	 * @param ids 优惠券ID
	 * @param couponUser 登录用户，游客传空对象
	 * @return 命中的优惠券（顺序不保证，调用方按配置顺序重排）
	 */
	List<CouponInfo> listByIds(List<String> ids, CouponUser couponUser);

	/**
	 * 按ID批量查询优惠券（管理端口径：不做可见性过滤，装修面板回填已选项名称用）
	 */
	List<CouponInfo> listForAdminByIds(List<String> ids);

}
