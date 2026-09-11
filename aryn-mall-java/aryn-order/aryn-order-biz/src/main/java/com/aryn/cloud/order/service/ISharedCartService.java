package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;

import java.util.List;

/**
 * 同船海员共享购物车服务。
 *
 * <p>权限：发起人可创建、邀请、提交确认和关闭；成员只能维护自己的明细；
 * 确认人可调整核定数量并统一提交生成一个整船订单。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface ISharedCartService {

	/**
	 * 创建共享购物车（发起人），绑定船舶与靠港计划后进入收集中。
	 */
	SharedCart create(String tenantId, String userId, SharedCartCreateDTO dto);

	/**
	 * 邀请成员加入（发起人）。
	 */
	SharedCartMember inviteMember(String tenantId, String cartId, String operatorUserId, String memberUserId,
			String memberRole);

	/**
	 * 成员添加自己的明细。
	 */
	SharedCartItem addItem(String tenantId, String userId, String cartId, SharedCartItemDTO itemDTO);

	/**
	 * 成员修改自己的明细（数量/备注）。
	 */
	SharedCartItem updateItem(String tenantId, String userId, String cartId, String itemId, SharedCartItemDTO itemDTO);

	/**
	 * 成员移除自己的明细。
	 */
	void removeItem(String tenantId, String userId, String cartId, String itemId);

	/**
	 * 确认人调整核定数量并统一提交，生成一个整船订单（按购物车幂等）。
	 * @return 提交生成的订单ID
	 */
	String confirmAndCreateOrder(String tenantId, String userId, String cartId, SharedCartConfirmDTO confirmDTO);

	/**
	 * 发起人关闭购物车。
	 */
	void close(String tenantId, String userId, String cartId);

	/**
	 * 查询购物车详情（仅成员可见）。
	 */
	SharedCart getCartForUser(String tenantId, String userId, String cartId);

	/**
	 * 查询购物车成员。
	 */
	List<SharedCartMember> listMembers(String tenantId, String cartId);

	/**
	 * 查询购物车有效明细。
	 */
	List<SharedCartItem> listItems(String tenantId, String cartId);

}
