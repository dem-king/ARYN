package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesStatService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCardTagVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSkuPriceDailyMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IGoodsCardTagService;
import com.aryn.cloud.product.support.SkuPriceWindowStat;
import com.aryn.cloud.product.support.SocialProofFormatter;
import com.aryn.cloud.promotion.api.dto.GoodsCardTagDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCardMarketingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品卡营销标签聚合实现：promotion（新人价+秒杀）、order（社证统计）两路
 * Dubbo 拉取 + product 本域（价格快照窗口判定）共三路，按 SPU 合并；
 * 社证文案的门槛/取整在服务端收口（{@link SocialProofFormatter}）。
 *
 * <p>构造器注入远程接口曾有双候选代理致 boot 启动失败的前科，与
 * {@code GoodsRankServiceImpl} 同款非 final 字段注入；每一路独立 try-catch 降级
 * （一路失败不影响另一路），整体失败降级空列表——商品卡永远不因营销标签挂掉而缺商品。
 *
 * <p>「近N天最低价」第三路是本域数据库查询（零远程）：打标三条件——
 * ① 主价驱动 SKU 的当日快照价 ≤ 窗口最低价；② 窗口内出现过 ≥2 个不同价
 * （发生过变价，防常平价天天挂标）；③ 当日价 < SPU 原价（有划线空间才有意义）。
 * 当日价取快照行（口径=MIN(标价,出清,秒杀)），与快照口径自洽：出清/秒杀降价当天即出标。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsCardTagServiceImpl implements IGoodsCardTagService {

	/** 单次请求 SPU 上限（远程侧 getSkuPrices 另有 SKU 上限，超出忽略不报错） */
	private static final int MAX_SPUS = 200;

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsSkuPriceDailyMapper goodsSkuPriceDailyMapper;

	@DubboReference
	private RemoteCardMarketingService remoteCardMarketingService;

	@DubboReference
	private RemoteGoodsSalesStatService remoteGoodsSalesStatService;

	/** 社证展示门槛：买家/回头客数下限（低于不下发，宁缺毋滥） */
	@Value("${goods-card.social-proof.min-buyer-cnt:1000}")
	private int minBuyerCnt;

	/** 社证展示门槛：回购率%下限 */
	@Value("${goods-card.social-proof.min-repurchase-rate:30}")
	private int minRepurchaseRate;

	/** 价格声明窗口天数（文案随 N 动态拼装，改窗口声明不失真）；≤0 视为关闭 */
	@Value("${goods-card.price-tag.window-days:14}")
	private int priceTagWindowDays;

	/** 价格声明总开关（快照表空时自然灰度；配置置 false 可彻底关断） */
	@Value("${goods-card.price-tag.enabled:true}")
	private boolean priceTagEnabled;

	@Override
	public List<GoodsCardTagVO> cardTags(List<String> spuIds) {
		if (spuIds == null || spuIds.isEmpty()) {
			return List.of();
		}
		Set<String> wanted = new LinkedHashSet<>();
		for (String spuId : spuIds) {
			if (StringUtils.hasText(spuId) && wanted.size() < MAX_SPUS) {
				wanted.add(spuId.trim());
			}
		}
		if (wanted.isEmpty()) {
			return List.of();
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		Map<String, GoodsCardTagVO> bySpu = new LinkedHashMap<>();
		// 三路独立降级：新人价/秒杀失败只丢价签，社证失败只丢文案，快照失败只丢价格声明
		fillPromotionTags(bySpu, tenantId, new ArrayList<>(wanted));
		fillSocialProof(bySpu, tenantId, new ArrayList<>(wanted));
		fillPriceTag(bySpu, tenantId, new ArrayList<>(wanted));
		return new ArrayList<>(bySpu.values());
	}

	/** 新人价 + 秒杀（promotion 域聚合出口）：命中才置值，null=不展示 */
	private void fillPromotionTags(Map<String, GoodsCardTagVO> bySpu, String tenantId, List<String> wanted) {
		try {
			List<GoodsCardTagDTO> tags = remoteCardMarketingService.batchCardTags(
					tenantId, currentUserId(), wanted);
			if (tags == null) {
				return;
			}
			for (GoodsCardTagDTO dto : tags) {
				if (dto == null || !StringUtils.hasText(dto.getSpuId())) {
					continue;
				}
				GoodsCardTagVO vo = bySpu.computeIfAbsent(dto.getSpuId(), key -> {
					GoodsCardTagVO created = new GoodsCardTagVO();
					created.setSpuId(key);
					return created;
				});
				vo.setNewbiePrice(dto.getNewbiePrice());
				vo.setSeckillPrice(dto.getSeckillPrice());
			}
		}
		catch (Exception ex) {
			log.warn("商品卡营销价签查询失败，降级 spuCount={}", wanted.size(), ex);
		}
	}

	/** 社证文案（order 域 T+1 统计）：门槛/取整见 SocialProofFormatter，只命中社证的 SPU 也产出标签行 */
	private void fillSocialProof(Map<String, GoodsCardTagVO> bySpu, String tenantId, List<String> wanted) {
		try {
			List<GoodsSpuSalesStatDTO> stats = remoteGoodsSalesStatService.latestStats(tenantId, wanted);
			if (stats == null) {
				return;
			}
			for (GoodsSpuSalesStatDTO stat : stats) {
				if (stat == null || !StringUtils.hasText(stat.getSpuId())) {
					continue;
				}
				SocialProofFormatter.Proof proof = SocialProofFormatter.socialProof(
						stat.getBuyerCnt(), stat.getRepeatBuyerCnt(), stat.getRepurchaseRate(),
						minBuyerCnt, minRepurchaseRate);
				if (proof == null) {
					continue;
				}
				GoodsCardTagVO vo = bySpu.computeIfAbsent(stat.getSpuId(), key -> {
					GoodsCardTagVO created = new GoodsCardTagVO();
					created.setSpuId(key);
					return created;
				});
				vo.setSocialProofText(proof.text());
				vo.setSocialProofKind(proof.kind());
			}
		}
		catch (Exception ex) {
			log.warn("商品卡社证文案查询失败，降级 spuCount={}", wanted.size(), ex);
		}
	}

	/**
	 * 近N天最低价（product 本域价格快照窗口判定，零远程）。
	 *
	 * <p>SPU 级打标按「主价驱动 SKU」判定：当日快照价最低的 SKU 自身满足三条件
	 * 才出标——展示价与声明严格对应，不因其它 SKU 的历史低价误标。
	 * 窗口聚合仍包含当天不在售 SKU 的历史行（窗口最低价取全量历史，保守方向）。
	 * 当日无快照行（Job 未跑/当天不在售）的 SKU 不参与主价。
	 */
	private void fillPriceTag(Map<String, GoodsCardTagVO> bySpu, String tenantId, List<String> wanted) {
		if (!priceTagEnabled || priceTagWindowDays <= 0) {
			return;
		}
		try {
			List<GoodsSpu> spus = goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
				.eq(GoodsSpu::getTenantId, tenantId)
				.in(GoodsSpu::getId, wanted)
				.select(GoodsSpu::getId, GoodsSpu::getOriginalPrice));
			if (spus.isEmpty()) {
				return;
			}
			List<String> spuIds = spus.stream().map(GoodsSpu::getId).filter(StringUtils::hasText).toList();
			List<GoodsSku> skus = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
				.eq(GoodsSku::getTenantId, tenantId)
				.in(GoodsSku::getSpuId, spuIds)
				.select(GoodsSku::getId, GoodsSku::getSpuId));
			if (skus.isEmpty()) {
				return;
			}
			Map<String, List<GoodsSku>> skusBySpu = new HashMap<>();
			List<String> skuIds = new ArrayList<>();
			for (GoodsSku sku : skus) {
				if (sku == null || !StringUtils.hasText(sku.getId()) || !StringUtils.hasText(sku.getSpuId())) {
					continue;
				}
				skusBySpu.computeIfAbsent(sku.getSpuId(), key -> new ArrayList<>()).add(sku);
				skuIds.add(sku.getId());
			}
			LocalDate today = LocalDate.now();
			List<SkuPriceWindowStat> stats = goodsSkuPriceDailyMapper.selectWindowStats(
					tenantId, skuIds, today.minusDays(priceTagWindowDays), today);
			Map<String, SkuPriceWindowStat> statBySku = new HashMap<>();
			for (SkuPriceWindowStat stat : stats) {
				if (stat != null && StringUtils.hasText(stat.getSkuId())) {
					statBySku.put(stat.getSkuId(), stat);
				}
			}
			String text = "近" + priceTagWindowDays + "天最低价";
			for (GoodsSpu spu : spus) {
				if (spu == null || !StringUtils.hasText(spu.getId())) {
					continue;
				}
				if (!judgePriceTag(spu, skusBySpu.get(spu.getId()), statBySku)) {
					continue;
				}
				GoodsCardTagVO vo = bySpu.computeIfAbsent(spu.getId(), key -> {
					GoodsCardTagVO created = new GoodsCardTagVO();
					created.setSpuId(key);
					return created;
				});
				vo.setPriceTagText(text);
			}
		}
		catch (Exception ex) {
			log.warn("商品卡近N天最低价判定失败，降级 spuCount={}", wanted.size(), ex);
		}
	}

	/** 三条件判定：主价驱动 SKU 的当日价 ≤ 窗口最低价、变过价（kinds≥2）、低于 SPU 原价 */
	private boolean judgePriceTag(GoodsSpu spu, List<GoodsSku> skus, Map<String, SkuPriceWindowStat> statBySku) {
		if (spu == null || spu.getOriginalPrice() == null || skus == null || skus.isEmpty()) {
			return false;
		}
		BigDecimal displayed = null;
		BigDecimal windowMin = null;
		boolean tagOk = false;
		for (GoodsSku sku : skus) {
			SkuPriceWindowStat stat = sku == null ? null : statBySku.get(sku.getId());
			if (stat == null) {
				continue;
			}
			if (stat.getMinPrice() != null
					&& (windowMin == null || stat.getMinPrice().compareTo(windowMin) < 0)) {
				// 窗口最低价取全量历史（含当天不在售 SKU）——「近N天最低」声明必须对全窗口成立
				windowMin = stat.getMinPrice();
			}
			BigDecimal todayPrice = stat.getTodayPrice();
			if (todayPrice == null || todayPrice.signum() <= 0) {
				// 当日无快照行（Job 未跑/当天不在售）：不参与主价
				continue;
			}
			boolean qualifies = stat.getMinPrice() != null
					&& todayPrice.compareTo(stat.getMinPrice()) <= 0
					&& stat.getPriceKinds() != null && stat.getPriceKinds() >= 2;
			if (displayed == null || todayPrice.compareTo(displayed) < 0) {
				// 主价驱动 SKU 变更：重置判定（展示价与声明严格对应）
				displayed = todayPrice;
				tagOk = qualifies;
			}
			else if (todayPrice.compareTo(displayed) == 0) {
				// 同价多 SKU：任一满足即视为驱动 SKU 满足
				tagOk = tagOk || qualifies;
			}
		}
		if (!tagOk || displayed == null) {
			return false;
		}
		// 展示价必须就是全窗口最低（displayed ≥ windowMin 恒成立，等号才声明成立）
		if (windowMin == null || displayed.compareTo(windowMin) > 0) {
			return false;
		}
		// 有划线空间才有意义：当日价不低于原价不出标（与卡片划线口径一致）
		return displayed.compareTo(spu.getOriginalPrice()) < 0;
	}

	/** 当前登录用户 ID；未登录/读取失败返回 null（未登录按潜在新人口径下发新人价） */
	private String currentUserId() {
		try {
			ArynUser user = SecurityUtils.getUser();
			return user != null ? user.getUserId() : null;
		}
		catch (Exception ex) {
			return null;
		}
	}

}
