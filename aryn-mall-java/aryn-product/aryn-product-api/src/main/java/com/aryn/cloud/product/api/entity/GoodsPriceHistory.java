package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品价格变更历史：用于「先涨后降」可信度判定与专区价格审计。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "商品价格变更历史")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("goods_price_history")
public class GoodsPriceHistory extends Model<GoodsPriceHistory> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID（SPU级变更可空）")
	private String skuId;

	@Schema(description = "价格类型：sales/original/clearance/member_fixed/member_rate")
	private String priceType;

	@Schema(description = "变更前值")
	private BigDecimal oldValue;

	@Schema(description = "变更后值")
	private BigDecimal newValue;

	@Schema(description = "来源：product_save/tag_save/migration/import")
	private String changeSource;

	@Schema(description = "变更说明")
	private String changeReason;

	@Schema(description = "操作人")
	private String operator;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
