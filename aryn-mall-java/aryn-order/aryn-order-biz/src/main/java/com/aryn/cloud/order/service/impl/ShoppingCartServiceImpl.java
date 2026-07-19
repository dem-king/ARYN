
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车
 *
 * @author 雨滴kian
 * @since 2022/3/17 14:51
 */
@Service
@RequiredArgsConstructor
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart>
		implements IShoppingCartService {

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@Override
	public List<ShoppingCart> apiPage(Page page, ShoppingCart shoppingCart) {
		IPage<ShoppingCart> iPage = baseMapper.selectApiPage(page, shoppingCart);
		if (CollectionUtils.isEmpty(iPage.getRecords())) {
			return null;
		}
		// 从List中提取SKUID
		List<String> skuIds = iPage.getRecords()
			.stream()
			.map(ShoppingCart::getSkuId)
			.distinct() // 去重，如果有可能有重复的商品ID
			.toList();

		// 调用商品服务，获取商品详情
		List<GoodsSku> goodsSkuList = remoteGoodsSkuService.getSkuByIds(skuIds);
		if (CollectionUtils.isEmpty(goodsSkuList)) {
			throw new IllegalArgumentException("query goods sku list fail!");
		}

		// 创建商品SKU ID到商品对象的映射
		Map<String, GoodsSku> skuIdToGoodsSku = goodsSkuList.stream()
			.collect(Collectors.toMap(GoodsSku::getId, goodsSku -> goodsSku, (existing, replacement) -> existing)); // 解决key冲突的情况
		iPage.getRecords().forEach(s -> {
			s.setGoodsSku(skuIdToGoodsSku.get(s.getSkuId()));
		});
		return iPage.getRecords();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveShoppingCart(String userId, ShoppingCartCreateDTO request) {
		GoodsSku sku = requireSaleSku(request.getSkuId(), request.getQuantity());
		if (baseMapper.incrementQuantity(userId, sku.getId(), request.getQuantity()) > 0) {
			return true;
		}
		ShoppingCart shoppingCart = buildCart(userId, sku, request.getQuantity());
		try {
			return super.save(shoppingCart);
		}
		catch (DuplicateKeyException exception) {
			return baseMapper.incrementQuantity(userId, sku.getId(), request.getQuantity()) > 0;
		}
	}

	@Override
	public boolean clear(String userId, List<String> skuIds) {
		if (CollectionUtils.isEmpty(skuIds)) {
			return true;
		}
		return baseMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
			.in(ShoppingCart::getSkuId, skuIds)
			.eq(ShoppingCart::getUserId, userId)) > 0;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateShoppingCart(String userId, ShoppingCartUpdateDTO request) {
		ShoppingCart source = baseMapper.selectOne(Wrappers.<ShoppingCart>lambdaQuery()
			.eq(ShoppingCart::getId, request.getId())
			.eq(ShoppingCart::getUserId, userId));
		if (source == null) {
			throw new ArynBusinessException("购物车商品不存在");
		}
		String targetSkuId = StringUtils.hasText(request.getSkuId()) ? request.getSkuId() : source.getSkuId();
		GoodsSku sku = requireSaleSku(targetSkuId, request.getQuantity());
		if (targetSkuId.equals(source.getSkuId())) {
			applySkuSnapshot(source, sku);
			source.setQuantity(request.getQuantity());
			return super.updateById(source);
		}

		ShoppingCart target = baseMapper.selectOne(Wrappers.<ShoppingCart>lambdaQuery()
			.eq(ShoppingCart::getUserId, userId)
			.eq(ShoppingCart::getSkuId, sku.getId())
			.ne(ShoppingCart::getId, source.getId()));
		if (target != null) {
			baseMapper.incrementQuantity(userId, sku.getId(), request.getQuantity());
			return baseMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
				.eq(ShoppingCart::getId, source.getId())
				.eq(ShoppingCart::getUserId, userId)) > 0;
		}
		applySkuSnapshot(source, sku);
		source.setQuantity(request.getQuantity());
		try {
			return super.updateById(source);
		}
		catch (DuplicateKeyException exception) {
			baseMapper.incrementQuantity(userId, sku.getId(), request.getQuantity());
			return baseMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
				.eq(ShoppingCart::getId, source.getId())
				.eq(ShoppingCart::getUserId, userId)) > 0;
		}
	}

	@Override
	public boolean removeByUserId(String userId, List<String> ids) {
		return baseMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
			.eq(ShoppingCart::getUserId, userId)
			.in(ShoppingCart::getId, ids)) > 0;
	}

	private GoodsSku requireSaleSku(String skuId, int quantity) {
		List<GoodsSku> skuList = remoteGoodsSkuService.getBySkuIds(List.of(skuId));
		if (skuList == null || skuList.size() != 1 || skuList.get(0).getGoodsSpu() == null
				|| skuList.get(0).getStock() == null || skuList.get(0).getStock() < quantity) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}
		return skuList.get(0);
	}

	private ShoppingCart buildCart(String userId, GoodsSku sku, int quantity) {
		ShoppingCart shoppingCart = new ShoppingCart();
		shoppingCart.setUserId(userId);
		shoppingCart.setQuantity(quantity);
		applySkuSnapshot(shoppingCart, sku);
		return shoppingCart;
	}

	private void applySkuSnapshot(ShoppingCart shoppingCart, GoodsSku sku) {
		GoodsSpu spu = sku.getGoodsSpu();
		shoppingCart.setSkuId(sku.getId());
		shoppingCart.setSpuId(spu.getId());
		shoppingCart.setSpuName(spu.getName());
		shoppingCart.setSalesPrice(sku.getSalesPrice());
		String picUrl = sku.getPicUrl();
		if (!StringUtils.hasText(picUrl) && spu.getSpuUrls() != null && spu.getSpuUrls().length > 0) {
			picUrl = spu.getSpuUrls()[0];
		}
		shoppingCart.setPicUrl(picUrl);
		String specsInfo = sku.getSpecsArr() == null ? "" : sku.getSpecsArr().stream()
			.map(GoodsSku.Specs::getSpecsValueName)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining("；"));
		shoppingCart.setSpecsInfo(specsInfo);
	}

}
