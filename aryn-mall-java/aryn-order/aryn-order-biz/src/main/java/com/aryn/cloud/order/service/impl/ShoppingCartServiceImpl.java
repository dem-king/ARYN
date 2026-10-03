
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ShoppingCartBatchAddDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.api.vo.ShoppingCartBatchAddVO;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.util.GoodsCostPriceMasker;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
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

		// 调用商品服务，获取商品详情。下架/删除的 SKU 查不到属正常情况：
		// 购物车允许留有历史商品，前端据 goodsSku 为空展示「下架」并让用户自行清理，
		// 因此这里不能因查不到而整体报错（曾导致整车商品全下架时列表 500）。
		List<GoodsSku> goodsSkuList = remoteGoodsSkuService.getSkuByIds(skuIds);
		if (CollectionUtils.isEmpty(goodsSkuList)) {
			return iPage.getRecords();
		}

		// 创建商品SKU ID到商品对象的映射
		Map<String, GoodsSku> skuIdToGoodsSku = goodsSkuList.stream()
			.collect(Collectors.toMap(GoodsSku::getId, goodsSku -> goodsSku, (existing, replacement) -> existing)); // 解决key冲突的情况
		iPage.getRecords().forEach(s -> {
			GoodsSku goodsSku = skuIdToGoodsSku.get(s.getSkuId());
			// C 端出参脱敏：购物车行内嵌完整 GoodsSku，成本价不下发（详见 GoodsCostPriceMasker）
			GoodsCostPriceMasker.maskSku(goodsSku);
			s.setGoodsSku(goodsSku);
		});
		return iPage.getRecords();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveShoppingCart(String userId, ShoppingCartCreateDTO request) {
		GoodsSku sku = requireSaleSku(request.getSkuId(), request.getQuantity());
		// 防串船：合并范围限定在同用户 + 同 SKU + 同靠港（无上下文归入“未指定”组）
		ShoppingCart mergeable = findMergeableRow(userId, sku.getId(), request.getVesselCallId());
		if (mergeable != null) {
			return baseMapper.incrementQuantityById(userId, mergeable.getId(), request.getQuantity()) > 0;
		}
		ShoppingCart shoppingCart = buildCart(userId, sku, request.getQuantity(), request);
		try {
			return super.save(shoppingCart);
		}
		catch (DuplicateKeyException exception) {
			ShoppingCart concurrent = findMergeableRow(userId, sku.getId(), request.getVesselCallId());
			if (concurrent != null) {
				return baseMapper.incrementQuantityById(userId, concurrent.getId(), request.getQuantity()) > 0;
			}
			throw exception;
		}
	}

	/**
	 * 查找同用户同 SKU 且靠港归属相同（NULL 等价于“未指定”）的可合并购物车行。
	 */
	private ShoppingCart findMergeableRow(String userId, String skuId, String vesselCallId) {
		com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShoppingCart> wrapper = Wrappers
			.<ShoppingCart>lambdaQuery()
			.eq(ShoppingCart::getTenantId, ArynTenantContextHolder.getTenantId())
			.eq(ShoppingCart::getUserId, userId)
			.eq(ShoppingCart::getSkuId, skuId)
			.eq(ShoppingCart::getDelFlag, "0");
		if (StringUtils.hasText(vesselCallId)) {
			wrapper.eq(ShoppingCart::getVesselCallId, vesselCallId);
		}
		else {
			wrapper.and(query -> query.isNull(ShoppingCart::getVesselCallId)
				.or().eq(ShoppingCart::getVesselCallId, ""));
		}
		return baseMapper.selectOne(wrapper.last("LIMIT 1"));
	}

	/**
	 * 批量加购（**刻意不加 @Transactional**）。
	 *
	 * <p>按部分成功语义实现：逐项调用 {@link #saveShoppingCart}，单项失败只记录原因、
	 * 不回滚已成功的项。若在此方法上加事务，任一项失败会把整批一起回滚，
	 * 用户就无法把清单里可买的商品先加进购物车。
	 */
	@Override
	public ShoppingCartBatchAddVO batchAdd(String userId, ShoppingCartBatchAddDTO request) {
		List<ShoppingCartCreateDTO> items = request.getItems();
		ShoppingCartBatchAddVO result = new ShoppingCartBatchAddVO();
		result.setRequestedCount(items.size());

		int added = 0;
		for (ShoppingCartCreateDTO item : items) {
			try {
				// 数量规则（MOQ/步长）随船供包装资料下线（2026-09-29）：
				// 批量加购不再做 MOQ/步长拦截，与单条加购口径一致。
				if (saveShoppingCart(userId, item)) {
					added++;
				}
				else {
					result.getFailures().add(new ShoppingCartBatchAddVO.Failure(
							item.getSkuId(), item.getQuantity(), "加入购物车失败"));
				}
			}
			catch (ArynBusinessException exception) {
				// 单条失败不影响其余项：批量加购按部分成功语义返回
				result.getFailures().add(new ShoppingCartBatchAddVO.Failure(
						item.getSkuId(), item.getQuantity(), readableReason(exception)));
			}
		}
		result.setAddedCount(added);
		result.setFailedCount(result.getFailures().size());
		return result;
	}

	/**
	 * 取面向用户可读的失败原因：优先用业务异常的 msg，为空时回落 code 提示。
	 */
	private String readableReason(ArynBusinessException exception) {
		if (StringUtils.hasText(exception.getMsg())) {
			return exception.getMsg();
		}
		return "加入购物车失败";
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
			.eq(ShoppingCart::getVesselCallId, source.getVesselCallId())
			.ne(ShoppingCart::getId, source.getId())
			.last("LIMIT 1"));
		if (target != null) {
			baseMapper.incrementQuantityById(userId, target.getId(), request.getQuantity());
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
			baseMapper.incrementQuantityById(userId, source.getId(), request.getQuantity());
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

	private ShoppingCart buildCart(String userId, GoodsSku sku, int quantity, ShoppingCartCreateDTO request) {
		ShoppingCart shoppingCart = new ShoppingCart();
		shoppingCart.setUserId(userId);
		shoppingCart.setQuantity(quantity);
		shoppingCart.setVesselId(request.getVesselId());
		shoppingCart.setVesselCallId(request.getVesselCallId());
		shoppingCart.setPurchaseScene(request.getPurchaseScene());
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
