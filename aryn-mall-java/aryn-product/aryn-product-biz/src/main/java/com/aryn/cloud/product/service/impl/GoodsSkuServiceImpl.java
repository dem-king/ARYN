
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		List<GoodsSkuStockReqDTO> requests = normalizeRequests(goodsSkuStockRqDTOList);
		List<GoodsSkuStockReqDTO> activeRequests = new ArrayList<>();
		requests.forEach(goodsSkuStockRqDTO -> {
			int affectedRows = baseMapper.update(new GoodsSku(),
					Wrappers.<GoodsSku>lambdaUpdate()
						.eq(GoodsSku::getId, goodsSkuStockRqDTO.getSkuId())
						.eq(GoodsSku::getSpuId, goodsSkuStockRqDTO.getSpuId())
						.setSql("stock = stock + " + goodsSkuStockRqDTO.getStockNum())
						.setSql("version = version + 1"));
			if (affectedRows > 0) {
				activeRequests.add(goodsSkuStockRqDTO);
				return;
			}
			if (baseMapper.restoreDeletedStock(goodsSkuStockRqDTO.getSkuId(), goodsSkuStockRqDTO.getSpuId(),
					goodsSkuStockRqDTO.getStockNum()) <= 0) {
				throw new ArynBusinessException("回滚SKU库存失败");
			}
		});
		if (!activeRequests.isEmpty()) {
			Map<String, Integer> result = aggregateSpuQuantity(activeRequests);
			goodsSpuService.rollbackStock(result);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean reduceStock(List<GoodsSkuStockReqDTO> goodsSkuStockRqDTO) {
		List<GoodsSkuStockReqDTO> requests = normalizeRequests(goodsSkuStockRqDTO);
		for (GoodsSkuStockReqDTO goodsSkuStockReqDTO : requests) {
			if (baseMapper.update(new GoodsSku(),
					Wrappers.<GoodsSku>lambdaUpdate()
						.eq(GoodsSku::getId, goodsSkuStockReqDTO.getSkuId())
						.eq(GoodsSku::getSpuId, goodsSkuStockReqDTO.getSpuId())
						.ge(GoodsSku::getStock, goodsSkuStockReqDTO.getStockNum())
						.setSql("stock = stock - " + goodsSkuStockReqDTO.getStockNum())
						.setSql("version = version + 1")) <= 0) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
						MallErrorCodeEnum.ERROR_60008.getMsg());
			}
		}
		Map<String, Integer> result = aggregateSpuQuantity(requests);
		goodsSpuService.reduceStock(result);
		return Boolean.TRUE;
	}

	private List<GoodsSkuStockReqDTO> normalizeRequests(List<GoodsSkuStockReqDTO> requests) {
		if (CollectionUtils.isEmpty(requests)) {
			throw new ArynBusinessException("库存变更明细不能为空");
		}
		Map<String, GoodsSkuStockReqDTO> normalized = new LinkedHashMap<>();
		for (GoodsSkuStockReqDTO request : requests) {
			if (request == null || !StringUtils.hasText(request.getSkuId()) || !StringUtils.hasText(request.getSpuId())
					|| request.getStockNum() == null || request.getStockNum() <= 0) {
				throw new ArynBusinessException("库存变更参数不合法");
			}
			normalized.compute(request.getSkuId(), (skuId, existing) -> {
				if (existing == null) {
					GoodsSkuStockReqDTO target = new GoodsSkuStockReqDTO();
					target.setSkuId(request.getSkuId());
					target.setSpuId(request.getSpuId());
					target.setStockNum(request.getStockNum());
					return target;
				}
				if (!existing.getSpuId().equals(request.getSpuId())) {
					throw new ArynBusinessException("SKU与SPU关系不一致");
				}
				existing.setStockNum(Math.addExact(existing.getStockNum(), request.getStockNum()));
				return existing;
			});
		}
		return List.copyOf(normalized.values());
	}

	private Map<String, Integer> aggregateSpuQuantity(List<GoodsSkuStockReqDTO> requests) {
		Map<String, Integer> result = new LinkedHashMap<>();
		for (GoodsSkuStockReqDTO request : requests) {
			result.merge(request.getSpuId(), request.getStockNum(), Math::addExact);
		}
		return result;
	}

	@Override
	public List<GoodsSku> getSkuByIds(List<String> ids) {
		return baseMapper.selectSkuByIds(ids);
	}

}
