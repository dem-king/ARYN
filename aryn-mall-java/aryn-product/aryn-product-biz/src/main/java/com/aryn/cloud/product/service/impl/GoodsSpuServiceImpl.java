
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.mapper.*;
import com.aryn.cloud.product.service.IGoodsSpuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Service
@RequiredArgsConstructor
public class GoodsSpuServiceImpl extends ServiceImpl<GoodsSpuMapper, GoodsSpu> implements IGoodsSpuService {

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsCollectMapper goodsCollectMapper;

	private final GoodsFootprintMapper goodsFootprintMapper;

	private final GoodsCategoryMapper goodsCategoryMapper;

	@Override
	public IPage<GoodsSpu> adminPage(Page page, GoodsSpu goodsSpu) {
		return baseMapper.selectPageByAdmin(page, goodsSpu);
	}

	@Override
	public IPage<GoodsSpu> warehousePage(Page page, GoodsSpu goodsSpu) {
		return baseMapper.selectPageWarehouse(page, goodsSpu);
	}

	@Override
	public GoodsSpu getSpuById(String id) {
		GoodsSpu goodsSpu = baseMapper.selectSpuById(id);
		if (Objects.nonNull(goodsSpu)) {
			// 查询分类
			GoodsCategory firstCategory = goodsCategoryMapper.selectById(goodsSpu.getCategoryFirstId());
			GoodsCategory secondCategory = goodsCategoryMapper.selectById(goodsSpu.getCategorySecondId());
			StringBuilder categoryName = new StringBuilder();
			if (Objects.nonNull(firstCategory)) {
				categoryName.append(firstCategory.getName());
			}
			if (Objects.nonNull(secondCategory)) {
				categoryName.append("/").append(secondCategory.getName());
			}
			goodsSpu.setCategoryName(categoryName.toString());
		}
		return goodsSpu;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateGoods(GoodsSpu goodsSpu) {
		if (goodsSpu == null || !StringUtils.hasText(goodsSpu.getId())) {
			throw new ArynBusinessException("商品标识不能为空");
		}
		GoodsSpu storedSpu = baseMapper.selectById(goodsSpu.getId());
		if (storedSpu == null) {
			throw new ArynBusinessException("商品不存在或无权操作");
		}
		List<GoodsSku> goodsSkuList = validateGoodsSkus(goodsSpu);
		Map<String, GoodsSku> storedSkuMap = Optional.ofNullable(goodsSkuMapper.selectBySpuId(goodsSpu.getId()))
			.orElseGet(Collections::emptyList)
			.stream()
			.collect(LinkedHashMap::new, (map, sku) -> map.put(sku.getId(), sku), Map::putAll);
		int stockDelta = 0;
		for (GoodsSku goodsSku : goodsSkuList) {
			if (StringUtils.hasText(goodsSku.getId())) {
				GoodsSku storedSku = storedSkuMap.remove(goodsSku.getId());
				if (storedSku == null) {
					throw new ArynBusinessException("SKU不属于当前商品");
				}
				int storedVersion = Objects.requireNonNullElse(storedSku.getVersion(), 0);
				if (goodsSku.getVersion() == null || goodsSku.getVersion() != storedVersion) {
					throw new ArynBusinessException("商品库存已变化，请刷新后重试");
				}
				stockDelta = Math.addExact(stockDelta, goodsSku.getStock() - storedSku.getStock());
				prepareSkuForUpdate(goodsSku, goodsSpu.getId());
				if (goodsSkuMapper.updateById(goodsSku) <= 0) {
					throw new ArynBusinessException("商品库存已变化，请刷新后重试");
				}
			}
			else {
				stockDelta = Math.addExact(stockDelta, goodsSku.getStock());
				prepareSkuForInsert(goodsSku, goodsSpu.getId());
				if (goodsSkuMapper.insert(goodsSku) <= 0) {
					throw new ArynBusinessException("新增SKU失败");
				}
			}
		}
		for (GoodsSku removedSku : storedSkuMap.values()) {
			int version = Objects.requireNonNullElse(removedSku.getVersion(), 0);
			if (goodsSkuMapper.delete(Wrappers.<GoodsSku>lambdaQuery()
				.eq(GoodsSku::getId, removedSku.getId())
				.eq(GoodsSku::getSpuId, goodsSpu.getId())
				.eq(GoodsSku::getVersion, version)) <= 0) {
				throw new ArynBusinessException("商品库存已变化，请刷新后重试");
			}
			stockDelta = Math.subtractExact(stockDelta, removedSku.getStock());
		}
		applyAggregatePrices(goodsSpu, goodsSkuList);
		prepareSpuForUpdate(goodsSpu);
		if (super.updateById(goodsSpu) == false) {
			throw new ArynBusinessException("商品修改失败");
		}
		adjustSpuStock(goodsSpu.getId(), stockDelta);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveGoods(GoodsSpu goodsSpu) {
		List<GoodsSku> goodsSkuList = validateGoodsSkus(goodsSpu);
		applyAggregatePrices(goodsSpu, goodsSkuList);
		goodsSpu.setStock(calculateTotalStock(goodsSkuList));
		prepareSpuForInsert(goodsSpu);
		if (super.save(goodsSpu) == false) {
			throw new ArynBusinessException("新增商品失败");
		}
		for (GoodsSku goodsSku : goodsSkuList) {
			prepareSkuForInsert(goodsSku, goodsSpu.getId());
			if (goodsSkuMapper.insert(goodsSku) <= 0) {
				throw new ArynBusinessException("新增SKU失败");
			}
		}
		return Boolean.TRUE;
	}

	private List<GoodsSku> validateGoodsSkus(GoodsSpu goodsSpu) {
		if (goodsSpu == null || CollectionUtils.isEmpty(goodsSpu.getGoodsSkus())) {
			throw new ArynBusinessException("商品SKU不能为空");
		}
		List<GoodsSku> goodsSkuList = goodsSpu.getGoodsSkus();
		if ("0".equals(goodsSpu.getEnableSpecs()) && goodsSkuList.size() != 1) {
			throw new ArynBusinessException("单规格商品只能包含一个SKU");
		}
		Set<String> specsKeys = new HashSet<>();
		for (GoodsSku goodsSku : goodsSkuList) {
			if (goodsSku == null || goodsSku.getSalesPrice() == null || goodsSku.getOriginalPrice() == null
					|| goodsSku.getCostPrice() == null || goodsSku.getStock() == null || goodsSku.getStock() < 0
					|| goodsSku.getSalesPrice().signum() < 0 || goodsSku.getOriginalPrice().signum() < 0
					|| goodsSku.getCostPrice().signum() < 0) {
				throw new ArynBusinessException("SKU价格或库存不合法");
			}
			String specsKey = buildSpecsKey(goodsSku);
			if ("1".equals(goodsSpu.getEnableSpecs()) && !StringUtils.hasText(specsKey)) {
				throw new ArynBusinessException("多规格商品的SKU规格不能为空");
			}
			if (!specsKeys.add(specsKey)) {
				throw new ArynBusinessException("SKU规格组合重复");
			}
		}
		return goodsSkuList;
	}

	private int calculateTotalStock(List<GoodsSku> goodsSkuList) {
		long totalStock = 0;
		for (GoodsSku goodsSku : goodsSkuList) {
			totalStock += goodsSku.getStock();
			if (totalStock > Integer.MAX_VALUE) {
				throw new ArynBusinessException("商品库存总量超出上限");
			}
		}
		return (int) totalStock;
	}

	private String buildSpecsKey(GoodsSku goodsSku) {
		if (CollectionUtils.isEmpty(goodsSku.getSpecsArr())) {
			return "";
		}
		List<String> keys = new ArrayList<>();
		for (GoodsSku.Specs specs : goodsSku.getSpecsArr()) {
			if (specs == null || !StringUtils.hasText(specs.getSpecsId())
					|| !StringUtils.hasText(specs.getSpecsValueId())) {
				throw new ArynBusinessException("SKU规格信息不完整");
			}
			keys.add(specs.getSpecsId() + ":" + specs.getSpecsValueId());
		}
		Collections.sort(keys);
		return String.join("|", keys);
	}

	private void applyAggregatePrices(GoodsSpu goodsSpu, List<GoodsSku> goodsSkuList) {
		goodsSpu.setSalesPrice(
				goodsSkuList.stream().map(GoodsSku::getSalesPrice).min(Comparator.naturalOrder()).orElseThrow());
		goodsSpu.setOriginalPrice(
				goodsSkuList.stream().map(GoodsSku::getOriginalPrice).min(Comparator.naturalOrder()).orElseThrow());
		goodsSpu.setCostPrice(
				goodsSkuList.stream().map(GoodsSku::getCostPrice).min(Comparator.naturalOrder()).orElseThrow());
	}

	private void prepareSpuForInsert(GoodsSpu goodsSpu) {
		goodsSpu.setId(null);
		goodsSpu.setTenantId(null);
		goodsSpu.setSalesVolume(0);
		goodsSpu.setCreateBy(null);
		goodsSpu.setUpdateBy(null);
		goodsSpu.setCreateTime(null);
		goodsSpu.setUpdateTime(null);
		goodsSpu.setDelFlag(null);
	}

	private void prepareSpuForUpdate(GoodsSpu goodsSpu) {
		goodsSpu.setTenantId(null);
		goodsSpu.setSalesVolume(null);
		goodsSpu.setStock(null);
		goodsSpu.setCreateBy(null);
		goodsSpu.setUpdateBy(null);
		goodsSpu.setCreateTime(null);
		goodsSpu.setUpdateTime(null);
		goodsSpu.setDelFlag(null);
	}

	private void prepareSkuForInsert(GoodsSku goodsSku, String spuId) {
		goodsSku.setId(null);
		goodsSku.setSpuId(spuId);
		goodsSku.setTenantId(null);
		goodsSku.setVersion(null);
		goodsSku.setCreateBy(null);
		goodsSku.setUpdateBy(null);
		goodsSku.setCreateTime(null);
		goodsSku.setUpdateTime(null);
		goodsSku.setDelFlag(null);
		goodsSku.setGoodsSpu(null);
	}

	private void prepareSkuForUpdate(GoodsSku goodsSku, String spuId) {
		goodsSku.setSpuId(spuId);
		goodsSku.setTenantId(null);
		goodsSku.setCreateBy(null);
		goodsSku.setUpdateBy(null);
		goodsSku.setCreateTime(null);
		goodsSku.setUpdateTime(null);
		goodsSku.setDelFlag(null);
		goodsSku.setGoodsSpu(null);
	}

	private void adjustSpuStock(String spuId, int stockDelta) {
		if (stockDelta == 0) {
			return;
		}
		if (stockDelta > 0) {
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.setSql("stock = stock + " + stockDelta)) <= 0) {
				throw new ArynBusinessException("商品库存汇总更新失败");
			}
			return;
		}
		int quantity = Math.negateExact(stockDelta);
		if (baseMapper.update(new GoodsSpu(),
				Wrappers.<GoodsSpu>lambdaUpdate()
					.eq(GoodsSpu::getId, spuId)
					.ge(GoodsSpu::getStock, quantity)
					.setSql("stock = stock - " + quantity)) <= 0) {
			throw new ArynBusinessException("商品库存汇总更新失败");
		}
	}

