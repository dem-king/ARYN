package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSkuPriceDaily;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.AppClearancePriceVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSkuPriceDailyMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IGoodsSkuPriceDailyService;
import com.aryn.cloud.product.service.IGoodsZoneTagService;
import com.aryn.cloud.promotion.api.dto.GoodsSkuSeckillDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCardMarketingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SKU 每日价格快照实现：对全部在售 SKU 落一行「非个性化到手价」。
 *
 * <p>价 = MIN(标价, 出清生效价, 秒杀价)：
 * - 标价取 SKU 售价、缺省回落 SPU 售价（与专区价查询的取价基准一致）；
 * - 出清价复用 {@link IGoodsZoneTagService#clearancePrices(List)} 专区同源口径
 *   （SKU 覆盖 + 系统活动最终价 + 倒挂过滤 + 专区开关），分区按 200 SKU 分批；
 * - 秒杀价经 {@link RemoteCardMarketingService#batchActiveSeckill} 批量拉取，
 *   远程失败降级仅用本地价（表注释记录口径）；
 * - 会员价/新人价是 user-scoped 身份价，无法全局快照，不参与取低。
 *
 * <p>构造器注入远程接口曾有双候选代理致 boot 启动失败的前科，远程引用走
 * 非 final 字段注入；本地 mapper/service 走构造器注入。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsSkuPriceDailyServiceImpl implements IGoodsSkuPriceDailyService {

	/** 出清价分批上限：与 GoodsZoneTagServiceImpl#MAX_PRICE_QUERY_SKUS 同值（超出其内部静默截断） */
	private static final int CLEARANCE_CHUNK = 200;

	/** 秒杀价分批上限：与 RemoteCardMarketingServiceImpl 的入参上限同值 */
	private static final int SECKILL_CHUNK = 200;

	/** 快照批量 upsert 分片大小 */
	private static final int UPSERT_CHUNK = 500;

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsSkuPriceDailyMapper goodsSkuPriceDailyMapper;

	private final IGoodsZoneTagService goodsZoneTagService;

	@DubboReference
	private RemoteCardMarketingService remoteCardMarketingService;

	@Override
	public int snapshotDaily(String tenantId, LocalDate statDate) {
		if (!StringUtils.hasText(tenantId) || statDate == null) {
			return 0;
		}
		List<GoodsSpu> spus = goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
			.eq(GoodsSpu::getTenantId, tenantId)
			.eq(GoodsSpu::getStatus, "1")
			.select(GoodsSpu::getId, GoodsSpu::getSalesPrice));
		if (spus.isEmpty()) {
			return 0;
		}
		Map<String, GoodsSpu> spuById = new HashMap<>();
		for (GoodsSpu spu : spus) {
			if (spu != null && StringUtils.hasText(spu.getId())) {
				spuById.put(spu.getId(), spu);
			}
		}
		List<GoodsSku> skus = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getTenantId, tenantId)
			.eq(GoodsSku::getStatus, "0")
			.in(GoodsSku::getSpuId, spuById.keySet())
			.select(GoodsSku::getId, GoodsSku::getSpuId, GoodsSku::getSalesPrice));
		if (skus.isEmpty()) {
			return 0;
		}
		List<String> skuIds = skus.stream()
			.map(GoodsSku::getId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		Map<String, BigDecimal> clearancePrices = loadClearancePrices(tenantId, skuIds);
		Map<String, BigDecimal> seckillPrices = loadSeckillPrices(tenantId, skuIds);

		int rows = 0;
		List<GoodsSkuPriceDaily> buffer = new ArrayList<>();
		for (GoodsSku sku : skus) {
			GoodsSpu spu = sku == null ? null : spuById.get(sku.getSpuId());
			if (spu == null || !StringUtils.hasText(sku.getId())) {
				continue;
			}
			BigDecimal sales = sku.getSalesPrice() != null ? sku.getSalesPrice() : spu.getSalesPrice();
			BigDecimal price = minPositive(sales, clearancePrices.get(sku.getId()), seckillPrices.get(sku.getId()));
			if (price == null || price.signum() <= 0) {
				continue;
			}
			GoodsSkuPriceDaily row = new GoodsSkuPriceDaily();
			row.setTenantId(tenantId);
			row.setSpuId(spu.getId());
			row.setSkuId(sku.getId());
			row.setStatDate(statDate);
			row.setSalePrice(price);
			buffer.add(row);
			if (buffer.size() >= UPSERT_CHUNK) {
				// 传拷贝再清空：list 复用会让捕获/异步消费方看到被清空后的同一引用
				rows += goodsSkuPriceDailyMapper.upsertDailyPrices(new ArrayList<>(buffer));
				buffer.clear();
			}
		}
		if (!buffer.isEmpty()) {
			rows += goodsSkuPriceDailyMapper.upsertDailyPrices(new ArrayList<>(buffer));
		}
		return rows;
	}

	/**
	 * 出清生效价（专区同源口径）：{@link IGoodsZoneTagService#clearancePrices(List)}
	 * 内部含专区开关/到期/倒挂过滤，只返回低于售价的有效价；单批失败只丢该批，
	 * 不阻断整个租户的快照（降级后该批 SKU 仅用本地价）。
	 */
	private Map<String, BigDecimal> loadClearancePrices(String tenantId, List<String> skuIds) {
		Map<String, BigDecimal> prices = new HashMap<>();
		for (int from = 0; from < skuIds.size(); from += CLEARANCE_CHUNK) {
			List<String> part = skuIds.subList(from, Math.min(from + CLEARANCE_CHUNK, skuIds.size()));
			try {
				AppClearancePriceVO vo = goodsZoneTagService.clearancePrices(part);
				if (vo == null || CollectionUtils.isEmpty(vo.getItems())) {
					continue;
				}
				for (AppClearancePriceVO.Item item : vo.getItems()) {
					if (item == null || !StringUtils.hasText(item.getSkuId())) {
						continue;
					}
					BigDecimal price = item.getClearancePrice();
					if (price == null || price.signum() <= 0) {
						continue;
					}
					prices.merge(item.getSkuId(), price, BigDecimal::min);
				}
			}
			catch (Exception ex) {
				log.warn("价格快照出清价查询失败（该批降级仅本地价） tenant={} size={}", tenantId, part.size(), ex);
			}
		}
		return prices;
	}

	/** 秒杀价（promotion 远程批量）：任何失败降级为空 Map，仅用本地价快照 */
	private Map<String, BigDecimal> loadSeckillPrices(String tenantId, List<String> skuIds) {
		Map<String, BigDecimal> prices = new HashMap<>();
		for (int from = 0; from < skuIds.size(); from += SECKILL_CHUNK) {
			List<String> part = skuIds.subList(from, Math.min(from + SECKILL_CHUNK, skuIds.size()));
			try {
				List<GoodsSkuSeckillDTO> dtos = remoteCardMarketingService.batchActiveSeckill(tenantId, part);
				if (CollectionUtils.isEmpty(dtos)) {
					continue;
				}
				for (GoodsSkuSeckillDTO dto : dtos) {
					if (dto == null || !StringUtils.hasText(dto.getSkuId())) {
						continue;
					}
					BigDecimal price = dto.getSeckillPrice();
					if (price == null || price.signum() <= 0) {
						continue;
					}
					prices.merge(dto.getSkuId(), price, BigDecimal::min);
				}
			}
			catch (Exception ex) {
				log.warn("价格快照秒杀价查询失败（该批降级仅本地价） tenant={} size={}", tenantId, part.size(), ex);
			}
		}
		return prices;
	}

	/** 多个候选价取最小正值（null 候选忽略；全空返回 null） */
	private BigDecimal minPositive(BigDecimal... candidates) {
		BigDecimal min = null;
		for (BigDecimal candidate : candidates) {
			if (candidate == null || candidate.signum() <= 0) {
				continue;
			}
			if (min == null || candidate.compareTo(min) < 0) {
				min = candidate;
			}
		}
		return min;
	}

}
