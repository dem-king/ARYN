package com.aryn.cloud.product.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
import com.aryn.cloud.common.search.MeilisearchTemplate;
import com.aryn.cloud.common.search.SearchConstants;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.dto.ProductIndexSyncEvent;
import com.aryn.cloud.product.mapper.BrandMapper;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 商品索引同步监听器
 * <p>
 * 监听 PRODUCT_INDEX_SYNC_TOPIC，收到 SPU 增删改事件后同步 Meilisearch 索引。
 * </p>
 *
 * @author aryn
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.PRODUCT_INDEX_SYNC_TOPIC,
		consumerGroup = "product-service-index-sync-group",
		maxReconsumeTimes = RocketMqConstants.DEFAULT_MAX_RECONSUME_TIMES)
public class ProductIndexSyncListener implements RocketMQListener<ProductIndexSyncEvent> {

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsCategoryMapper goodsCategoryMapper;

	private final BrandMapper brandMapper;

	private final MeilisearchTemplate meilisearchTemplate;

	@Override
	public void onMessage(ProductIndexSyncEvent event) {
		RocketMqConsumerHelper.safeConsume(log, RocketMqConstants.PRODUCT_INDEX_SYNC_TOPIC,
				"product-service-index-sync-group", event, () -> doConsume(event));
	}

	private void doConsume(ProductIndexSyncEvent event) {
		String action = event.getAction();
		String spuId = event.getSpuId();
		log.info("[索引同步] action={}, spuId={}", action, spuId);

		switch (action) {
			case "add", "update" -> syncSingleDocument(spuId);
			case "delete" -> deleteSingleDocument(spuId);
			default -> log.warn("[索引同步] 未知操作类型: action={}", action);
		}
	}

	/**
	 * 同步单个 SPU 文档到 Meilisearch
	 */
	private void syncSingleDocument(String spuId) {
		try {
			GoodsSpu spu = goodsSpuMapper.selectById(spuId);
			if (spu == null) {
				log.warn("[索引同步] SPU不存在, spuId={}", spuId);
				return;
			}
			Map<String, Object> doc = convertToDocument(spu);
			meilisearchTemplate.addDocuments(SearchConstants.SPU_INDEX,
					List.of(doc), SearchConstants.SPU_INDEX_PRIMARY_KEY);
			log.info("[索引同步] 文档同步成功, spuId={}", spuId);
		}
		catch (Exception e) {
			log.error("[索引同步] 文档同步失败, spuId={}, error={}", spuId, e.getMessage(), e);
		}
	}

	/**
	 * 从 Meilisearch 删除单个 SPU 文档
	 */
	private void deleteSingleDocument(String spuId) {
		try {
			meilisearchTemplate.deleteDocument(SearchConstants.SPU_INDEX, spuId);
			log.info("[索引同步] 文档删除成功, spuId={}", spuId);
		}
		catch (Exception e) {
			log.error("[索引同步] 文档删除失败, spuId={}, error={}", spuId, e.getMessage(), e);
		}
	}

	/**
	 * 将 GoodsSpu 实体转换为 Meilisearch 文档
	 */
	public Map<String, Object> convertToDocument(GoodsSpu spu) {
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