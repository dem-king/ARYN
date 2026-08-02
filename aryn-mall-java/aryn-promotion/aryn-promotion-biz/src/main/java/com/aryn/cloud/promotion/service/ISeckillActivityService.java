package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SeckillActivityDTO;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;
import com.aryn.cloud.promotion.api.vo.AppSeckillVO;
import com.aryn.cloud.promotion.api.vo.SeckillActivityVO;

import java.util.List;

public interface ISeckillActivityService extends IService<SeckillActivity> {

	// ==================== 管理端 ====================

	/**
	 * 管理端分页查询
	 */
	IPage<SeckillActivity> getAdminPage(Page page, SeckillActivity activity);

	/**
	 * 活动详情(含场次和商品)
	 */
	SeckillActivityVO getDetail(String id);

	/**
	 * 保存活动+场次+商品(事务)
	 */
	boolean saveWithSessions(SeckillActivityDTO dto);

	/**
	 * 更新活动+场次+商品(事务)
	 */
	boolean updateWithSessions(SeckillActivityDTO dto);

	/**
	 * 更新活动状态
	 */
	boolean updateStatus(String id, Integer status);

	// ==================== C端 ====================

	/**
	 * 获取进行中的场次列表
	 */
	List<AppSeckillVO> getActiveSessions();

	/**
	 * 获取场次商品列表
	 */
	AppSeckillVO getSessionGoods(String sessionId);

	/**
	 * 获取商品当前秒杀信息
	 */
	AppSeckillGoodsVO getGoodsSeckillInfo(String skuId);
}