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
 * 共享购物车（同船多海员合并采购）。
 *
 * <p>状态机：1草稿 2收集中 3待确认 4已提交 5已关闭。
 * 创建后绑定船舶与靠港计划，不可变更；已提交/已关闭不可编辑。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_cart")
public class SharedCart extends Model<SharedCart> {

	public static final String STATUS_DRAFT = "1";

	public static final String STATUS_COLLECTING = "2";

	public static final String STATUS_WAITING_CONFIRM = "3";

	public static final String STATUS_SUBMITTED = "4";

	public static final String STATUS_CLOSED = "5";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 共享购物车编号 */
	private String cartNo;

	/** 船舶ID（绑定后不可变更） */
	private String vesselId;

	/** 靠港计划ID（绑定后不可变更） */
	private String vesselCallId;

	/** 发起人用户ID */
	private String ownerUserId;

	/** 确认人用户ID（默认发起人） */
	private String confirmerUserId;

	/** 状态：1草稿 2收集中 3待确认 4已提交 5已关闭 */
	private String status;

	/** 收集截止时间 */
	private LocalDateTime expiresAt;

	/** 提交生成的订单ID */
	private String submitOrderId;

	/** 提交时间 */
	private LocalDateTime submittedTime;

	/** 备注 */
	private String remark;

	/** 乐观锁版本号 */
	private Integer version;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
