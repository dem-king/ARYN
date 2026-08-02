package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;

import java.math.BigDecimal;

/**
 * 秒杀 Dubbo 远程服务接口
 */
public interface RemoteSeckillService {

	/**
	 * 预扣秒杀库存（Lua 原子扣减 + 限购校验）
	 *
	 * @param dto      下单参数
	 * @param userId   用户ID
	 * @param orderId  业务订单ID（用于关联预扣记录）
	 * @return 秒杀价 * 数量（成功）；null 表示失败（库存不足/超出限购/活动未开始）
	 */
	BigDecimal deductStock(SeckillOrderDTO dto, String userId, String orderId);

	/**
	 * 回滚预扣库存（下单失败/取消时调用）
	 *
	 * @param orderId 业务订单ID
	 * @return true=回滚成功
	 */
	boolean rollbackStock(String orderId);

	/**
	 * 查询 SKU 当前秒杀价（供订单价格计算使用）
	 *
	 * @param skuId SKU ID
	 * @return 秒杀价；null 表示无进行中的秒杀
	 */
	BigDecimal getSeckillPrice(String skuId);

	/**
	 * 查询 SKU 秒杀商品信息
	 */
	AppSeckillGoodsVO getSeckillGoodsInfo(String skuId);
}