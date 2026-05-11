
package com.aryn.cloud.product.dubbo;

import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.service.IGoodsSkuService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGoodsSkuServiceImpl implements RemoteGoodsSkuService {

	private final IGoodsSkuService goodsSkuService;

	@Override
	public void rollbackStock(List<GoodsSkuStockReqDTO> goodsSkuStockReqDTOList) {
		goodsSkuService.rollbackStockList(goodsSkuStockReqDTOList);
	}

	@Override
	public boolean reduceStock(List<GoodsSkuStockReqDTO> goodsSkuStockReqDTO) {
		return goodsSkuService.reduceStock(goodsSkuStockReqDTO);
	}

	@Override
	public List<GoodsSku> getBySkuIds(List<String> ids) {
		return goodsSkuService.getListByIds(ids);
	}

	@Override
	public List<GoodsSku> getSkuByIds(List<String> ids) {
		return goodsSkuService.getSkuByIds(ids);
	}

}
