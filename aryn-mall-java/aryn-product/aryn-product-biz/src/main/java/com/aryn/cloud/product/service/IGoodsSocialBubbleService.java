package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.vo.GoodsSocialBubbleVO;

/**
 * 商详口碑气泡装配服务（C 端商品详情轮播图浮层）。
 *
 * @author aryn
 * @since 2026/11/08
 */
public interface IGoodsSocialBubbleService {

	/**
	 * 装配单个 SPU 的口碑气泡（社证文案 + 好评率 + 买家头像）。
	 *
	 * <p>社证与商品卡同源同门槛（订单域 T+1 统计），好评率与评价区同一口径；
	 * 任何一步失败/未达门槛只丢对应字段，整体失败返回 null——气泡属增强展示，
	 * 不影响商详主流程。
	 *
	 * @param spuId SPU ID
	 * @return 气泡数据；无任何可展示内容或装配失败返回 null
	 */
	GoodsSocialBubbleVO build(String spuId);

}
