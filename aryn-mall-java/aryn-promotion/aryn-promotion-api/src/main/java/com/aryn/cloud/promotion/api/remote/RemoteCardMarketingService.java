package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.GoodsCardTagDTO;
import com.aryn.cloud.promotion.api.dto.GoodsSkuSeckillDTO;

import java.util.List;

/**
 * 商品卡营销标签 Dubbo 远程接口（商品卡角标/卡面价用）。
 *
 * <p>新人价与秒杀同在 promotion 域，聚合成一个出口：消费方（product-biz 的
 * card-tags 聚合端点）一次 RPC 拿全；任何失败由消费方降级为空标签，本接口
 * 实现侧也不抛异常（展示增强，绝不断商品卡主流程）。
 *
 * @author aryn
 * @since 2026/11/08
 */
public interface RemoteCardMarketingService {

	/**
	 * 批量查询 SPU 的卡面营销标签（新人价 + 进行中秒杀）。
	 *
	 * @param tenantId 租户 ID；缺失时返回空列表（fail-closed，防跨租户）
	 * @param userId   当前登录用户 ID；null/空表示未登录，新人价按「潜在新人可享」口径下发
	 *                 （与免登楼层一致，资格最终在下单链路硬校验）
	 * @param spuIds   SPU ID 集合，超出上限部分忽略（展示缺失只影响角标，不影响交易）
	 * @return 命中的标签（只包含至少命中一项的 SPU）；无命中返回空列表
	 */
	List<GoodsCardTagDTO> batchCardTags(String tenantId, String userId, List<String> spuIds);

	/**
	 * 批量查询 SKU 级进行中秒杀价（价格快照 Job 用，{@code skuPriceDailySnapshotJobHandler}）。
	 *
	 * <p>与 {@link #batchCardTags} 的 SPU 聚合不同：快照按 SKU 逐行落库，这里保留 SKU 维度；
	 * 同一 SKU 命中多场次取最低。实现侧不抛异常，失败返回空列表（调用方降级为仅本地价快照）。
	 *
	 * @param tenantId 租户 ID；缺失时返回空列表（fail-closed，防跨租户）
	 * @param skuIds   SKU ID 集合，超出上限（200）部分忽略
	 * @return 进行中的秒杀价（只包含命中的 SKU）；无命中或失败返回空列表
	 */
	List<GoodsSkuSeckillDTO> batchActiveSeckill(String tenantId, List<String> skuIds);

}
