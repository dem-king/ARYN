package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格政策 SKU 明细（协议价）。
 *
 * <p>唯一键 (tenant_id, policy_id, sku_id) 不含 del_flag：保存时按唯一键 upsert
 * 复用行（复活 del_flag=0），禁止物理删除。price_mode=1 必填 policyPrice、
 * price_mode=2 必填 policyRate；min_qty 仅作起订量展示提示，不参与计价校验。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "价格政策SKU明细")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "price_policy_item")
public class PricePolicyItem extends Model<PricePolicyItem> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "政策ID")
	private String policyId;

	@Schema(description = "商品SPU")
	private String spuId;

	@Schema(description = "商品SKU")
	private String skuId;

	@Schema(description = "协议固定价（price_mode=1 必填）")
	private BigDecimal policyPrice;

	@Schema(description = "协议折扣率（price_mode=2 必填，如0.9000=9折）")
	private BigDecimal policyRate;

	@Schema(description = "协议起订量（展示提示）")
	private Integer minQty;

	@Schema(description = "租户ID")
	private String tenantId;

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

}
