package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.SharedCartImportConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartReuseDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartImportVO;
import com.aryn.cloud.order.api.vo.SharedCartItemVO;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartSummaryVO;
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
	 * 补给单 Excel 导入预览：解析 + 匹配 + 落任务与解析行，返回导入报告。
	 *
	 * <p>权限与提交整船订单一致（确认人/发起人）。解析与匹配全部在服务端完成，
	 * 客户端后续只回传「行号 → 处置动作」，不信任客户端回传的行内容。
	 */
	SharedCartImportVO previewImport(String tenantId, String userId, String cartId, String fileName, long fileSize,
			byte[] fileBytes);

	/**
	 * 微信群接龙粘贴导入预览：解析「人 × 商品 × 数量」+ 匹配 + 落任务与解析行。
	 *
	 * <p>与 Excel 导入共用报告与确认端点；行上多带 {@code personName}（接龙人名
	 * 原文）。确认并入时人名精确匹配成员自填姓名的归到真实成员，匹配不上的
	 * 作为归属姓名标签挂在操作者明细上——接龙里的很多人不是系统用户。
	 */
	SharedCartImportVO previewChainImport(String tenantId, String userId, String cartId, String text);

	/**
	 * 取回导入报告（「稍后处理」后回来继续）。
	 */
	SharedCartImportVO getImport(String tenantId, String userId, String cartId, String importId);

	/**
	 * 列出该补给单的导入任务（按时间倒序，不带行明细）。
	 */
	List<SharedCartImportVO> listImports(String tenantId, String userId, String cartId);

	/**
	 * 确认并入补给单：按行处置重新校验后写入明细，幂等。
	 *
	 * @return 并入后的报告（status=2 已并入）
	 */
	SharedCartImportVO confirmImport(String tenantId, String userId, String cartId, String importId,
			SharedCartImportConfirmDTO dto);

	/**
	 * 历史补给单一键复用：把源单明细复制到「当前靠港计划」下的购物车。
	 *
	 * <p>与「再来一单」同口径：复用只是把清单搬过来，不下单、不锁价、不校验库存，
	 * 用户确认后仍走正常的改数量→提交整船订单流程。
	 *
	 * <p>权限：须为源单成员（发起人也算），否则可以借复用读取任意购物车明细。
	 * 目标车沿用 create 的「同船同时只有一张收集中购物车」规则，命中则并入。
	 *
	 * @param sourceCartId 历史购物车ID
	 * @return 目标购物车与复用统计
	 */
	SharedCartReuseVO reuseFromHistory(String tenantId, String userId, String sourceCartId, SharedCartReuseDTO dto);

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
	 * 当前进行中的共享购物车摘要（首页「今日补给单」卡片一次取数）。
	 *
	 * <p>取「我参与 + 状态为收集中 + 未过期」的最近一条，聚合项数/人数/估算合计/明细预览。
	 * 无进行中的购物车时返回仅含零值的对象（cart 为 null），不抛异常，便于首页直接渲染空态。
	 *
	 * @param vesselCallId 可选；传入时优先取该靠港计划下的购物车，为空则取全部进行中的最近一条
	 */
	SharedCartSummaryVO getActiveSummary(String tenantId, String userId, String vesselCallId);

	/**
	 * 查询购物车成员。
	 */
	List<SharedCartMember> listMembers(String tenantId, String cartId);

	/**
	 * 设置当前成员在共享购物车中的展示姓名（加入时填写一次，用于配送贴标签）。
	 */
	SharedCartMember updateMemberDisplayName(String tenantId, String userId, String cartId, String displayName);

	/**
	 * 设置成员的明细维护权限（管理端运营操作）。
	 *
	 * <p>收回后该成员只能查看清单：加购/改数量/移除以及导入、历史复用都会被
	 * {@code requireCanEdit} 拒绝。用于处理误报、代报等运营场景。
	 *
	 * @param canEdit 1 可维护，0 只读
	 */
	SharedCartMember updateMemberCanEdit(String tenantId, String cartId, String memberId, String canEdit);

	/**
	 * 查询购物车有效明细（实体）。
	 *
	 * <p>供服务内部计算使用，不补齐商品名与价格；C 端展示请用
	 * {@link #listItemVOs(String, String)}。
	 */
	List<SharedCartItem> listItems(String tenantId, String cartId);

	/**
	 * 查询购物车有效明细（C 端视图）。
	 *
	 * <p>在实体字段之外补齐商品名、规格、图片与金额：单价取 SKU 当前售价，
	 * 行小计按**成员申请量**计算（收集阶段没有核定数量）。商品域不可用时
	 * 价格字段为 null，前端显示「待核价」，绝不能把缺失的金额当成 0。
	 */
	List<SharedCartItemVO> listItemVOs(String tenantId, String cartId);

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