	@Override
	public IPage<GoodsSpu> apiPage(Page page, GoodsSpu goodsSpu) {
		return baseMapper.selectApiPage(page, goodsSpu);
	}

	@Override
	public GoodsSpu getApiSpuById(String id) {
		GoodsSpu goodsSpu = baseMapper.selectApiSpuById(id);
		if (Objects.nonNull(goodsSpu)) {
			ArynUser hxUser = SecurityUtils.getUser();
			if (Objects.nonNull(hxUser) && StringUtils.hasText(hxUser.getUserId())) {
				GoodsCollect goodsCollect = goodsCollectMapper.selectOne(Wrappers.<GoodsCollect>lambdaQuery()
					.eq(GoodsCollect::getSpuId, id)
					.eq(GoodsCollect::getUserId, hxUser.getUserId()));
				if (Objects.nonNull(goodsCollect)) {
					goodsSpu.setCollectId(goodsCollect.getId());
				}
				// 保存浏览记录
				GoodsFootprint goodsFootprint = new GoodsFootprint();
				goodsFootprint.setSpuId(id);
				goodsFootprint.setUserId(hxUser.getUserId());
				goodsFootprintMapper.insert(goodsFootprint);
			}

		}
		return goodsSpu;
	}

	@Override
	public boolean updateSalesVolume(String spuId, Integer buyQuantity) {
		return baseMapper.update(new GoodsSpu(),
				Wrappers.<GoodsSpu>lambdaUpdate()
					.eq(GoodsSpu::getId, spuId)
					.setSql(" sales_volume = sales_volume + " + buyQuantity)) > 0;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean reduceStock(Map<String, Integer> result) {
		for (Map.Entry<String, Integer> entry : result.entrySet()) {
			String spuId = entry.getKey();
			int quantity = entry.getValue();
			if (!StringUtils.hasText(spuId) || quantity <= 0) {
				throw new ArynBusinessException("库存变更参数不合法");
			}
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.ge(GoodsSpu::getStock, quantity)
						.setSql(" stock = stock - " + quantity)) <= 0) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
						MallErrorCodeEnum.ERROR_60008.getMsg());
			}
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean rollbackStock(Map<String, Integer> result) {
		for (Map.Entry<String, Integer> entry : result.entrySet()) {
			String spuId = entry.getKey();
			int quantity = entry.getValue();
			if (!StringUtils.hasText(spuId) || quantity <= 0) {
				throw new ArynBusinessException("库存变更参数不合法");
			}
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.ge(GoodsSpu::getStock, 0)
						.setSql(" stock = stock + " + quantity)) <= 0) {
				throw new ArynBusinessException("回滚库存失败！");
			}
		}
		return Boolean.TRUE;
	}

	@Override
	public List<GoodsSpu> getTop10HotSearchGoods() {
		return baseMapper.getTop10HotSearchGoods();
	}

}
