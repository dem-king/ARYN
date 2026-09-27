package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

	private final IDiscountGoodsService discountGoodsService;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

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
		List<DiscountGoods> goodsList = discountGoodsService.listByActivityId(id);
		vo.setGoodsCount((long) goodsList.size());
		List<DiscountGoodsVO> goodsVOs = goodsList.stream().map(goods -> {
			DiscountGoodsVO goodsVO = new DiscountGoodsVO();
			BeanUtils.copyProperties(goods, goodsVO);
			return goodsVO;
		}).collect(Collectors.toList());
		vo.setGoodsList(goodsVOs);
		return vo;
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
		boolean saved = this.save(activity);
		if (!saved) {
			throw new ArynBusinessException("保存折扣活动失败");
		}
		// scope=2 时保存指定商品
		if (activity.getScope() == 2 && !CollectionUtils.isEmpty(dto.getGoodsList())) {
			saveGoods(activity.getId(), dto.getGoodsList());
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
		boolean updated = this.updateById(activity);
		if (!updated) {
			throw new ArynBusinessException("更新折扣活动失败");
		}
		// 删除原有商品关联
		discountGoodsService.remove(Wrappers.<DiscountGoods>lambdaQuery()
				.eq(DiscountGoods::getActivityId, activity.getId()));
		// 重新保存商品
		if (activity.getScope() == 2 && !CollectionUtils.isEmpty(dto.getGoodsList())) {
			saveGoods(activity.getId(), dto.getGoodsList());
		}
		log.info("折扣活动更新成功, activityId={}", dto.getId());
		return true;
	}

	@Override
	public boolean updateStatus(String id, Integer status) {
		return this.update(Wrappers.<DiscountActivity>lambdaUpdate()
				.eq(DiscountActivity::getId, id)
				.set(DiscountActivity::getStatus, status));
	}

	@Override
	public IPage<AppDiscountActivityVO> getActiveActivityPage(Page page) {
		LocalDateTime now = LocalDateTime.now();
		IPage<DiscountActivity> activityPage = this.page(page, Wrappers.<DiscountActivity>lambdaQuery()
				.eq(DiscountActivity::getStatus, 1)
				.le(DiscountActivity::getStartTime, now)
				.gt(DiscountActivity::getEndTime, now)
				.orderByDesc(DiscountActivity::getCreateTime));
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
	public BigDecimal calculatePrice(String skuId, BigDecimal originalPrice) {
		AppDiscountVO vo = getGoodsDiscountInfo(skuId);
		if (vo == null) {
			return originalPrice;
		}
		return vo.getDiscountPrice();
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

	private void saveGoods(String activityId, List<DiscountGoodsDTO> goodsList) {
		List<DiscountGoods> list = new ArrayList<>();
		for (DiscountGoodsDTO dto : goodsList) {
			DiscountGoods goods = new DiscountGoods();
			BeanUtils.copyProperties(dto, goods);
			goods.setActivityId(activityId);
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
		// 折扣以销售价为基准，与商品详情页 getGoodsDiscountInfo 的口径保持一致
		BigDecimal originalPrice = sku.getSalesPrice();
		vo.setOriginalPrice(originalPrice);
		vo.setDiscountPrice(calculateDiscountPrice(originalPrice,
				activity.getDiscountType(), activity.getDiscountValue()));
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