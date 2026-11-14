package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.BuyerOrgApplyAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgBatchAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgInviteCodeDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgMemberDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgSaveDTO;
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.BuyerOrgApply;
import com.aryn.cloud.order.api.entity.BuyerOrgInviteCode;
import com.aryn.cloud.order.api.vo.BuyerOrgApplyVO;
import com.aryn.cloud.order.api.vo.BuyerOrgMemberVO;
import com.aryn.cloud.order.api.vo.BuyerOrgVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 采购主体（工厂/企业/园区餐厅）服务。
 *
 * <p>三条入驻路径：
 * <ol>
 *   <li>搜索已有主体 → 提交加入申请 → 厂长/运营审核；</li>
 *   <li>邀请码/分享卡 → 免审直接加入；</li>
 *   <li>搜不到 → 创建主体（同步落待审核主体，可先下企业单，不享首单）→ 运营审核。</li>
 * </ol>
 *
 * <p>首单权益按主体算一次，不按成员账号重复算；资格判定在 P1 引擎实现。
 * P0 只负责主体、成员、申请、邀请码与岸上车/订单的主体快照。
 *
 * @author aryn
 * @since 2026/11/12
 */
public interface IBuyerOrgService {

	/**
	 * C 端提交创建/加入申请。
	 *
	 * <p>创建申请会同步创建待审核主体与厂长成员行；同一信用代码已存在时
	 * 拒绝创建并提示搜索加入，避免一厂多主体导致首单重复。
	 */
	BuyerOrgApply apply(String tenantId, String userId, BuyerOrgApplyDTO dto);

	/**
	 * 我绑定的主体列表（含待审核/驳回/停用状态，前端据此展示「审核中」）。
	 */
	List<BuyerOrgVO> listMyOrgs(String tenantId, String userId);

	/**
	 * 搜索已审核主体（名称/信用代码），信用代码脱敏；不返回完整证照信息。
	 */
	List<BuyerOrgVO> search(String tenantId, String userId, String keyword);

	/**
	 * 我的申请单（按时间倒序）。
	 */
	List<BuyerOrgApplyVO> listMyApplies(String tenantId, String userId);

	/**
	 * C 端主体详情（仅成员可见）。
	 */
	BuyerOrgVO getDetail(String tenantId, String userId, String orgId);

	/**
	 * 凭邀请码加入主体（免审）。
	 */
	BuyerOrgMemberVO joinByInviteCode(String tenantId, String userId, String code);

	/**
	 * 主体成员列表（仅成员可见）。
	 */
	List<BuyerOrgMemberVO> listMembers(String tenantId, String userId, String orgId);

	/**
	 * 生成邀请码（厂长/管理员）。同主体旧有效码自动失效，只保留一个。
	 */
	BuyerOrgInviteCode createInviteCode(String tenantId, String userId, String orgId, BuyerOrgInviteCodeDTO dto);

	/**
	 * 非厂长成员主动退出主体。
	 */
	void leave(String tenantId, String userId, String orgId);

	/**
	 * 厂长移除成员。
	 */
	void removeMember(String tenantId, String userId, String orgId, String memberUserId);

	/**
	 * 管理端分页。
	 */
	IPage<BuyerOrg> adminPage(Page<BuyerOrg> page, String tenantId, String orgName, String creditCode,
			String orgType, String auditStatus, String status);

	/**
	 * 管理端详情。
	 */
	BuyerOrg adminGet(String tenantId, String orgId);

	/**
	 * 管理端代建主体（默认审核通过）。
	 */
	BuyerOrg adminCreate(String tenantId, String operator, BuyerOrgSaveDTO dto);

	/**
	 * 管理端修改主体资料（不改变审核态）。
	 */
	void adminUpdate(String tenantId, String operator, String orgId, BuyerOrgSaveDTO dto);

	/**
	 * 管理端审核主体：通过 / 驳回 / 停用。
	 */
	void adminAudit(String tenantId, String operator, String orgId, BuyerOrgAuditDTO dto);

	/**
	 * 管理端批量审核 / 批量停用主体；任一条不合法整体回滚。
	 *
	 * @return 实际处理条数
	 */
	int adminBatchAudit(String tenantId, String operator, BuyerOrgBatchAuditDTO dto);

	/**
	 * 管理端查看成员。
	 */
	List<BuyerOrgMemberVO> adminListMembers(String tenantId, String orgId);

	/**
	 * 管理端添加/调整成员。
	 */
	BuyerOrgMemberVO adminAddMember(String tenantId, String operator, String orgId, BuyerOrgMemberDTO dto);

	/**
	 * 管理端移除成员。
	 */
	void adminRemoveMember(String tenantId, String operator, String orgId, String memberUserId);

	/**
	 * 管理端申请单分页（待审核优先）。
	 */
	IPage<BuyerOrgApplyVO> adminApplyPage(Page<BuyerOrgApply> page, String tenantId, String status,
			String orgName, String applyType);

	/**
	 * 管理端审核申请单：通过时建/放行主体并补成员，驳回时同步驳回主体。
	 */
	void adminAuditApply(String tenantId, String operator, String applyId, BuyerOrgApplyAuditDTO dto);

	/**
	 * 岸上车创建/提交时校验主体可用：存在、同租户、正常、审核中或已通过、当前用户是有效成员。
	 *
	 * @return 主体实体（名称/信用代码用于快照）
	 */
	BuyerOrg requireUsableForCart(String tenantId, String userId, String buyerOrgId);

	/**
	 * 订单落主体快照时校验：主体存在且未停用、当前下单用户是有效成员。
	 *
	 * <p>不要求可编辑/可确认：确认人可能是厂长，也可能只是被授权确认的采购员。
	 */
	BuyerOrg requireForOrder(String tenantId, String userId, String buyerOrgId);

	/**
	 * 按租户 + 主体ID取有效主体（不过滤审核态），供展示审核状态使用；不存在返回 null。
	 */
	BuyerOrg getOrg(String tenantId, String buyerOrgId);

}
