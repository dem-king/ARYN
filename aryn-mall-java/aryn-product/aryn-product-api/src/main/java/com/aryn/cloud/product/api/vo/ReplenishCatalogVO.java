package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 补给单导入模板的商品目录（含截断信息）。
 *
 * <p>为什么要把「总数/是否截断」一并回传，而不是只给一个列表：
 * 单文件导入上限是 2000 行，商品多的租户必然导出不全。
 * 静默截断会让客户以为「我的商品没建档」，因此截断必须能传到客户端，
 * 由前端明确告知「模板仅含前 N 项，共 M 项」。
 *
 * @author aryn
 * @since 2026/9/28
 */
@Data
@Schema(description = "补给单导入模板商品目录")
public class ReplenishCatalogVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "在售商品行（按分类排序，已按上限截断）")
	private List<ReplenishCatalogRowVO> rows;

	@Schema(description = "在售商品行总数（未截断前的真实条数）")
	private Integer totalCount;

	@Schema(description = "是否因超过单文件导入上限被截断")
	private boolean truncated;

}
