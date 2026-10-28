
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.product.api.entity.GoodsSku;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车
 *
 * @author 雨滴kian
 * @since 2022/3/17 10:52
 */
@Data
@Schema(description = "购物车")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "shopping_cart")
public class ShoppingCart extends Model<ShoppingCart> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "skuId")
	private String skuId;

	@Schema(description = "加入数量")
	private Integer quantity;

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "销售价格（元）")
	private BigDecimal salesPrice;

	@Schema(description = "商品图")
	private String picUrl;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "规格信息")
	private String specsInfo;

	/** 加购船舶ID快照 */
	private String vesselId;

	/** 加购靠港计划ID快照（防串船分组键） */
	private String vesselCallId;

	/** 加购购买场景快照：1个人 2船供 */
	private String purchaseScene;

	@TableField(exist = false)
	private GoodsSku goodsSku;

	/** 商品卖点（SPU 子标题）：非购物车表字段，列表出参时按 spuId 批量补齐 */
	@TableField(exist = false)
	private String subTitle;

	/**
	 * 行归属靠港的港口名称：列表出参按 vesselCallId 批量回填（展示快照，已结束靠港也有值）。
	 * C 端购物车分组头用它替代裸靠港 ID，用户才能看出「这批货是哪次靠港加购的」。
	 */
	@TableField(exist = false)
	private String vesselCallPortName;

	/** 行归属靠港的船舶名称：跨船遗留行只有港口名分不出是哪艘船，一并回填 */
	@TableField(exist = false)
	private String vesselCallVesselName;

	/**
	 * 行归属靠港是否仍可下单：false=该靠港已结束/已取消。
	 * 前端据此区分「已结束靠港的遗留行」（给转入入口）与「另一条进行中靠港的行」（给切换提示）。
	 */
	@TableField(exist = false)
	private Boolean vesselCallOrderable;

}
