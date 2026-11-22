package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.vo.OrderContextVO;

import java.util.List;

/**
 * 订单上下文远程契约（内部 Dubbo，无权限项）。
 *
 * <p>供客服工作台聚合「会员最近订单」上下文；调用方（message 域）只透传当前租户与会员 ID，
 * 订单域按租户 + 会员过滤，不返回其它会员数据。</p>
 */
public interface RemoteOrderContextService {

	/**
	 * 查询会员最近订单（按下单时间倒序）。
	 * @param tenantId 租户 ID（必填，空返回空列表）
	 * @param customerId 会员 ID（必填，空返回空列表）
	 * @param limit 返回条数：≤0 归一化为 3，>10 截断为 10（1..10）
	 * @return 订单上下文列表；无数据/查询异常时返回空列表（降级，不抛错）
	 */
	List<OrderContextVO> queryRecentOrders(String tenantId, String customerId, int limit);

	/**
	 * 岸上采购主体可用成员校验（2026-11-15 审查修复新增：协议价批量查价的主体越权防护）。
	 *
	 * <p>口径与下单链 requireForOrder 一致：主体存在且未停用 + 本人有效成员；
	 * 不要求建车权限（展示场景只读）。任何不满足/查询异常返回 false（fail-closed）。
	 * @param tenantId 租户ID
	 * @param userId 用户ID
	 * @param buyerOrgId 采购主体ID
	 * @return 是否可用成员
	 */
	boolean isUsableBuyerOrgMember(String tenantId, String userId, String buyerOrgId);

}
