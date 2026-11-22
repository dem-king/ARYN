package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.vo.EnterprisePurchaseAggVO;

import java.util.List;

/**
 * 订单指标 Dubbo 远程服务。
 *
 * <p>供平台侧租户概览读取订单域计数，租户由调用上下文决定。
 */
public interface RemoteOrderMetricService {

	/**
	 * 统计当前租户订单总数。
	 */
	Long countOrders();

	/**
	 * 企业采购额月度聚合（企业等级月度对账 Job 用，2026-11-15 审查修复新增）。
	 *
	 * <p>口径：已支付且完成收货的企业场景订单（scene=2 按船舶、scene=3 按采购主体），
	 * 按完成月份归属，剔除已退款明细与赠品，金额=实付商品金额（不含运费）。
	 * @param tenantId 租户ID（显式锁定，不依赖调用上下文）
	 * @param startMonth 起始月份 yyyy-MM（含）
	 * @return 聚合行（空列表=合法零采购）；查询失败抛出异常，调用方回退存量绑定重算，不得按零覆盖
	 */
	List<EnterprisePurchaseAggVO> aggregateEnterprisePurchase(String tenantId, String startMonth);

}
