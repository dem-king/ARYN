package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * SPU 船供资料（与 goods_spu 一对一扩展）。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ship_goods_profile")
public class ShipGoodsProfile extends Model<ShipGoodsProfile> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 商品SPU ID */
	private String spuId;

	/** 销售范围：1仅个人购买 2仅船供采购 3个人和船供均可 */
	private String saleScope;

	/** IMPA 编码 */
	private String impaCode;

	/** ISSA 编码 */
	private String issaCode;

	/** 内部物料编码 */
	private String internalItemCode;

	/** 条形码 */
	private String barcode;

	/** 英文品名 */
	private String nameEn;

	/** 搜索别名（逗号分隔） */
	private String searchAliases;

	/** 储存条件：1常温 2冷藏 3冷冻 4危险品 5其他 */
	private String storageType;

	/** 保质期天数 */
	private Integer shelfLifeDays;

	/** 温度要求说明 */
	private String temperatureRequirement;

	/** 船供说明 */
	private String shipSupplyRemark;

	/** 资料完整度（0-100） */
	private Integer publishCompleteness;

	private String tenantId;

	@TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
	private String createBy;

	@TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.UPDATE)
	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
	private String delFlag;

}
