package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.vo.AppGoodsRankVO;

import java.util.List;

/**
 * C 端商品榜单服务（搜索页「热门榜单」）。
 *
 * <p>两个榜单口径（见 docs/50-需求文档/2026-10-25-搜索页与排行组件重构）：
 * <ul>
 * <li>热搜榜：当前租户近 30 天真实浏览足迹（goods_footprint）聚合，仅上架商品；</li>
 * <li>热销榜：订单域近 90 天真实有效订单聚合（Dubbo 跨域），回商品域过滤当前在售并保持名次。</li>
 * </ul>
 * 出参只含名次相关的 id/name/subTitle/picUrl；租户缺失一律 fail-closed 返回空列表。
 *
 * @author aryn
 * @since 2026/10/25
 */
public interface IGoodsRankService {

	/**
	 * 热搜榜 Top10：当前租户近 30 天浏览足迹聚合，按浏览次数倒序。
	 *
	 * @param tenantId 租户（缺失时 fail-closed 返回空）
	 * @return 榜单条目（可能不足 10 条或为空）
	 */
	List<AppGoodsRankVO> hotTop10(String tenantId);

	/**
	 * 热销榜 Top10：订单域近 90 天有效订单聚合，回商品域过滤当前在售商品并保持名次。
	 *
	 * <p>订单服务异常时降级为空列表（warn 日志），不打断搜索主流程。
	 *
	 * @param tenantId 租户（缺失时 fail-closed 返回空）
	 * @return 榜单条目（可能不足 10 条或为空）
	 */
	List<AppGoodsRankVO> salesTop10(String tenantId);

}
