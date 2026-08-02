package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.DiscountActivityDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
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
	 * 获取进行中的折扣活动
	 */
	List<DiscountActivityVO> getActiveActivities();

	/**
	 * 获取商品当前最优折扣信息
	 */
	AppDiscountVO getGoodsDiscountInfo(String skuId);

	/**
	 * 计算商品折扣价
	 */
	BigDecimal calculatePrice(String skuId, BigDecimal originalPrice);

	/**
	 * 状态流转
	 */
	void refreshStatus();
}