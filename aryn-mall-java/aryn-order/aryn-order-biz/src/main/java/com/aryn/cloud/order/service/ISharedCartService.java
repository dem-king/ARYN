package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartVO;

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
	 * 查询购物车详情视图（仅成员可见），含船舶/靠港展示字段与查看者权限标记。
	 */
	SharedCartVO getCartDetail(String tenantId, String userId, String cartId);

	/**
	 * 查询我参与的全部共享购物车（我发起或我作为成员），按创建时间倒序。
	 */
	List<SharedCartVO> listMyCarts(String tenantId, String userId);

	/**
	 * 查询购物车成员。
	 */
	List<SharedCartMember> listMembers(String tenantId, String cartId);

	/**
	 * 设置当前成员在共享购物车中的展示姓名（加入时填写一次，用于配送贴标签）。
	 */
	SharedCartMember updateMemberDisplayName(String tenantId, String userId, String cartId, String displayName);

	/**
	 * 查询购物车有效明细。
	 */
	List<SharedCartItem> listItems(String tenantId, String cartId);

	/**
	 * 生成/复用分享令牌（发起人）。令牌随购物车过期失效，用于微信群转发自助加入。
	 * @return 分享令牌
	 */
	String ensureShareToken(String tenantId, String userId, String cartId);

	/**
	 * 凭分享令牌自助加入：校验令牌与购物车有效期，自动补建船舶成员关系后写入成员行。
	 * @return 加入后的购物车（供前端直接跳转详情）
	 */
	SharedCart joinByShareToken(String tenantId, String userId, String shareToken);

	/**
	 * 关闭已过期的收集/待确认购物车，返回处理条数（定时任务调用，按租户执行）。
	 */
	int closeExpiredCarts(int batchSize);

	/**
	 * 订单签收后归档：把生成该订单的共享购物车置为「已完成」，让船员看到「本次采购已送达」。
	 * @return 是否归档成功（无关联购物车视为无需处理，返回 false）
	 */
	boolean archiveOnOrderSigned(String orderId);

}
