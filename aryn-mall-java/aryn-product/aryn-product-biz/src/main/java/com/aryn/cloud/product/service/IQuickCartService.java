package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.vo.QuickCartInfoVO;

/**
 * 快捷加购服务。
 *
 * <p>面向商品列表/推荐位的「快捷加购」按钮：列表接口不返回 SKU 明细，
 * 点击时按需查询一次单个商品的加购可行性（SKU、库存、MOQ/步长）。
 *
 * @author aryn
 * @since 2026/9/21
 */
public interface IQuickCartService {

	/**
	 * 查询商品的快捷加购信息。
	 *
	 * @param spuId 商品 SPU ID
	 * @return 加购信息；商品不存在或已下架返回 null
	 */
	QuickCartInfoVO getQuickCartInfo(String spuId);

}
