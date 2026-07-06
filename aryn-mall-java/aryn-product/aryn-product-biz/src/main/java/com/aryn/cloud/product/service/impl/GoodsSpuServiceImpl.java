
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.search.MeilisearchTemplate;
import com.aryn.cloud.common.search.SearchConstants;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.dto.ProductIndexSyncEvent;
import com.aryn.cloud.product.mapper.*;
import com.aryn.cloud.product.service.IGoodsSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.*;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsSpuServiceImpl extends ServiceImpl<GoodsSpuMapper, GoodsSpu> implements IGoodsSpuService {

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsCollectMapper goodsCollectMapper;

	private final GoodsFootprintMapper goodsFootprintMapper;

	private final GoodsCategoryMapper goodsCategoryMapper;

	private final BrandMapper brandMapper;

	private final RocketMQTemplate rocketMQTemplate;

	private final MeilisearchTemplate meilisearchTemplate;

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
			// 查询品牌
			if (StringUtils.hasText(goodsSpu.getBrandId())) {
				Brand brand = brandMapper.selectById(goodsSpu.getBrandId());
				if (Objects.nonNull(brand)) {
					goodsSpu.setBrandName(brand.getName());
				}
			}
		}
		return goodsSpu;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateGoods(GoodsSpu goodsSpu) {

		List<GoodsSku> goodsSkuList = goodsSpu.getGoodsSkus();
		// 获取sku最低销售价
		goodsSpu.setSalesPrice(
				goodsSkuList.stream().min(Comparator.comparing(GoodsSku::getSalesPrice)).get().getSalesPrice());
		// 获取sku最低原价
		goodsSpu.setOriginalPrice(
				goodsSkuList.stream().min(Comparator.comparing(GoodsSku::getOriginalPrice)).get().getSalesPrice());
		// 获取sku最低成本价
		goodsSpu.setCostPrice(
				goodsSkuList.stream().min(Comparator.comparing(GoodsSku::getCostPrice)).get().getSalesPrice());
		// 累加sku库存
		goodsSpu.setStock(goodsSkuList.stream().mapToInt(GoodsSku::getStock).sum());
		List<String> notDelSkuIds = new ArrayList<>();
		// 保存sku
		goodsSkuList.forEach(goodsSku -> {
			goodsSku.setSpuId(goodsSpu.getId());
			if (StringUtils.hasText(goodsSku.getId())) {
				goodsSkuMapper.updateById(goodsSku);
			}
			else {
				goodsSkuMapper.insert(goodsSku);
			}
			notDelSkuIds.add(goodsSku.getId());
		});
		// sku处理
		goodsSkuMapper.delete(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getSpuId, goodsSpu.getId())
			.notIn(!CollectionUtils.isEmpty(notDelSkuIds), GoodsSku::getId, notDelSkuIds.toArray()));

		super.updateById(goodsSpu);

		// 发送索引同步消息
		sendIndexSyncMessage("update", goodsSpu.getId());

		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveGoods(GoodsSpu goodsSpu) {
		List<GoodsSku> goodsSkuList = goodsSpu.getGoodsSkus();

		goodsSpu.setSalesPrice(
				goodsSkuList.stream().min(Comparator.comparing(GoodsSku::getSalesPrice)).get().getSalesPrice());

		goodsSpu.setOriginalPrice(
				goodsSkuList.stream().min(Comparator.comparing(GoodsSku::getOriginalPrice)).get().getSalesPrice());

		goodsSpu.setCostPrice(
				goodsSkuList.stream().min(Comparator.comparing(GoodsSku::getCostPrice)).get().getSalesPrice());

		goodsSpu.setStock(goodsSkuList.stream().mapToInt(GoodsSku::getStock).sum());
		super.save(goodsSpu);
		// 保存sku
		goodsSkuList.forEach(goodsSku -> {
			goodsSku.setId(null);
			goodsSku.setSpuId(goodsSpu.getId());
			goodsSkuMapper.insert(goodsSku);
		});

		// 发送索引同步消息
		sendIndexSyncMessage("add", goodsSpu.getId());

		return Boolean.TRUE;
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
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.ge(GoodsSpu::getStock, 0)
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

	@Override
	public boolean removeById(Serializable id) {
		boolean result = super.removeById(id);
		if (result) {
			sendIndexSyncMessage("delete", id.toString());
		}
		return result;
	}

	/**
	 * 发送索引同步消息到 RocketMQ
	 * @param action 操作类型：add / update / delete
	 * @param spuId  SPU 主键
	 */
	private void sendIndexSyncMessage(String action, String spuId) {
		try {
			ProductIndexSyncEvent event = new ProductIndexSyncEvent(action, spuId);
			rocketMQTemplate.syncSend(RocketMqConstants.PRODUCT_INDEX_SYNC_TOPIC,
					new GenericMessage<>(event), RocketMqConstants.TIME_OUT);
			log.info("[索引同步] 发送消息成功: action={}, spuId={}", action, spuId);
		}
		catch (Exception e) {
			log.error("[索引同步] 发送消息失败: action={}, spuId={}, error={}", action, spuId, e.getMessage(), e);
		}
	}

	@Override
	public long rebuildSearchIndex() {
		log.info("[索引重建] 开始全量重建 SPU 搜索索引");
		List<GoodsSpu> allSpuList = list(Wrappers.<GoodsSpu>lambdaQuery()
			.eq(GoodsSpu::getDelFlag, "0"));
		if (CollectionUtils.isEmpty(allSpuList)) {
			log.info("[索引重建] 无有效 SPU 数据，跳过重建");
			return 0;
		}
		List<Map<String, Object>> documents = allSpuList.stream()
			.map(this::convertToSearchDocument)
			.toList();
		meilisearchTemplate.addDocuments(SearchConstants.SPU_INDEX,
				documents, SearchConstants.SPU_INDEX_PRIMARY_KEY);
		log.info("[索引重建] 全量重建完成，文档数={}", documents.size());
		return documents.size();
	}

	/**
	 * 将 GoodsSpu 转换为 Meilisearch 搜索文档
	 */
	private Map<String, Object> convertToSearchDocument(GoodsSpu spu) {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put(SearchConstants.SPU_FIELD_ID, spu.getId());
		doc.put(SearchConstants.SPU_FIELD_NAME, spu.getName());
		doc.put(SearchConstants.SPU_FIELD_SUBTITLE, spu.getSubTitle());
		doc.put(SearchConstants.SPU_FIELD_KEYWORD, spu.getName());
		doc.put(SearchConstants.SPU_FIELD_CATEGORY_ID, spu.getCategorySecondId());
		doc.put(SearchConstants.SPU_FIELD_CATEGORY_NAME, buildCategoryName(spu));
		doc.put(SearchConstants.SPU_FIELD_BRAND_ID, spu.getBrandId());
		doc.put(SearchConstants.SPU_FIELD_PRICE, spu.getSalesPrice());
		doc.put(SearchConstants.SPU_FIELD_SALES, spu.getSalesVolume());
		doc.put(SearchConstants.SPU_FIELD_STATUS, spu.getStatus());
		doc.put(SearchConstants.SPU_FIELD_TENANT_ID, spu.getTenantId());
		doc.put(SearchConstants.SPU_FIELD_CREATE_TIME,
				spu.getCreateTime() != null ? spu.getCreateTime().toString() : null);
		return doc;
	}

	/**
	 * 构建分类名称（一级/二级）
	 */
	private String buildCategoryName(GoodsSpu spu) {
		StringBuilder sb = new StringBuilder();
		if (spu.getCategoryFirstId() != null) {
			GoodsCategory first = goodsCategoryMapper.selectById(spu.getCategoryFirstId());
			if (first != null) {
				sb.append(first.getName());
			}
		}
		if (spu.getCategorySecondId() != null) {
			GoodsCategory second = goodsCategoryMapper.selectById(spu.getCategorySecondId());
			if (second != null) {
				if (!sb.isEmpty()) {
					sb.append("/");
				}
				sb.append(second.getName());
			}
		}
		return sb.toString();
	}

}
