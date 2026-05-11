
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;

import java.util.List;

/**
 * 商品sku
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
public interface IGoodsSkuService extends IService<GoodsSku> {

	List<GoodsSku> getListByIds(List<String> ids);

	/**
	 * 回滚库存
	 * @param goodsSkuStockRqDTOList
	 * @return
	 */
	void rollbackStockList(List<GoodsSkuStockReqDTO> goodsSkuStockRqDTOList);

	/**
	 * 扣减库存
	 * @param goodsSkuStockRqDTO
	 * @return boolean true false
	 */
	Boolean reduceStock(List<GoodsSkuStockReqDTO> goodsSkuStockRqDTO);

	/**
	 * 批量查询sku附带规格信息
	 * @param ids
	 * @return
	 */
	List<GoodsSku> getSkuByIds(List<String> ids);

}
