package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SeckillActivityDTO;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;
import com.aryn.cloud.promotion.api.vo.AppSeckillVO;
import com.aryn.cloud.promotion.api.vo.SeckillActivityVO;

import java.util.Collection;
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
	 * 获取场次列表（今日剩余 + 明日预告）
	 *
	 * @param mallUserId 当前商城用户ID，可为空；非空时回填是否已订阅开抢提醒
	 */
	List<AppSeckillVO> getActiveSessions(String mallUserId);

	/**
	 * 获取场次商品列表
	 */
	AppSeckillVO getSessionGoods(String sessionId);

	/**
	 * 获取商品当前秒杀信息
	 */
	AppSeckillGoodsVO getGoodsSeckillInfo(String skuId);

	/**
	 * 批量查询 SPU 当前进行中的秒杀商品（商品卡「秒杀价」角标用）。
	 *
	 * <p>「进行中」与 {@link #getActiveSessions(String)} 同口径：按时间判定
	 * （活动未暂停未结束 + 场次已开始未结束），不依赖定时任务落库的场次 status，
	 * 任务停摆时卡面角标与会场表现一致不空窗。同一 SPU 命中多个并行场次时全部返回，
	 * 最低价聚合由调用方决定。
	 *
	 * @param spuIds SPU ID 集合
	 * @return 命中的秒杀商品（同一 SPU 可能多行）
	 */
	List<SeckillGoods> getActiveSeckillGoodsBySpuIds(Collection<String> spuIds);

	/**
	 * 批量查询 SKU 当前进行中的秒杀商品（价格快照 Job 用，SKU 维度不聚合）。
	 *
	 * <p>「进行中」口径与 {@link #getActiveSeckillGoodsBySpuIds(Collection)} 完全一致，
	 * 仅过滤列不同（按 sku_id 点查）。
	 *
	 * @param skuIds SKU ID 集合
	 * @return 命中的秒杀商品（同一 SKU 可能多行，最低价聚合由调用方决定）
	 */
	List<SeckillGoods> getActiveSeckillGoodsBySkuIds(Collection<String> skuIds);
}