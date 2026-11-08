package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SKU 每日价格快照（商品卡「近14天最低价」判定底表）。
 *
 * <p>每日由 {@code skuPriceDailySnapshotJobHandler} 按租户对全部在售 SKU 写一行：
 * sale_price = MIN(标价, 出清生效价, 秒杀价)——即当日「非个性化到手价」；
 * 会员价/新人价是 user-scoped 身份价，无法全局快照，不计入口径。
 * 出清价复用 {@code IGoodsZoneTagService#clearancePrices} 的专区同源口径
 * （SKU 覆盖 + 系统活动最终价 + 倒挂过滤），秒杀价经
 * {@code RemoteCardMarketingService#batchActiveSeckill} 批量拉取、失败降级仅本地价。
 *
 * <p>**口径即合规证据**：priceTagText 一旦对外展示，本表的取价口径就是价格合规
 * 的举证依据，口径变更必须走需求评审，禁止 Job 顺手修改（表注释与需求文档双重固化）。
 *
 * <p>同一 (租户, SKU, 快照日) 唯一，Job 重跑覆盖；纯快照表无逻辑删除，
 * 历史行即判定窗口（万级 SKU × 365 行/年量级可控）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "SKU每日价格快照")
@TableName("goods_sku_price_daily")
public class GoodsSkuPriceDaily implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	@TableId
	private String id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "快照日")
	private LocalDate statDate;

	@Schema(description = "非个性化到手价=MIN(标价,出清生效价,秒杀价)；会员/新人价不计入（user-scoped）")
	private BigDecimal salePrice;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}
