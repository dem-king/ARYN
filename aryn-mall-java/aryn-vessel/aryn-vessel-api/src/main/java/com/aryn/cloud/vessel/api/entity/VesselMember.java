package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 船舶成员（商城用户与船舶的绑定关系）。
 *
 * <p>角色取值见下方常量。注意 {@link #ROLE_SALES}（业务员）是本次新增：
 * 公司销售用现有 C 端账号登录，靠「是这条船的成员」获得操作权，
 * 可添加成员、可代船员下单。
 *
 * <p><b>可添加成员的角色 = 发起人 / 采购确认人 / 业务员</b>，普通船员不可添加。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_member")
public class VesselMember extends AbstractVesselEntity {

	/** 角色：发起人 / 船长授权 */
	public static final String ROLE_OWNER = "1";

	/** 角色：普通船员 */
	public static final String ROLE_CREW = "2";

	/** 角色：采购确认人 */
	public static final String ROLE_CONFIRMER = "3";

	/** 角色：业务员（公司销售，可添加成员、可代船员下单） */
	public static final String ROLE_SALES = "4";

	/** 在船状态：在船 */
	public static final String STATUS_ONBOARD = "1";

	/** 船舶ID */
	private String vesselId;

	/** 商城用户ID */
	private String userId;

	/** 成员角色：1发起人/船长授权 2普通船员 3采购确认人 */
	private String memberRole;

	/** 可编辑共享购物车：1是 0否 */
	private String canEdit;

	/** 可确认提交订单：1是 0否 */
	private String canConfirm;

	/** 在船状态：1在船 0离船 */
	private String status;

	/** 加入时间 */
	private LocalDateTime joinTime;

	/** 备注 */
	private String remark;

	/**
	 * 绑定来源，取值见 {@link com.aryn.cloud.vessel.api.constant.VesselMemberBindSource}。
	 *
	 * <p>DB 默认 {@code MANUAL}（历史数据兜底）；业务写入必须显式标记真实来源，
	 * 否则运营无法在成员列表按来源识别并回收「分享链接自动补建」这类低信任关系。
	 */
	private String bindSource;

	/** 绑定补充原因（审核偏离理由、解绑说明等），与来源一起构成可追溯证据 */
	private String bindReason;

}
