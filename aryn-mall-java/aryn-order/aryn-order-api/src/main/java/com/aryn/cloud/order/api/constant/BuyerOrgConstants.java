package com.aryn.cloud.order.api.constant;

/**
 * 采购主体（工厂/企业/园区餐厅）口径常量。
 *
 * <p>与 {@code buyer_org} 四张表的字段取值一一对应；前端与管理端只认这些字面量，
 * 不要在业务代码里散落魔法字符串。
 *
 * @author aryn
 * @since 2026/11/12
 */
public final class BuyerOrgConstants {

	private BuyerOrgConstants() {
	}

	/** 主体类型：企业 */
	public static final String ORG_TYPE_ENTERPRISE = "1";

	/** 主体类型：工厂 */
	public static final String ORG_TYPE_FACTORY = "2";

	/** 主体类型：园区餐厅 */
	public static final String ORG_TYPE_RESTAURANT = "3";

	/** 成员角色：厂长/管理员 */
	public static final String ROLE_OWNER = "1";

	/** 成员角色：采购员 */
	public static final String ROLE_BUYER = "2";

	/** 成员角色：业务员 */
	public static final String ROLE_SALES = "3";

	/** 成员角色：查看者 */
	public static final String ROLE_VIEWER = "4";

	/** 审核状态：待审核（允许下企业单，但不享首单） */
	public static final String AUDIT_PENDING = "0";

	/** 审核状态：已通过 */
	public static final String AUDIT_APPROVED = "1";

	/** 审核状态：已驳回 */
	public static final String AUDIT_REJECTED = "2";

	/** 审核状态：已停用 */
	public static final String AUDIT_DISABLED = "3";

	/** 主体状态：正常 */
	public static final String STATUS_NORMAL = "1";

	/** 主体状态：停用 */
	public static final String STATUS_DISABLED = "0";

	/** 成员状态：正常 */
	public static final String MEMBER_STATUS_NORMAL = "1";

	/** 成员状态：已移除 */
	public static final String MEMBER_STATUS_REMOVED = "0";

	/** 申请类型：创建主体 */
	public static final String APPLY_TYPE_CREATE = "1";

	/** 申请类型：加入已有主体 */
	public static final String APPLY_TYPE_JOIN = "2";

	/** 申请状态：待审核 */
	public static final String APPLY_STATUS_PENDING = "1";

	/** 申请状态：已通过 */
	public static final String APPLY_STATUS_APPROVED = "2";

	/** 申请状态：已驳回 */
	public static final String APPLY_STATUS_REJECTED = "3";

	/** 申请状态：已取消 */
	public static final String APPLY_STATUS_CANCELLED = "4";

	/** 绑定来源：申请审核 */
	public static final String BIND_SOURCE_APPLY_AUDIT = "APPLY_AUDIT";

	/** 绑定来源：邀请码 */
	public static final String BIND_SOURCE_INVITE_CODE = "INVITE_CODE";

	/** 绑定来源：成员添加 */
	public static final String BIND_SOURCE_MEMBER_ADD = "MEMBER_ADD";

	/** 绑定来源：运营代建 */
	public static final String BIND_SOURCE_ADMIN_ADD = "ADMIN_ADD";

	/** 绑定来源：分享购物车 */
	public static final String BIND_SOURCE_SHARE_CART = "SHARE_CART";

	/** 绑定来源：人工补录 */
	public static final String BIND_SOURCE_MANUAL = "MANUAL";

	/** 绑定来源：C 端自助创建（免审核，落库即通过） */
	public static final String BIND_SOURCE_SELF_SERVICE = "SELF_SERVICE";

	/** 邀请码有效期默认（小时） */
	public static final int INVITE_CODE_DEFAULT_EXPIRE_HOURS = 168;

	/** 邀请码有效期上限（小时） */
	public static final int INVITE_CODE_MAX_EXPIRE_HOURS = 24 * 30;

	/** 邀请码状态：有效 */
	public static final String INVITE_CODE_STATUS_ACTIVE = "1";

	/** 邀请码状态：已失效 */
	public static final String INVITE_CODE_STATUS_INACTIVE = "0";

	/** 主体可用于下单/建车的审核态：待审核允许（不享首单）、已通过 */
	public static boolean usableAuditStatus(String auditStatus) {
		return AUDIT_PENDING.equals(auditStatus) || AUDIT_APPROVED.equals(auditStatus);
	}

}
