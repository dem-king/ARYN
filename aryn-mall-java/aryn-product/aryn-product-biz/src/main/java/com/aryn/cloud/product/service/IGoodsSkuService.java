
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.vo.GoodsSkuDisplayVO;

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

	/**
	 * 批量查询SKU展示信息（商品名/规格名/售价）。
	 *
	 * <p>活动等配置只落库 SKU ID，管理端编辑回显时按 ID 换取展示字段；
	 * 不校验上下架状态，商品下架后仍可回显名称。
	 * @param ids SKU ID 列表
	 * @return SKU 展示信息（查不到的 ID 不返回）
	 */
	List<GoodsSkuDisplayVO> getDisplayByIds(List<String> ids);

}
