package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 船舶邀请码。
 *
 * <p>由已在船且具备成员管理权限的角色（发起人 / 采购确认人 / 业务员）生成，
 * 供同事自助加入。相比「业务员现场手动添加」，邀请码适用于业务员不在场的场景。
 *
 * <p>{@link #ownerUserId} 刻意不叫 createdBy —— 审计字段 createBy 由框架自动填充，
 * 两者含义不同，同名会造成混淆。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_invite_code")
public class VesselInviteCode extends AbstractVesselEntity {

	/** 状态：有效 */
	public static final String STATUS_ACTIVE = "1";

	/** 状态：已撤销 */
	public static final String STATUS_REVOKED = "0";

	/** 船舶ID */
	private String vesselId;

	/** 邀请码（6 位大写字母数字，已去除易混字符） */
	private String code;

	/** 生成人商城用户ID */
	private String ownerUserId;

	/** 最大使用次数，0 表示不限 */
	private Integer maxUses;

	/** 已使用次数 */
	private Integer usedCount;

	/** 过期时间 */
	private LocalDateTime expiresAt;

	/** 状态：1有效 0已撤销 */
	private String status;

	/** 备注 */
	private String remark;

}
