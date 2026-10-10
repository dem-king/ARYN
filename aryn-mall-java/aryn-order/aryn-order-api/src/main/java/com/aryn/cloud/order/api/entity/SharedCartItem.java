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
 * 共享购物车明细。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_cart_item")
public class SharedCartItem extends Model<SharedCartItem> {

	public static final String ITEM_PENDING = "1";

	public static final String ITEM_CONFIRMED = "2";

	public static final String ITEM_REMOVED = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 共享购物车ID */
	private String cartId;

	/** 添加成员用户ID（订单明细保留贡献者来源） */
	private String userId;

	/**
	 * 归属人姓名快照（接龙代报场景）。
	 *
	 * <p>工作人员替没注册的船员报货时，明细挂在操作者名下，接龙里的人名
	 * 原文落在这里——配送贴标签、按人分装认的是这个名字，不是用户账号。
	 * 空串表示行归属就是 {@code userId} 本人。
	 */
	private String attributedName;

	/** 商品SPU ID */
	private String spuId;

	/** 商品SKU ID */
	private String skuId;

	/** 成员申请数量（采购单位） */
	private Integer requestedQuantity;

	/**
	 * 确认人核定数量（采购单位）。三种取值：
	 * <ul>
	 *   <li>NULL：未核定，下单时按成员申请量采购；</li>
	 *   <li>&gt; 0：确认人改定的采购量；</li>
	 *   <li>0：确认人标记「本次不采」，该行不进整船订单。</li>
	 * </ul>
	 *
	 * <p>成员报多少（{@code requestedQuantity}）与买多少（本字段）分成两列：
	 * 收集期间成员可反复改自己的申请量，确认人在提交前才做最终核定。
	 */
	private Integer approvedQuantity;

	/** 成员备注 */
	private String memberRemark;

	/** 明细状态：1待确认 2已确认 3已移除 */
	private String status;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
