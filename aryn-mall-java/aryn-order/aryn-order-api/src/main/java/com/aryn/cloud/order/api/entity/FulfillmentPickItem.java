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
 * 拣货明细（扫码校验 SKU、数量；短装记录原因）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_pick_item")
public class FulfillmentPickItem extends Model<FulfillmentPickItem> {

	public static final String PICK_PENDING = "1";

	public static final String PICK_DONE = "2";

	public static final String PICK_SHORT = "3";

	public static final String PICK_REPLACED = "4";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 拣货波次ID */
	private String waveId;

	/** 订单ID */
	private String orderId;

	/** 订单明细ID */
	private String orderItemId;

	/** 商品SKU ID */
	private String skuId;

	/** 商品名称快照 */
	private String spuName;

	/** 规格名称快照 */
	private String skuName;

	/** 条码快照（扫码校验用） */
	private String skuBarcode;

	/** 应拣数量（采购单位） */
	private Integer requiredQuantity;

	/** 实拣数量 */
	private Integer pickedQuantity;

	/** 短装数量 */
	private Integer shortQuantity;

	/** 短装原因编码 */
	private String shortReasonCode;

	/** 短装原因说明 */
	private String shortReasonDesc;

	/** 拣货状态：1待拣 2已拣 3短装 4替代 */
	private String pickStatus;

	/** 替代商品SKU ID */
	private String substitutedSkuId;

	/** 扫码确认时间 */
	private LocalDateTime scannedTime;

	/** 拣货员ID */
	private String pickerId;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
