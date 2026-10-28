package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.promotion.api.dto.DiscountActivityDTO;
import com.aryn.cloud.promotion.api.dto.DiscountGoodsDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.api.vo.AppDiscountActivityVO;
import com.aryn.cloud.promotion.api.vo.AppDiscountGoodsVO;
import com.aryn.cloud.promotion.api.vo.AppDiscountVO;
import com.aryn.cloud.promotion.api.vo.DiscountActivityVO;
import com.aryn.cloud.promotion.api.vo.DiscountGoodsVO;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import com.aryn.cloud.promotion.service.IDiscountGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountActivityServiceImpl extends ServiceImpl<DiscountActivityMapper, DiscountActivity>
		implements IDiscountActivityService {

	/** 批量查价接口单次最多查询的 SKU 数（免登接口，防超长参数放大） */
	private static final int MAX_PRICE_QUERY_SKUS = 200;

	private final IDiscountGoodsService discountGoodsService;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	@Override
	public IPage<DiscountActivity> getAdminPage(Page page, DiscountActivity activity) {
		return baseMapper.selectAdminPage(page, activity);
	}

	@Override
	public DiscountActivityVO getDetail(String id) {
		DiscountActivity activity = this.getById(id);
		if (activity == null) {
			return null;
		}
		DiscountActivityVO vo = new DiscountActivityVO();
		BeanUtils.copyProperties(activity, vo);
		if (activity.getStock() != null) {
			vo.setRemainingStock(activity.getStock()
					- (activity.getSoldCount() == null ? 0 : activity.getSoldCount()));
		}
		List<DiscountGoods> goodsList = discountGoodsService.listByActivityId(id);
		vo.setGoodsCount((long) goodsList.size());
		List<DiscountGoodsVO> goodsVOs = goodsList.stream().map(goods -> {
			DiscountGoodsVO goodsVO = new DiscountGoodsVO();
			BeanUtils.copyProperties(goods, goodsVO);
			// 剩余 = 限量 − 已占用；未配置限量（stock=null）时保持 null，前端按「不限量」展示
			if (goods.getStock() != null) {
				goodsVO.setRemainingStock(goods.getStock() - (goods.getSoldCount() == null ? 0 : goods.getSoldCount()));
			}
			return goodsVO;
		}).collect(Collectors.toList());
		backfillSpuInfo(goodsVOs);
		vo.setGoodsList(goodsVOs);
		return vo;
	}

	/**
	 * 回填商品名称与图片：discount_goods 只存 ID，不回填时编辑页商品信息列只能兜底显示一串 SPU ID。
	 * 查询失败不阻断详情，降级为仅返回 ID（与秒杀活动详情同一口径）。
	 */
	private void backfillSpuInfo(List<DiscountGoodsVO> goodsVOs) {
		List<String> spuIds = goodsVOs.stream()
			.map(DiscountGoodsVO::getSpuId)
			.filter(Objects::nonNull)
			.distinct()
			.collect(Collectors.toList());
		if (spuIds.isEmpty()) {
			return;
		}
		List<GoodsSpu> spus;
		try {
			spus = remoteGoodsSpuService.getSpuByIds(spuIds);
		} catch (Exception exception) {
			log.warn("折扣活动详情回填商品名称失败，降级为仅返回商品ID, spuIds={}", spuIds, exception);
			return;
		}
		if (CollectionUtils.isEmpty(spus)) {
			return;
		}
		Map<String, GoodsSpu> spuMap = spus.stream()
			.collect(Collectors.toMap(GoodsSpu::getId, spu -> spu, (a, b) -> a));
		goodsVOs.forEach(goodsVO -> {
			GoodsSpu spu = spuMap.get(goodsVO.getSpuId());
			if (spu != null) {
				goodsVO.setSpuName(spu.getName());
				goodsVO.setSpuUrls(spu.getSpuUrls());
			}
		});
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveWithGoods(DiscountActivityDTO dto) {
		DiscountActivity activity = new DiscountActivity();
		BeanUtils.copyProperties(dto, activity);
		activity.setStatus(0);
		if (activity.getScope() == null) {
			activity.setScope(1);
		}
		// 叠加策略缺省按折扣类型默认值（可用）补齐，避免依赖列默认值导致口径漂移
		if (!StringUtils.hasText(activity.getCouponStackable())) {
			activity.setCouponStackable("1");
		}
		if (!StringUtils.hasText(activity.getPointsStackable())) {
			activity.setPointsStackable("1");
		}
		boolean saved = this.save(activity);
		if (!saved) {
			throw new ArynBusinessException("保存折扣活动失败");
		}
		// scope=2 时保存指定商品
		if (activity.getScope() == 2 && !CollectionUtils.isEmpty(dto.getGoodsList())) {
			saveGoods(activity.getId(), dto.getGoodsList(), Map.of());
		}
		log.info("折扣活动创建成功, activityId={}, activityName={}", activity.getId(), dto.getActivityName());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateWithGoods(DiscountActivityDTO dto) {
		if (dto.getId() == null) {
			throw new ArynBusinessException("活动ID不能为空");
		}
		DiscountActivity activity = this.getById(dto.getId());
		if (activity == null) {
			throw new ArynBusinessException("折扣活动不存在");
		}
		BeanUtils.copyProperties(dto, activity);
		// 叠加策略缺省按折扣类型默认值（可用）补齐；显式提交的值（含关闭）必须生效
		if (!StringUtils.hasText(activity.getCouponStackable())) {
			activity.setCouponStackable("1");
		}
		if (!StringUtils.hasText(activity.getPointsStackable())) {
			activity.setPointsStackable("1");
		}
		// 活动级限量不能低于已售：否则剩余为负，且删掉的额度无处追回
		if (activity.getStock() != null && activity.getSoldCount() != null
				&& activity.getStock() < activity.getSoldCount()) {
			throw new ArynBusinessException("活动限量不能小于已售数量（已售 " + activity.getSoldCount() + " 件）");
		}
		boolean updated = this.updateById(activity);
		if (!updated) {
			throw new ArynBusinessException("更新折扣活动失败");
		}
		// 删除前记下各 SKU 的已占用数量：编辑活动只改配置，不能把已售清零（会凭空多卖）
		Map<String, Integer> soldBySku = discountGoodsService.listByActivityId(activity.getId()).stream()
			.filter(row -> row.getSkuId() != null)
			.collect(Collectors.toMap(DiscountGoods::getSkuId,
					row -> row.getSoldCount() == null ? 0 : row.getSoldCount(), (a, b) -> a));
		// 已产生销量的商品不能被移除：占用明细还指向它，删掉后取消/退款回补不到（P3-03）
		Set<String> incomingSkuIds = new HashSet<>();
		if (activity.getScope() == 2 && !CollectionUtils.isEmpty(dto.getGoodsList())) {
			dto.getGoodsList().stream()
				.map(DiscountGoodsDTO::getSkuId)
				.filter(Objects::nonNull)
				.forEach(incomingSkuIds::add);
		}
		soldBySku.forEach((skuId, sold) -> {
			if (sold > 0 && !incomingSkuIds.contains(skuId)) {
				String tail = skuId.length() > 6 ? skuId.substring(skuId.length() - 6) : skuId;
				throw new ArynBusinessException("商品（SKU 尾号 " + tail + "）已有 " + sold
						+ " 件售出，不能移除或转为全场活动；请等活动结束后再调整");
			}
		});
		// 删除原有商品关联
		discountGoodsService.remove(Wrappers.<DiscountGoods>lambdaQuery()
				.eq(DiscountGoods::getActivityId, activity.getId()));
		// 重新保存商品：沿用已占用数量
		if (activity.getScope() == 2 && !CollectionUtils.isEmpty(dto.getGoodsList())) {
			saveGoods(activity.getId(), dto.getGoodsList(), soldBySku);
		}
		log.info("折扣活动更新成功, activityId={}", dto.getId());
		return true;
	}

	@Override
	public boolean updateStatus(String id, Integer status) {
		DiscountActivity activity = this.getById(id);
		if (activity == null || status == null) {
			return false;
		}
		// 传 0 表示启用：直接按时间窗落到最终状态，不能停在 0 等定时任务补成 1
		int targetStatus = status == 0 ? resolveEnabledStatus(activity, LocalDateTime.now()) : status;
		return this.update(Wrappers.<DiscountActivity>lambdaUpdate()
				.eq(DiscountActivity::getId, id)
				.set(DiscountActivity::getStatus, targetStatus));
	}

	/** 启用后的目标状态，与 C 端 status=1 + 时间窗的过滤口径保持一致 */
	private int resolveEnabledStatus(DiscountActivity activity, LocalDateTime now) {
		if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
			return 0;
		}
		if (activity.getEndTime() != null && !now.isBefore(activity.getEndTime())) {
			return 2;
		}
		return 1;
	}

	@Override
	public IPage<AppDiscountActivityVO> getActiveActivityPage(Page page) {
		LocalDateTime now = LocalDateTime.now();
		IPage<DiscountActivity> activityPage = this.page(page, Wrappers.<DiscountActivity>lambdaQuery()
				.eq(DiscountActivity::getStatus, 1)
				.le(DiscountActivity::getStartTime, now)
				.gt(DiscountActivity::getEndTime, now)
				.orderByDesc(DiscountActivity::getCreateTime)
				.orderByDesc(DiscountActivity::getId));
		Map<String, List<DiscountGoods>> goodsByActivity = loadGoodsByActivity(activityPage.getRecords());
		Map<String, GoodsSku> skuMap = loadSkuMap(goodsByActivity);
		return activityPage.convert(activity -> toAppDiscountActivity(activity, goodsByActivity, skuMap, now));
	}

	@Override
	public AppDiscountVO getGoodsDiscountInfo(String skuId) {
		LocalDateTime now = LocalDateTime.now();
		// 查询进行中的折扣活动
		List<DiscountActivity> activities = this.list(Wrappers.<DiscountActivity>lambdaQuery()
				.eq(DiscountActivity::getStatus, 1)
				.le(DiscountActivity::getStartTime, now)
				.gt(DiscountActivity::getEndTime, now));
		if (CollectionUtils.isEmpty(activities)) {
			return null;
		}
		// 查询商品信息
		List<GoodsSku> skus = remoteGoodsSkuService.getBySkuIds(List.of(skuId));
		if (CollectionUtils.isEmpty(skus)) {
			return null;
		}
		GoodsSku sku = skus.get(0);
		BigDecimal originalPrice = sku.getSalesPrice();
		// 找到最优折扣（价格最低）
		AppDiscountVO bestVO = null;
		BigDecimal bestPrice = originalPrice;
		for (DiscountActivity activity : activities) {
			boolean match = false;
			if (activity.getScope() == 1) {
				// 全场折扣
				match = true;
			} else {
				// 指定商品折扣
				long count = discountGoodsService.count(Wrappers.<DiscountGoods>lambdaQuery()
						.eq(DiscountGoods::getActivityId, activity.getId())
						.eq(DiscountGoods::getSkuId, skuId));
				match = count > 0;
			}
			if (!match) {
				continue;
			}
			BigDecimal discountPrice = calculateDiscountPrice(originalPrice,
					activity.getDiscountType(), activity.getDiscountValue());
			if (discountPrice.compareTo(bestPrice) < 0) {
				bestPrice = discountPrice;
				bestVO = new AppDiscountVO();
				bestVO.setActivityId(activity.getId());
				bestVO.setActivityName(activity.getActivityName());
				bestVO.setDiscountType(activity.getDiscountType());
				bestVO.setDiscountValue(activity.getDiscountValue());
				bestVO.setSpuId(sku.getSpuId());
				bestVO.setSkuId(skuId);
				bestVO.setGoodsImage(sku.getPicUrl());
				bestVO.setOriginalPrice(originalPrice);
				bestVO.setDiscountPrice(discountPrice);
			}
		}
		return bestVO;
	}

	@Override
	public List<AppDiscountVO> getGoodsDiscountPrices(List<String> skuIds) {
		Set<String> wanted = normalizeQuerySkuIds(skuIds);
		if (wanted.isEmpty()) {
			return List.of();
		}
		LocalDateTime now = LocalDateTime.now();
		// 1) 一次查出全部进行中活动：后续在内存里做 SKU×活动匹配，禁止按 SKU 循环查库
		List<DiscountActivity> activities = this.list(Wrappers.<DiscountActivity>lambdaQuery()
				.eq(DiscountActivity::getStatus, 1)
				.le(DiscountActivity::getStartTime, now)
				.gt(DiscountActivity::getEndTime, now));
		if (CollectionUtils.isEmpty(activities)) {
			return List.of();
		}
		// 2) 一次查出指定商品型活动对本批 SKU 的命中关系（key=活动ID，value=命中 SKU 集合）
		Map<String, Set<String>> matchedSkuIdsByActivity = loadMatchedSkuIdsByActivity(activities, wanted);
		boolean fullScopeActivityExists = activities.stream()
				.anyMatch(activity -> Integer.valueOf(1).equals(activity.getScope()));
		boolean scopedActivityMatched = matchedSkuIdsByActivity.values().stream()
				.anyMatch(matched -> !matched.isEmpty());
		// 本批 SKU 没有命中任何活动时不再打远程商品库，免登接口不放大无谓的下游调用
		if (!fullScopeActivityExists && !scopedActivityMatched) {
			return List.of();
		}
		// 3) 一次批量取商品（销售价 / 名称 / 图片），批量口径与单 SKU 接口一致：基准价 = salesPrice
		List<GoodsSku> skus = remoteGoodsSkuService.getBySkuIds(new ArrayList<>(wanted));
		if (CollectionUtils.isEmpty(skus)) {
			return List.of();
		}
		Map<String, GoodsSku> skuMap = skus.stream()
				.filter(sku -> sku.getId() != null)
				.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		List<AppDiscountVO> result = new ArrayList<>();
		for (String skuId : wanted) {
			GoodsSku sku = skuMap.get(skuId);
			// 商品库查不到 / 无销售价的 SKU 不标注折扣，由调用方回落展示原价
			if (sku == null || sku.getSalesPrice() == null) {
				continue;
			}
			AppDiscountVO bestVO = calculateBestDiscount(sku, skuId, activities, matchedSkuIdsByActivity);
			if (bestVO != null) {
				result.add(bestVO);
			}
		}
		log.debug("批量查询折扣价完成, 请求SKU数={}, 命中数={}", wanted.size(), result.size());
		return result;
	}

	/**
	 * 入参归一化：剔除空值、按请求顺序去重，并限制查询规模。
	 *
	 * <p>上限与新人价批量接口（200 条）同口径：本接口免登可访问，不限制的话
	 * 一个超长 skuIds 就能放大成巨型 IN 条件与一次超大远程批量调用。
	 */
	private Set<String> normalizeQuerySkuIds(List<String> skuIds) {
		if (CollectionUtils.isEmpty(skuIds)) {
			return Set.of();
		}
		long validCount = skuIds.stream().filter(StringUtils::hasText).count();
		if (validCount > MAX_PRICE_QUERY_SKUS) {
			log.warn("批量折扣价查询 SKU 数超过上限，已截断, requested={}, limit={}", validCount, MAX_PRICE_QUERY_SKUS);
		}
		Set<String> wanted = new LinkedHashSet<>();
		for (String skuId : skuIds) {
			if (wanted.size() >= MAX_PRICE_QUERY_SKUS) {
				break;
			}
			if (StringUtils.hasText(skuId)) {
				wanted.add(skuId.trim());
			}
		}
		return wanted;
	}

	/**
	 * 一次批量查询「指定商品型（scope=2）活动 × 本批 SKU」的命中关系，避免逐活动 count 造成 N+1。
	 *
	 * <p>全场型（scope=1）活动不挂 discount_goods，无需查询；仅当存在 scope=2 活动时才打库。
	 */
	private Map<String, Set<String>> loadMatchedSkuIdsByActivity(List<DiscountActivity> activities,
			Set<String> wanted) {
		List<String> scopedActivityIds = activities.stream()
				.filter(activity -> Integer.valueOf(2).equals(activity.getScope()))
				.map(DiscountActivity::getId)
				.filter(Objects::nonNull)
				.toList();
		if (scopedActivityIds.isEmpty()) {
			return Map.of();
		}
		return discountGoodsService.list(Wrappers.<DiscountGoods>lambdaQuery()
					.in(DiscountGoods::getActivityId, scopedActivityIds)
					.in(DiscountGoods::getSkuId, wanted))
				.stream()
				.filter(row -> row.getActivityId() != null && wanted.contains(row.getSkuId()))
				.collect(Collectors.groupingBy(DiscountGoods::getActivityId,
						Collectors.mapping(DiscountGoods::getSkuId, Collectors.toSet())));
	}

	/**
	 * 在已装载的活动与命中关系里挑单个 SKU 的最优折扣，口径与 {@link #getGoodsDiscountInfo(String)} 一致：
	 * 折后价必须严格低于当前最优价（初始为销售价），因此折后价 ≥ 原价或配置非法时不会出现在结果里。
	 */
	private AppDiscountVO calculateBestDiscount(GoodsSku sku, String skuId,
			List<DiscountActivity> activities, Map<String, Set<String>> matchedSkuIdsByActivity) {
		BigDecimal originalPrice = sku.getSalesPrice();
		AppDiscountVO bestVO = null;
		BigDecimal bestPrice = originalPrice;
		for (DiscountActivity activity : activities) {
			// 折扣类型/折扣值为空属非法配置：跳过，避免 switch 拆箱 NPE 把整个批量接口打挂
			if (activity.getDiscountType() == null || activity.getDiscountValue() == null) {
				continue;
			}
			boolean match = Integer.valueOf(1).equals(activity.getScope())
					|| matchedSkuIdsByActivity.getOrDefault(activity.getId(), Set.of()).contains(skuId);
			if (!match) {
				continue;
			}
			BigDecimal discountPrice = calculateDiscountPrice(originalPrice,
					activity.getDiscountType(), activity.getDiscountValue());
			if (discountPrice.compareTo(bestPrice) >= 0) {
				continue;
			}
			bestPrice = discountPrice;
			bestVO = new AppDiscountVO();
			bestVO.setActivityId(activity.getId());
			bestVO.setActivityName(activity.getActivityName());
			bestVO.setDiscountType(activity.getDiscountType());
			bestVO.setDiscountValue(activity.getDiscountValue());
			bestVO.setSpuId(sku.getSpuId());
			bestVO.setSkuId(skuId);
			bestVO.setGoodsName(sku.getGoodsSpu() != null ? sku.getGoodsSpu().getName() : null);
			bestVO.setGoodsImage(sku.getPicUrl());
			bestVO.setOriginalPrice(originalPrice);
			bestVO.setDiscountPrice(discountPrice);
		}
		return bestVO;
	}

	@Override
	public BigDecimal calculatePrice(String skuId, BigDecimal originalPrice) {
		AppDiscountVO vo = getGoodsDiscountInfo(skuId);
		if (vo == null) {
			return originalPrice;
		}
		return vo.getDiscountPrice();
	}

	@Override
	public com.aryn.cloud.promotion.api.vo.DiscountPriceContextVO getPriceContext(String skuId,
			BigDecimal originalPrice) {
		AppDiscountVO vo = getGoodsDiscountInfo(skuId);
		if (vo == null) {
			return null;
		}
		com.aryn.cloud.promotion.api.vo.DiscountPriceContextVO context =
				new com.aryn.cloud.promotion.api.vo.DiscountPriceContextVO();
		context.setSkuId(skuId);
		context.setActivityId(vo.getActivityId());
		context.setDiscountPrice(vo.getDiscountPrice());
		DiscountActivity activity = vo.getActivityId() == null ? null : this.getById(vo.getActivityId());
		context.setCouponStackable(com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.resolve(
				activity == null ? null : activity.getCouponStackable(),
				com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.DISCOUNT.isDefaultCouponStackable()));
		context.setPointsStackable(com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.resolve(
				activity == null ? null : activity.getPointsStackable(),
				com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.DISCOUNT.isDefaultPointsStackable()));
		return context;
	}

	@Override
	public void refreshStatus() {
		LocalDateTime now = LocalDateTime.now();
		// 未开始 → 进行中
		this.update(Wrappers.<DiscountActivity>lambdaUpdate()
				.eq(DiscountActivity::getStatus, 0)
				.le(DiscountActivity::getStartTime, now)
				.gt(DiscountActivity::getEndTime, now)
				.set(DiscountActivity::getStatus, 1));
		// 进行中 → 已结束
		this.update(Wrappers.<DiscountActivity>lambdaUpdate()
				.eq(DiscountActivity::getStatus, 1)
				.le(DiscountActivity::getEndTime, now)
				.set(DiscountActivity::getStatus, 2));
		log.info("折扣活动状态刷新完成, time={}", now);
	}

	// ==================== 私有方法 ====================

	private void saveGoods(String activityId, List<DiscountGoodsDTO> goodsList, Map<String, Integer> soldBySku) {
		List<DiscountGoods> list = new ArrayList<>();
		for (DiscountGoodsDTO dto : goodsList) {
			DiscountGoods goods = new DiscountGoods();
			BeanUtils.copyProperties(dto, goods);
			goods.setActivityId(activityId);
			// 编辑活动时沿用已占用数量；新建活动为 0
			goods.setSoldCount(soldBySku.getOrDefault(dto.getSkuId(), 0));
			if (goods.getStock() != null && goods.getStock() < goods.getSoldCount()) {
				throw new ArynBusinessException("折扣商品限量不能小于已售数量");
			}
			list.add(goods);
		}
		discountGoodsService.saveBatch(list);
	}

	/** 批量装载本页活动的折扣商品，避免逐活动查询；全场(scope=1)活动不挂商品 */
	private Map<String, List<DiscountGoods>> loadGoodsByActivity(List<DiscountActivity> activities) {
		if (CollectionUtils.isEmpty(activities)) {
			return Map.of();
		}
		List<String> scopedIds = activities.stream()
				.filter(activity -> activity.getScope() != null && activity.getScope() == 2)
				.map(DiscountActivity::getId)
				.toList();
		if (scopedIds.isEmpty()) {
			return Map.of();
		}
		return discountGoodsService.list(Wrappers.<DiscountGoods>lambdaQuery()
					.in(DiscountGoods::getActivityId, scopedIds))
				.stream()
				.collect(Collectors.groupingBy(DiscountGoods::getActivityId));
	}

	private Map<String, GoodsSku> loadSkuMap(Map<String, List<DiscountGoods>> goodsByActivity) {
		Set<String> skuIds = goodsByActivity.values().stream()
				.flatMap(List::stream)
				.map(DiscountGoods::getSkuId)
				.collect(Collectors.toSet());
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			return remoteGoodsSkuService.getBySkuIds(new ArrayList<>(skuIds)).stream()
					.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		} catch (Exception e) {
			// 商品信息不可用不应让整个会场挂掉：活动照常返回，仅商品项降级
			log.warn("查询折扣活动商品SKU失败, skuCount={}", skuIds.size(), e);
			return Map.of();
		}
	}

	private AppDiscountActivityVO toAppDiscountActivity(DiscountActivity activity,
			Map<String, List<DiscountGoods>> goodsByActivity, Map<String, GoodsSku> skuMap, LocalDateTime now) {
		AppDiscountActivityVO vo = new AppDiscountActivityVO();
		BeanUtils.copyProperties(activity, vo);
		vo.setActivityId(activity.getId());
		// 全场型活动的额度挂在活动上（指定商品型由商品项各自带 remainingStock）
		if (activity.getScope() != null && activity.getScope() == 1 && activity.getStock() != null) {
			vo.setRemainingStock(Math.max(0, activity.getStock()
					- (activity.getSoldCount() == null ? 0 : activity.getSoldCount())));
		}
		vo.setCountdown(countdownSeconds(activity, now));
		List<DiscountGoods> goodsList = goodsByActivity.getOrDefault(activity.getId(), List.of());
		vo.setGoodsList(goodsList.stream()
				.map(goods -> toAppDiscountGoods(goods, skuMap.get(goods.getSkuId()), activity))
				.filter(Objects::nonNull)
				.collect(Collectors.toList()));
		return vo;
	}

	/** 商品项在售校验失败（已下架/删除）时返回 null，由调用方过滤，避免渲染空卡片 */
	private AppDiscountGoodsVO toAppDiscountGoods(DiscountGoods goods, GoodsSku sku, DiscountActivity activity) {
		if (sku == null) {
			return null;
		}
		AppDiscountGoodsVO vo = new AppDiscountGoodsVO();
		vo.setSpuId(sku.getSpuId() != null ? sku.getSpuId() : goods.getSpuId());
		vo.setSkuId(goods.getSkuId());
		vo.setDiscountType(activity.getDiscountType());
		vo.setDiscountValue(activity.getDiscountValue());
		vo.setGoodsImage(sku.getPicUrl());
		vo.setGoodsName(sku.getGoodsSpu() != null ? sku.getGoodsSpu().getName() : null);
		vo.setSubTitle(sku.getGoodsSpu() != null ? sku.getGoodsSpu().getSubTitle() : null);
		// 折扣以销售价为基准，与商品详情页 getGoodsDiscountInfo 的口径保持一致
		BigDecimal originalPrice = sku.getSalesPrice();
		vo.setOriginalPrice(originalPrice);
		vo.setDiscountPrice(calculateDiscountPrice(originalPrice,
				activity.getDiscountType(), activity.getDiscountValue()));
		// 商品级限量：stock 为 null 表示不限量，C 端隐藏「仅剩 N 件」
		vo.setStock(goods.getStock());
		if (goods.getStock() != null) {
			vo.setRemainingStock(Math.max(0, goods.getStock()
					- (goods.getSoldCount() == null ? 0 : goods.getSoldCount())));
		}
		return vo;
	}

	/** 倒计时：进行中取距结束，未开始取距开始，其余为 0 */
	private Long countdownSeconds(DiscountActivity activity, LocalDateTime now) {
		if (activity.getStatus() == null) {
			return 0L;
		}
		if (activity.getStatus() == 1 && activity.getEndTime() != null) {
			return Math.max(0, Duration.between(now, activity.getEndTime()).getSeconds());
		}
		if (activity.getStatus() == 0 && activity.getStartTime() != null) {
			return Math.max(0, Duration.between(now, activity.getStartTime()).getSeconds());
		}
		return 0L;
	}

	/**
	 * 计算折扣价
	 *
	 * @param originalPrice  原价
	 * @param discountType   折扣类型:1打折 2减价 3固定价
	 * @param discountValue  折扣值
	 */
	public static BigDecimal calculateDiscountPrice(BigDecimal originalPrice,
			Integer discountType, BigDecimal discountValue) {
		BigDecimal result;
		switch (discountType) {
			case 1:
				// 打折：discountValue 不能大于 1（不允许加价）
				if (discountValue != null && discountValue.compareTo(BigDecimal.ONE) > 0) {
					log.warn("打折值非法(>1), 已回退为原价, discountValue={}", discountValue);
					result = originalPrice;
				} else {
					result = originalPrice.multiply(discountValue);
				}
				break;
			case 2:
				// 减价
				result = originalPrice.subtract(discountValue);
				break;
			case 3:
				// 固定价
				result = discountValue;
				break;
			default:
				result = originalPrice;
		}
		// 校验负数：折扣价不能小于 0
		if (result == null || result.compareTo(BigDecimal.ZERO) < 0) {
			result = BigDecimal.ZERO;
		}
		// 保留两位小数，向下取整
		return result.setScale(2, RoundingMode.DOWN);
	}
}