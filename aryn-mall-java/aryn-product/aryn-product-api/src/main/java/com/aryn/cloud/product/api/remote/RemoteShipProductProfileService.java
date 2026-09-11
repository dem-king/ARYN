package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.entity.ShipSkuProfile;

import java.util.List;

/**
 * 船供商品包装资料远程接口。
 *
 * <p>由 aryn-product-biz 实现，供订单域（共享购物车提交、结算）校验
 * 采购单位、MOQ 和 step_qty。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface RemoteShipProductProfileService {

	/**
	 * 按租户与 SKU ID 集合查询包装资料。
	 * @return 按 skuId 索引的资料列表；无资料的 SKU 不返回
	 */
	List<ShipSkuProfile> getSkuProfiles(String tenantId, List<String> skuIds);

}
