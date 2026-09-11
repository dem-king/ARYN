package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU 包装资料（与 goods_sku 一对一扩展）。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ship_sku_profile")
public class ShipSkuProfile extends Model<ShipSkuProfile> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 商品SPU ID */
	private String spuId;

	/** 商品SKU ID */
	private String skuId;

	/** 基本单位（零售单位） */
	private String baseUnit;

	/** 采购单位 */
	private String purchaseUnit;

	/** 采购单位换算基本单位倍率 */
	private BigDecimal conversionRate;

	/** 箱规（中文包装规格） */
	private String packageSpec;

	/** 箱规（英文包装规格） */
	private String packageSpecEn;

	/** 最小起订量 */
	private Integer moq;

	/** 数量步长 */
	private Integer stepQty;

	/** 毛重（kg） */
	private BigDecimal grossWeight;

	/** 体积（m³） */
	private BigDecimal volume;

	/** 库存预警线 */
	private Integer stockWarningLine;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
