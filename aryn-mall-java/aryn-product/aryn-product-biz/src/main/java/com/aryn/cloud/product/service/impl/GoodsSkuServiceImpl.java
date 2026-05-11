
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.service.IGoodsSkuService;
import com.aryn.cloud.product.service.IGoodsSpuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品sku
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Service
@RequiredArgsConstructor
public class GoodsSkuServiceImpl extends ServiceImpl<GoodsSkuMapper, GoodsSku> implements IGoodsSkuService {

	private final IGoodsSpuService goodsSpuService;

	@Override
	public List<GoodsSku> getListByIds(List<String> ids) {
		return baseMapper.selectListByIds(ids);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void rollbackStockList(List<GoodsSkuStockReqDTO> goodsSkuStockRqDTOList) {
		goodsSkuStockRqDTOList.forEach(goodsSkuStockRqDTO -> {
			baseMapper.update(new GoodsSku(),
					Wrappers.<GoodsSku>lambdaUpdate()
						.eq(GoodsSku::getId, goodsSkuStockRqDTO.getSkuId())
						.setSql(" stock = stock + " + goodsSkuStockRqDTO.getStockNum()));
		});
		Map<String, Integer> result = goodsSkuStockRqDTOList.stream()
			.collect(Collectors.toMap(GoodsSkuStockReqDTO::getSpuId, GoodsSkuStockReqDTO::getStockNum, Integer::sum)); // 如果遇到相同的key，则将count相加
		goodsSpuService.rollbackStock(result);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean reduceStock(List<GoodsSkuStockReqDTO> goodsSkuStockRqDTO) {
		for (GoodsSkuStockReqDTO goodsSkuStockReqDTO : goodsSkuStockRqDTO) {
			if (baseMapper.update(new GoodsSku(),
					Wrappers.<GoodsSku>lambdaUpdate()
						.eq(GoodsSku::getId, goodsSkuStockReqDTO.getSkuId())
						.gt(GoodsSku::getStock, 0)
						.setSql(" stock = stock - " + goodsSkuStockReqDTO.getStockNum())) <= 0) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
						MallErrorCodeEnum.ERROR_60008.getMsg());
			}
		}
		Map<String, Integer> result = goodsSkuStockRqDTO.stream()
			.collect(Collectors.toMap(GoodsSkuStockReqDTO::getSpuId, GoodsSkuStockReqDTO::getStockNum, Integer::sum)); // 如果遇到相同的key，则将count相加
		goodsSpuService.reduceStock(result);
		return Boolean.TRUE;
	}

	@Override
	public List<GoodsSku> getSkuByIds(List<String> ids) {
		return baseMapper.selectSkuByIds(ids);
	}

}
