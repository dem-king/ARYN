package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.DiscountActivityDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.vo.AppDiscountActivityVO;
import com.aryn.cloud.promotion.api.vo.AppDiscountVO;
import com.aryn.cloud.promotion.api.vo.DiscountActivityVO;

import java.math.BigDecimal;
import java.util.List;

public interface IDiscountActivityService extends IService<DiscountActivity> {

	// ==================== 管理端 ====================

	/**
	 * 管理端分页查询
	 */
	IPage<DiscountActivity> getAdminPage(Page page, DiscountActivity activity);

	/**
	 * 活动详情(含商品)
	 */
	DiscountActivityVO getDetail(String id);

	/**
	 * 保存活动+商品(事务)
	 */
	boolean saveWithGoods(DiscountActivityDTO dto);

	/**
	 * 更新活动+商品(事务)
	 */
	boolean updateWithGoods(DiscountActivityDTO dto);

	/**
	 * 更新活动状态
	 */
	boolean updateStatus(String id, Integer status);

	// ==================== C端 ====================

	/**
	 * 分页获取进行中的折扣活动（含商品明细与实时折扣价，供 C 端会场按活动分组展示）
	 */
	IPage<AppDiscountActivityVO> getActiveActivityPage(Page page);

	/**
	 * 获取商品当前最优折扣信息
	 */
	AppDiscountVO getGoodsDiscountInfo(String skuId);

	/**
	 * 批量获取商品当前最优折扣信息（商详 / 购物车一次标注多行用，免登）。
	 *
	 * <p>口径与 {@link #getGoodsDiscountInfo(String)} 完全一致：以 SKU 销售价（salesPrice）为基准，
	 * 全场活动直接命中、指定商品活动需命中 discount_goods，取折后价最低的活动；
	 * 折后价不低于原价（含非法配置回退原价）的 SKU 不返回。
	 *
	 * @param skuIds SKU ID 列表（允许重复与空值，内部去重）
	 * @return 命中最优折扣的 SKU 价格列表；无命中返回空列表（不返回 null）
	 */
	List<AppDiscountVO> getGoodsDiscountPrices(List<String> skuIds);

	/**
	 * 计算商品折扣价
	 */
	BigDecimal calculatePrice(String skuId, BigDecimal originalPrice);

	/**
	 * 查询商品折扣价上下文（价格 + 活动叠加策略，订单侧计价用）。
	 *
	 * @return 上下文；null 表示无进行中的折扣
	 */
	com.aryn.cloud.promotion.api.vo.DiscountPriceContextVO getPriceContext(String skuId, BigDecimal originalPrice);

	/**
	 * 状态流转
	 */
	void refreshStatus();
}