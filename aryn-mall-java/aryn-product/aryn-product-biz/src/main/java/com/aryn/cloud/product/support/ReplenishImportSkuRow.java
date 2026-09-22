package com.aryn.cloud.product.support;

import com.aryn.cloud.product.api.entity.GoodsSku;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 补给单导入匹配用的 SKU 行（SKU + SPU + 船供包装资料一次联表取出）。
 *
 * <p>刻意不返回下架商品之外的条件：导入报告要能区分「未匹配」与「已下架」，
 * 若把下架数据过滤掉，用户永远看不到真实原因（见
 * {@code RemoteReplenishImportMatchService} 的注释）。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
public class ReplenishImportSkuRow {

	private String skuId;

	private String spuId;

	private String name;

	/** SPU 状态：0下架 1上架 */
	private String spuStatus;

	/** SKU 状态：0正常 1下架 */
	private String skuStatus;

	private BigDecimal salesPrice;

	private Integer stock;

	private String purchaseUnit;

	private Integer moq;

	private Integer stepQty;

	private List<GoodsSku.Specs> specsArr;

}
