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
	 * 按预扣参数释放库存（下单事务回滚时调用）。
	 *
	 * <p>下单与预扣在同一事务内时（boot 单体模式），事务回滚会连预扣记录一起回滚，
	 * 此时无法按订单号找回记录，必须凭参数直接回滚 Redis；预扣记录若仍在，
	 * 一并置为已取消。方法幂等，可安全重试。
	 *
	 * @param dto    预扣时的下单参数
	 * @param userId 用户ID
	 * @param orderId 业务订单ID
	 * @return true=已释放
	 */
	boolean releaseDeduct(SeckillOrderDTO dto, String userId, String orderId);

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