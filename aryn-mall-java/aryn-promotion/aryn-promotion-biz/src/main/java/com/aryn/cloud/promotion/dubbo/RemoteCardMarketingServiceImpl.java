package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.GoodsCardTagDTO;
import com.aryn.cloud.promotion.api.dto.GoodsSkuSeckillDTO;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.remote.RemoteCardMarketingService;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;
import com.aryn.cloud.promotion.service.INewbieActivityService;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品卡营销标签远程实现：新人价 + 秒杀聚合成 SPU 级标签。
 *
 * <p>租户上下文快照替换为显式入参再还原（与 RemoteBuyerProfileServiceImpl 同款）：
 * 依赖链上 goods_sku / seckill_* 都在租户拦截表白名单内，上下文缺失会被注入
 * tenant_id = NULL 而静默查空。两路标签各自独立降级（一路失败另一路照常下发），
 * 外层再兜底防异常穿透——卡面标签属增强展示，绝不能打断商品卡渲染。
 *
 * <p>新人价复用 {@link INewbieActivityService#getSkuPrices}：未登录（eligible=null）
 * 按「潜在新人可享」下发，与免登楼层口径一致；登录用户资格判定 fail-closed。
 * getSkuPrices 内部对请求 SKU 有 200 上限（超出忽略），因此先解析 SPU→SKU 再分批查询。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteCardMarketingServiceImpl implements RemoteCardMarketingService {

	/** 单次请求 SPU 上限：超出部分忽略（展示缺失只影响角标，不影响交易） */
	private static final int MAX_SPUS = 200;

	/** 单次请求 SKU 上限（价格快照 Job 调用方按 200 分批，这里再兜底截断） */
	private static final int MAX_SKUS = 200;

	/** getSkuPrices 的单批 SKU 上限：解析出的 SKU 超过时分批查询 */
	private static final int SKU_CHUNK_SIZE = 200;

	private final INewbieActivityService newbieActivityService;

	private final ISeckillActivityService seckillActivityService;

	/**
	 * SPU→SKU 解析走商品域远程接口；非 final 字段注入——构造器注入远程接口
	 * 与引用代理 bean 双候选曾致 boot 启动失败（NewbieActivityServiceImpl 同款注释）。
	 */
	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@Override
	public List<GoodsCardTagDTO> batchCardTags(String tenantId, String userId, List<String> spuIds) {
		if (!StringUtils.hasText(tenantId) || CollectionUtils.isEmpty(spuIds)) {
			return List.of();
		}
		List<String> wanted = spuIds.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.limit(MAX_SPUS)
			.toList();
		if (wanted.isEmpty()) {
			return List.of();
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			Map<String, String> spuBySku = loadSpuBySku(wanted);
			Map<String, GoodsCardTagDTO> bySpu = new LinkedHashMap<>();
			if (!spuBySku.isEmpty()) {
				fillNewbie(bySpu, spuBySku, userId);
			}
			fillSeckill(bySpu, wanted);
			return new ArrayList<>(bySpu.values());
		}
		catch (Exception ex) {
			log.warn("商品卡营销标签批量查询失败，降级为空标签 tenant={} spuCount={}", tenantId, wanted.size(), ex);
			return List.of();
		}
		finally {
			if (snapshot == null) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(snapshot);
			}
		}
	}

	@Override
	public List<GoodsSkuSeckillDTO> batchActiveSeckill(String tenantId, List<String> skuIds) {
		if (!StringUtils.hasText(tenantId) || CollectionUtils.isEmpty(skuIds)) {
			return List.of();
		}
		List<String> wanted = skuIds.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.limit(MAX_SKUS)
			.toList();
		if (wanted.isEmpty()) {
			return List.of();
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			Map<String, GoodsSkuSeckillDTO> bySku = new LinkedHashMap<>();
			for (SeckillGoods goods : seckillActivityService.getActiveSeckillGoodsBySkuIds(wanted)) {
				if (goods == null || !StringUtils.hasText(goods.getSkuId())
						|| goods.getSeckillPrice() == null || goods.getSeckillPrice().signum() <= 0) {
					continue;
				}
				GoodsSkuSeckillDTO dto = bySku.computeIfAbsent(goods.getSkuId(), key -> {
					GoodsSkuSeckillDTO created = new GoodsSkuSeckillDTO();
					created.setSkuId(key);
					return created;
				});
				// 同一 SKU 命中多场次取最低（与卡面「秒杀价」聚合口径一致）
				if (dto.getSeckillPrice() == null
						|| goods.getSeckillPrice().compareTo(dto.getSeckillPrice()) < 0) {
					dto.setSeckillPrice(goods.getSeckillPrice());
				}
			}
			return new ArrayList<>(bySku.values());
		}
		catch (Exception ex) {
			log.warn("价格快照秒杀价批量查询失败，降级为空 tenant={} skuCount={}", tenantId, wanted.size(), ex);
			return List.of();
		}
		finally {
			if (snapshot == null) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(snapshot);
			}
		}
	}

	/** SPU→SKU 精简映射（远程失败只降级新人价标签，秒杀不依赖此映射照常下发） */
	private Map<String, String> loadSpuBySku(List<String> spuIds) {
		try {
			Map<String, String> spuBySku = new LinkedHashMap<>();
			for (GoodsSku sku : remoteGoodsSkuService.listBySpuIds(spuIds)) {
				if (sku != null && StringUtils.hasText(sku.getId()) && StringUtils.hasText(sku.getSpuId())) {
					spuBySku.put(sku.getId(), sku.getSpuId());
				}
			}
			return spuBySku;
		}
		catch (Exception ex) {
			log.warn("商品卡标签 SPU→SKU 解析失败，新人价标签降级: {}", ex.getMessage());
			return Map.of();
		}
	}

	private void fillNewbie(Map<String, GoodsCardTagDTO> bySpu, Map<String, String> spuBySku, String userId) {
		try {
			List<String> skuIds = new ArrayList<>(spuBySku.keySet());
			List<AppNewbiePriceVO> chunks = new ArrayList<>();
			for (int from = 0; from < skuIds.size(); from += SKU_CHUNK_SIZE) {
				List<String> part = skuIds.subList(from, Math.min(from + SKU_CHUNK_SIZE, skuIds.size()));
				AppNewbiePriceVO vo = newbieActivityService.getSkuPrices(part, userId);
				if (vo != null) {
					chunks.add(vo);
				}
			}
			if (chunks.isEmpty()) {
				return;
			}
			// 登录用户资格判定 fail-closed：任一批返回不合格即整体不下发（同一用户口径必然一致）
			boolean rejected = chunks.stream().anyMatch(vo -> Boolean.FALSE.equals(vo.getEligible()));
			if (rejected) {
				return;
			}
			for (AppNewbiePriceVO vo : chunks) {
				if (CollectionUtils.isEmpty(vo.getItems())) {
					continue;
				}
				for (AppNewbiePriceVO.NewbieSkuPriceVO item : vo.getItems()) {
					mergePrice(bySpu, spuBySku.get(item.getSkuId()), item.getNewbiePrice(), true);
				}
			}
		}
		catch (Exception ex) {
			log.warn("商品卡新人价标签查询失败，降级: {}", ex.getMessage());
		}
	}

	private void fillSeckill(Map<String, GoodsCardTagDTO> bySpu, List<String> spuIds) {
		try {
			for (SeckillGoods goods : seckillActivityService.getActiveSeckillGoodsBySpuIds(spuIds)) {
				mergePrice(bySpu, goods != null ? goods.getSpuId() : null, goods != null ? goods.getSeckillPrice() : null,
						false);
			}
		}
		catch (Exception ex) {
			log.warn("商品卡秒杀标签查询失败，降级: {}", ex.getMessage());
		}
	}

	/** 同 SPU 多 SKU/多场次命中取最低可享价（与出清价的 SPU 聚合口径一致） */
	private void mergePrice(Map<String, GoodsCardTagDTO> bySpu, String spuId, java.math.BigDecimal price,
			boolean newbie) {
		if (!StringUtils.hasText(spuId) || price == null || price.signum() <= 0) {
			return;
		}
		GoodsCardTagDTO tag = bySpu.computeIfAbsent(spuId, key -> {
			GoodsCardTagDTO created = new GoodsCardTagDTO();
			created.setSpuId(key);
			return created;
		});
		if (newbie) {
			if (tag.getNewbiePrice() == null || price.compareTo(tag.getNewbiePrice()) < 0) {
				tag.setNewbiePrice(price);
			}
		}
		else {
			if (tag.getSeckillPrice() == null || price.compareTo(tag.getSeckillPrice()) < 0) {
				tag.setSeckillPrice(price);
			}
		}
	}

}
