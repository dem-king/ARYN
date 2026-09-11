package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 船舶成员（商城用户与船舶的绑定关系）。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_member")
public class VesselMember extends AbstractVesselEntity {

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

}
