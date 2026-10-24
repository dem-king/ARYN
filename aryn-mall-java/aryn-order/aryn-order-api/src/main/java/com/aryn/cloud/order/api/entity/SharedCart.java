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
 * 共享购物车（多成员合并采购：船供整车或岸上企业）。
 *
 * <p>状态机：1草稿 2收集中 3待确认 4已提交 5已关闭 6已完成（订单签收后归档）。
 * 船供车（cartType=1）创建后绑定船舶与靠港计划，不可变更；岸上车（cartType=2）
 * 绑定发起人地址簿中的一个收货地址（港口附近工厂等无船客户）。已提交/已关闭不可编辑。
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

	/** 已完成：订单签收（送达）后归档 */
	public static final String STATUS_COMPLETED = "6";

	/** 车类型：船供整车采购（默认，存量数据同此） */
	public static final String CART_TYPE_SHIP = "1";

	/** 车类型：岸上企业采购（收货地址配送，无船舶与靠港） */
	public static final String CART_TYPE_SHORE = "2";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 共享购物车编号 */
	private String cartNo;

	/** 车类型：1船供整车（默认）；2岸上企业 */
	private String cartType;

	/** 船舶ID（船供车绑定后不可变更；岸上车为空） */
	private String vesselId;

	/** 靠港计划ID（船供车绑定后不可变更；岸上车为空） */
	private String vesselCallId;

	/** 岸上车收货地址ID（地址簿；船供车为空） */
	private String userAddressId;

	/**
	 * 岸上车收货地址快照（创建时拼接：「省市区+详址 收货人 电话」）。
	 *
	 * <p>展示用：列表/详情/摘要不回查地址簿；提交时订单按 userAddressId 重取
	 * 地址簿落收货人，地址被删由提交校验拦截。
	 */
	private String addressSnapshot;

	/** 发起人用户ID */
	private String ownerUserId;

	/** 确认人用户ID（默认发起人） */
	private String confirmerUserId;

	/** 状态：1草稿 2收集中 3待确认 4已提交 5已关闭 6已完成（送达归档） */
	private String status;

	/** 收集截止时间 */
	private LocalDateTime expiresAt;

	/** 提交生成的订单ID */
	private String submitOrderId;

	/** 提交时间 */
	private LocalDateTime submittedTime;

	/** 送达归档时间（订单签收后写入） */
	private LocalDateTime completedTime;

	/** 分享令牌（转发卡片携带，随购物车过期失效） */
	private String shareToken;

	/**
	 * 非持久化标记：本次创建请求命中了该船已有的「收集中」购物车，
	 * 返回的是被复用的实例而非新建。前端据此提示「已为你打开进行中的采购」。
	 */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private Boolean adoptedExisting;

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
