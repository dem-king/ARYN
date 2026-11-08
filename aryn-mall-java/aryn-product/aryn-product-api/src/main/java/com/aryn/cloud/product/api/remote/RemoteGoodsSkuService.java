
package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;

import java.util.List;

/**
 * @author 雨滴kian
 */
public interface RemoteGoodsSkuService {

	/**
	 * 批量回滚库存
	 * @param goodsSkuStockReqDTOList
	 * @return void
	 */
	void rollbackStock(List<GoodsSkuStockReqDTO> goodsSkuStockReqDTOList);

	/**
	 * 扣减库存
	 * @param goodsSkuStockReqDTO
	 * @return Boolean
	 */

	boolean reduceStock(List<GoodsSkuStockReqDTO> goodsSkuStockReqDTO);

	/**
	 * 通过id批量查询
	 * @param ids
	 * @return
	 */
	List<GoodsSku> getBySkuIds(List<String> ids);

	/**
	 * 通过id批量查询SKU 附带规格信息
	 * @param ids
	 * @return
	 */
	List<GoodsSku> getSkuByIds(List<String> ids);

	/**
	 * 按 SPU 批量查询 SKU（商品卡营销标签的 SPU→SKU 解析用，仅 id/spuId 精简列）。
	 * @param spuIds SPU ID 集合
	 * @return SKU 精简列表；空入参返回空列表
	 */
	List<GoodsSku> listBySpuIds(List<String> spuIds);

}
