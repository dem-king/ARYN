package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 共享购物车成员。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_cart_member")
public class SharedCartMember extends Model<SharedCartMember> {

	public static final String ROLE_OWNER = "1";

	public static final String ROLE_MEMBER = "2";

	public static final String ROLE_CONFIRMATOR = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 共享购物车ID */
	private String cartId;

	/** 成员用户ID */
	private String userId;

	/** 成员角色：1发起人 2成员 3确认人 */
	private String memberRole;

	/** 可编辑自己的明细：1是 0否 */
	private String canEdit;

	/** 可确认提交订单：1是 0否 */
	private String canConfirm;

	/** 成员展示姓名（加入时填写一次，用于配送贴标签；默认取商城收货人姓名，可自行修改） */
	private String displayName;

	/** 加入时间 */
	private LocalDateTime joinedTime;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
