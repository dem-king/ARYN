
package com.aryn.cloud.product.api.util;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;

/**
 * 商品实体在 C 端出参边界的成本价脱敏。
 *
 * <p>成本价（{@code cost_price}）是内部经营数据：管理端表单/SKU 表格要读写它，
 * 但不应随 C 端接口下发。历史上 /app/goodsspu 系列、收藏列表、购物车都把实体
 * 原样序列化返回，小程序响应体里能直接看到成本价——这与 {@code QuickCartSkuVO}
 * 注释里「成本价、租户、逻辑删除等内部字段不下发到 C 端」的既定口径相悖。
 *
 * <p>使用约定：凡是把 {@link GoodsSpu}/{@link GoodsSku} 返回给 C 端 HTTP 接口的
 * 出口（app controller 或其 service 方法），返回前必须经过本类脱敏；管理端
 * （adminPage/selectSpuById 等）与 Dubbo 内部调用不受影响。
 *
 * <p>配合实体 costPrice 字段上的 {@code @JsonInclude(NON_NULL)}，置空后该字段
 * 从 JSON 中整个消失。salesPrice/originalPrice 是 C 端展示所需，一律不动。
 *
 * @author system
 * @since 2026/9/25
 */
public final class GoodsCostPriceMasker {

	private GoodsCostPriceMasker() {
	}

	/**
	 * 就地清空 SPU 及其嵌套 SKU 的成本价，返回同一实例便于链式使用。
	 * @param goodsSpu 待脱敏的 SPU，可为 null
	 * @return 同一实例（脱敏后）
	 */
	public static GoodsSpu maskSpu(GoodsSpu goodsSpu) {
		if (goodsSpu == null) {
			return null;
		}
		goodsSpu.setCostPrice(null);
		if (goodsSpu.getGoodsSkus() != null) {
			goodsSpu.getGoodsSkus().forEach(GoodsCostPriceMasker::maskSku);
		}
		return goodsSpu;
	}

	/**
	 * 就地清空 SKU 的成本价，返回同一实例便于链式使用。
	 * @param goodsSku 待脱敏的 SKU，可为 null
	 * @return 同一实例（脱敏后）
	 */
	public static GoodsSku maskSku(GoodsSku goodsSku) {
		if (goodsSku == null) {
			return null;
		}
		goodsSku.setCostPrice(null);
		return goodsSku;
	}

}
