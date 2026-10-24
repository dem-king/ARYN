package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.PurchaseSceneEnum;
import com.aryn.cloud.product.api.constant.ProductConstants;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.promotion.api.vo.SeckillPriceContextVO;
import com.aryn.cloud.promotion.api.vo.DiscountPriceContextVO;
import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.dto.DiscountStockReserveResultDTO;
import com.aryn.cloud.promotion.api.remote.RemoteDiscountService;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPriceComputeService {

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	@DubboReference
	private final RemoteDiscountService remoteDiscountService;

	@DubboReference
	private final RemoteSeckillService remoteSeckillService;

	@DubboReference
	private final RemotePromotionEngine remotePromotionEngine;

	/**
	 * 叠加策略总开关（灰度/回滚用）：false 时所有 SKU 视为可叠，等同现网行为，不读活动配置。
	 * 字段级默认 true（无 Spring 注入的纯构造场景，如单元测试，与生产默认一致）。
	 */
	@Value("${promotion.stack-policy.enabled:true}")
	private boolean stackPolicyEnabled = true;

	/**
	 * 最低实付兜底开关：券额封顶后保证商品实付 ≥ 最低支付金额，避免叠加击穿出 0 元单。
	 */
	@Value("${promotion.stack-policy.min-payable-enabled:true}")
	private boolean minPayableEnabled = true;

	/** 最低实付金额（元），业务自定策略（有赞口径为 0 元，不可对外声称行业惯例）。 */
	@Value("${promotion.stack-policy.min-payable-amount:0.01}")
	private BigDecimal minPayableAmount = new BigDecimal("0.01");

	/**
	 * 促销价格处理：拼团价 > 秒杀价 > 限时折扣 > 原价。
	 *
	 * <p>由下单/结算主流程在会员折扣与优惠券之前显式调用：券与会员折扣必须基于促销后
	 * 金额计算，若仍按原价基数抵扣，券额会超过商品实际应付（历史实现即在运费分支内调用，
	 * 导致自提/内配不生效、且券按原价抵扣）。
	 *
	 * @param orderItemEntityList 订单明细
	 */
	public void orderPromotionPriceHandler(List<OrderItemEntity> orderItemEntityList) {
		orderPromotionPriceHandler(orderItemEntityList, null, null);
	}

	/**
	 * 促销价格处理（带拼团上下文）：拼团价 > 秒杀价 > 限时折扣 > 原价。
	 *
	 * <p>拼团价仅对拼团记录绑定的 SKU 生效，由下单/结算主流程按拼团记录预取，
	 * 保证确认页与提交后金额一致；普通商品仍走秒杀/折扣链路。
	 *
	 * @param groupBuyContext 拼团下单上下文（可为 null，表示非拼团单）
	 */
	public void orderPromotionPriceHandler(List<OrderItemEntity> orderItemEntityList,
			GroupBuyOrderContextVO groupBuyContext) {
		orderPromotionPriceHandler(orderItemEntityList, groupBuyContext, null);
	}

	/**
	 * 促销价格处理（完整上下文）：拼团价 > 秒杀价 > 新人价 > 限时折扣 > 原价。
	 *
	 * <p>{@code newbieSkuIds} 中的 SKU 已被营销引擎写入新人价基价，
	 * 限时折扣分支必须跳过（折扣会无条件覆盖 salesPrice），否则新人价被冲掉；
	 * 秒杀仍优先覆盖（平台级爆款活动优先保护）。
	 *
	 * @param groupBuyContext 拼团下单上下文（可为 null，表示非拼团单）
	 * @param newbieSkuIds 命中新人价的 SKU 集合（可为 null/空，表示无新人价订单）
	 */
	public void orderPromotionPriceHandler(List<OrderItemEntity> orderItemEntityList,
			GroupBuyOrderContextVO groupBuyContext, Set<String> newbieSkuIds) {
		PromotionPricePlan plan = resolvePromotionPricePlan(orderItemEntityList, groupBuyContext, newbieSkuIds);
		applyPromotionPricePlan(orderItemEntityList, newbieSkuIds, plan);
	}

	/**
	 * 按下单前预取的活动价计划落价。
	 *
	 * <p>下单/结算主流程先调用 {@link #resolvePromotionPricePlan} 再走营销试算，
	 * 试算、落价与整船分摊必须共用同一份计划：既避免同一请求重复查询营销服务，
	 * 也保证整船门槛排除的 SKU 与实际成交价来源一致。
	 */
	public void applyPromotionPricePlan(List<OrderItemEntity> orderItemEntityList,
			Set<String> newbieSkuIds, PromotionPricePlan plan) {
		for (OrderItemEntity item : orderItemEntityList) {
			BigDecimal originalUnitPrice = item.getSalesPrice();
			if (originalUnitPrice == null) {
				// 未定价商品不做促销价处理，避免空指针
				continue;
			}
			boolean newbiePriced = newbieSkuIds != null && newbieSkuIds.contains(item.getSkuId());
			BigDecimal promotionUnitPrice = plan.groupBuyPrice(item.getSkuId());
			// 其次秒杀价（秒杀优先于新人价：平台级爆款活动优先保护）
			if (promotionUnitPrice == null) {
				promotionUnitPrice = plan.seckillPrice(item.getSkuId());
			}
			// 其次限时折扣（新人价 SKU 跳过：新人价已作为基价写入，折扣不得覆盖）
			if (promotionUnitPrice == null && !newbiePriced) {
				promotionUnitPrice = plan.discountPrice(item.getSkuId());
			}
			if (promotionUnitPrice == null) {
				promotionUnitPrice = originalUnitPrice;
			}
			// 促销价不能高于原价
			if (promotionUnitPrice.compareTo(originalUnitPrice) > 0) {
				promotionUnitPrice = originalUnitPrice;
			}
			item.setSalesPrice(promotionUnitPrice);
			item.setTotalPrice(promotionUnitPrice.multiply(BigDecimal.valueOf(item.getBuyQuantity())));
		}
	}

	/**
	 * 预取拼团/秒杀/限时折扣价，并标记已享活动价的 SKU（命中记录含活动类型与叠加策略）。
	 *
	 * <p>查询顺序与落价优先级一致：拼团命中后不再查秒杀/折扣，秒杀命中后不再查折扣。
	 * 营销试算用该标记把活动价 SKU 排除出整船门槛，订单分摊也据此排除；
	 * 例外是新人价：它由营销引擎在试算中落定，订单侧只需保证折扣不再覆盖，
	 * 其命中信息在试算后经 {@link #mergeStackPolicyHits} 补入计划。
	 */
	public PromotionPricePlan resolvePromotionPricePlan(List<OrderItemEntity> orderItemEntityList,
			GroupBuyOrderContextVO groupBuyContext) {
		return resolvePromotionPricePlan(orderItemEntityList, groupBuyContext, null);
	}

	private PromotionPricePlan resolvePromotionPricePlan(List<OrderItemEntity> orderItemEntityList,
			GroupBuyOrderContextVO groupBuyContext, Set<String> newbieSkuIds) {
		PromotionPricePlan plan = new PromotionPricePlan(groupBuyContext, stackPolicyEnabled);
		for (OrderItemEntity item : orderItemEntityList) {
			BigDecimal originalUnitPrice = item.getSalesPrice();
			if (originalUnitPrice == null) {
				continue;
			}
			BigDecimal groupBuyPrice = plan.groupBuyPrice(item.getSkuId());
			if (groupBuyPrice != null) {
				if (groupBuyPrice.compareTo(originalUnitPrice) < 0) {
					plan.markActivityPriced(item.getSkuId());
					plan.putHit(item.getSkuId(), StackActivityTypeEnum.GROUP_BUY, groupBuyContext.getActivityId(),
							groupBuyContext.getCouponStackable(), groupBuyContext.getPointsStackable());
				}
				continue;
			}
			SeckillPriceContextVO seckillContext = querySeckillContext(item.getSkuId());
			if (seckillContext != null && seckillContext.getSeckillPrice() != null) {
				plan.putSeckillPrice(item.getSkuId(), seckillContext.getSeckillPrice());
				if (seckillContext.getSeckillPrice().compareTo(originalUnitPrice) < 0) {
					plan.markActivityPriced(item.getSkuId());
					// 秒杀是平台级硬互斥：无论远程/配置给什么值，订单侧一律强制不可叠
					plan.putHit(item.getSkuId(), StackActivityTypeEnum.SECKILL, seckillContext.getActivityId(),
							false, false);
				}
				continue;
			}
			if (newbieSkuIds != null && newbieSkuIds.contains(item.getSkuId())) {
				continue;
			}
			DiscountPriceContextVO discountContext = queryDiscountContext(item.getSkuId(), originalUnitPrice);
			if (discountContext != null && discountContext.getDiscountPrice() != null) {
				plan.putDiscountPrice(item.getSkuId(), discountContext.getDiscountPrice());
				if (discountContext.getDiscountPrice().compareTo(originalUnitPrice) < 0) {
					plan.markActivityPriced(item.getSkuId());
					plan.putHit(item.getSkuId(), StackActivityTypeEnum.DISCOUNT, discountContext.getActivityId(),
							discountContext.getCouponStackable(), discountContext.getPointsStackable());
				}
			}
		}
		return plan;
	}

	/**
	 * 把营销引擎的新人价命中补入叠加策略计划（在 applyPromotionLadder 之后调用）。
	 *
	 * <p>新人价的成交基价由引擎写入，订单侧预取计划时未知，必须从试算结果的
	 * ladderOverrides（activityType=9）回填：叠加策略取引擎填充值，
	 * 老数据缺省按新人类型默认（不可叠）处理。
	 */
	public void mergeStackPolicyHits(PromotionPricePlan plan, PromotionCalculationVO calculation) {
		if (plan == null || calculation == null || calculation.getLadderOverrides() == null) {
			return;
		}
		for (PromotionCalculationVO.UnitPriceOverride override : calculation.getLadderOverrides()) {
			if (override.getSkuId() == null || !com.aryn.cloud.promotion.api.entity.PromotionActivity.TYPE_NEWBIE_PRICE
					.equals(override.getActivityType())) {
				continue;
			}
			plan.putHit(override.getSkuId(), StackActivityTypeEnum.NEWBIE, override.getActivityId(),
					override.getCouponStackable() == null ? StackActivityTypeEnum.NEWBIE.isDefaultCouponStackable()
							: override.getCouponStackable(),
					override.getPointsStackable() == null ? StackActivityTypeEnum.NEWBIE.isDefaultPointsStackable()
							: override.getPointsStackable());
		}
	}

	private SeckillPriceContextVO querySeckillContext(String skuId) {
		try {
			SeckillPriceContextVO context = remoteSeckillService.getPriceContext(skuId);
			return context != null && context.getSeckillPrice() != null
					&& context.getSeckillPrice().compareTo(BigDecimal.ZERO) > 0 ? context : null;
		}
		catch (Exception e) {
			// Dubbo 调用失败不阻断下单，降级用折扣/原价
			return null;
		}
	}

	private DiscountPriceContextVO queryDiscountContext(String skuId, BigDecimal originalUnitPrice) {
		try {
			DiscountPriceContextVO context = remoteDiscountService.getPriceContext(skuId, originalUnitPrice);
			return context != null && context.getDiscountPrice() != null
					&& context.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0 ? context : null;
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * 本次下单的活动价计划：对外部活动价（拼团/秒杀/限时折扣/新人价）的预取结果与叠加策略。
	 */
	public static class PromotionPricePlan {

		private final GroupBuyOrderContextVO groupBuyContext;

		private final Map<String, BigDecimal> seckillPriceBySku = new HashMap<>();

		private final Map<String, BigDecimal> discountPriceBySku = new HashMap<>();

		private final Set<String> activityPricedSkuIds = new HashSet<>();

		/** 已享活动价 SKU → 命中信息（活动类型 + 叠加策略） */
		private final Map<String, ActivityPriceHit> hits = new HashMap<>();

		/** 叠加策略总开关：false 时所有 SKU 视为可叠（灰度回滚，等同现网行为） */
		private final boolean stackPolicyEnabled;

		private PromotionPricePlan(GroupBuyOrderContextVO groupBuyContext, boolean stackPolicyEnabled) {
			this.groupBuyContext = groupBuyContext;
			this.stackPolicyEnabled = stackPolicyEnabled;
		}

		/** 命中的活动价信息（类型/活动ID/叠加策略），null=非活动价 SKU。 */
		public ActivityPriceHit hit(String skuId) {
			return hits.get(skuId);
		}

		/** 券 eligible 判定：非活动价或活动允许叠券即 true；总开关关闭恒 true。 */
		public boolean couponStackable(String skuId) {
			if (!stackPolicyEnabled) {
				return true;
			}
			ActivityPriceHit hit = hits.get(skuId);
			return hit == null || hit.couponStackable();
		}

		/** 积分 eligible 判定：非活动价或活动允许积分即 true；总开关关闭恒 true。 */
		public boolean pointsStackable(String skuId) {
			if (!stackPolicyEnabled) {
				return true;
			}
			ActivityPriceHit hit = hits.get(skuId);
			return hit == null || hit.pointsStackable();
		}

		boolean isActivityPriced(String skuId) {
			return activityPricedSkuIds.contains(skuId);
		}

		Set<String> activityPricedSkuIds() {
			return Collections.unmodifiableSet(activityPricedSkuIds);
		}

		private BigDecimal groupBuyPrice(String skuId) {
			if (groupBuyContext == null || !Objects.equals(groupBuyContext.getSkuId(), skuId)) {
				return null;
			}
			BigDecimal price = groupBuyContext.getGroupPrice();
			return price != null && price.compareTo(BigDecimal.ZERO) > 0 ? price : null;
		}

		private BigDecimal seckillPrice(String skuId) {
			return seckillPriceBySku.get(skuId);
		}

		private BigDecimal discountPrice(String skuId) {
			return discountPriceBySku.get(skuId);
		}

		private void putSeckillPrice(String skuId, BigDecimal price) {
			seckillPriceBySku.put(skuId, price);
		}

		private void putDiscountPrice(String skuId, BigDecimal price) {
			discountPriceBySku.put(skuId, price);
		}

		private void markActivityPriced(String skuId) {
			activityPricedSkuIds.add(skuId);
		}

		private void putHit(String skuId, StackActivityTypeEnum activityType, String activityId,
				Boolean couponStackable, Boolean pointsStackable) {
			hits.put(skuId, new ActivityPriceHit(activityType, activityId,
					couponStackable == null ? activityType.isDefaultCouponStackable() : couponStackable,
					pointsStackable == null ? activityType.isDefaultPointsStackable() : pointsStackable));
		}

		/** 单 SKU 的活动价命中（活动类型 + 叠加策略）。 */
		public record ActivityPriceHit(StackActivityTypeEnum activityType, String activityId,
				boolean couponStackable, boolean pointsStackable) {
		}

	}

	public void orderFreightHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			List<GoodsSku> goodsSkuList, boolean freeShipping) {
		Map<String, GoodsSku> skuMap = goodsSkuList.stream().collect(Collectors.toMap(GoodsSku::getId, v -> v));

		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			GoodsSku goodsSku = skuMap.get(orderItemEntity.getSkuId());
			if (goodsSku == null) {
				// 游离明细（如已被移除的赠品）无对应 SKU，跳过运费即可，避免空指针中断下单
				continue;
			}
			GoodsSpu goodsSpu = goodsSku.getGoodsSpu();
			BigDecimal freightPrice = BigDecimal.ZERO;

			if (goodsSpu != null && ProductConstants.FREIGHT_TYPE_1.equals(goodsSpu.getFreightType())) {
				freightPrice = goodsSpu.getFixedFreightPrice()
					.multiply(BigDecimal.valueOf(orderItemEntity.getBuyQuantity()));
				orderItemEntity.setFreightPrice(freightPrice);
			}
			if (freeShipping) {
				orderItemEntity.setFreightPrice(BigDecimal.ZERO);
			}
			computeItemPayPrice(orderItemEntity);

		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	/**
	 * 库存校验与扣减。
	 *
	 * <p>共享购物车按成员拆行后，同一 SKU 会出现在多条明细中，
	 * 因此必须<b>按 SKU 汇总数量</b>后再与库存比对；
	 * 历史实现取 findFirst() 的单个数量，且以「SKU 去重数 &lt; 明细行数」判定不足，
	 * 在多人买同一商品时必然误报库存不足。
	 */
	public void orderStockHandler(List<GoodsSku> goodsSkuList, List<OrderItemEntity> orderItemEntityList) {

		Map<String, Integer> requiredBySku = orderItemEntityList.stream()
			.collect(Collectors.toMap(OrderItemEntity::getSkuId, OrderItemEntity::getBuyQuantity, Integer::sum));
		boolean stockEnough = goodsSkuList.stream()
			.allMatch(goodsSku -> goodsSku.getStock() != null
					&& goodsSku.getStock() >= requiredBySku.getOrDefault(goodsSku.getId(), 0));
		if (CollUtil.isEmpty(goodsSkuList) || !stockEnough) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}
		boolean result = remoteGoodsSkuService.reduceStock(orderItemEntityList.stream().map(v -> {
			GoodsSkuStockReqDTO goodsSkuStockReqDTO = new GoodsSkuStockReqDTO();
			goodsSkuStockReqDTO.setSkuId(v.getSkuId());
			goodsSkuStockReqDTO.setStockNum(v.getBuyQuantity());
			goodsSkuStockReqDTO.setSpuId(v.getSpuId());
			return goodsSkuStockReqDTO;
		}).toList());
		if (!result) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}

	}

	public void computeOrderPrice(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		BigDecimal totalPrice = BigDecimal.ZERO;
		BigDecimal paymentPrice = BigDecimal.ZERO;
		BigDecimal freightPrice = BigDecimal.ZERO;
		BigDecimal couponPrice = BigDecimal.ZERO;
		BigDecimal memberDiscountPrice = BigDecimal.ZERO;
		BigDecimal promoPrice = BigDecimal.ZERO;

		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			totalPrice = totalPrice.add(orderItemEntity.getTotalPrice());
			freightPrice = freightPrice.add(orderItemEntity.getFreightPrice());
			couponPrice = couponPrice.add(orderItemEntity.getCouponPrice());
			memberDiscountPrice = memberDiscountPrice.add(orderItemEntity.getMemberDiscountPrice());
			promoPrice = promoPrice
				.add(orderItemEntity.getPromoPrice() != null ? orderItemEntity.getPromoPrice() : BigDecimal.ZERO);
			paymentPrice = paymentPrice.add(orderItemEntity.getPaymentPrice());
		}

		orderInfo.setTotalPrice(totalPrice)
			.setPaymentPrice(paymentPrice)
			.setFreightPrice(freightPrice)
			.setCouponPrice(couponPrice)
			.setPromoPrice(promoPrice)
			.setMemberDiscountPrice(memberDiscountPrice);

	}

	/**
	 * 结算页价格展示：回填「原价 / 活动优惠」（非表字段，仅结算响应下发）。
	 *
	 * <p>促销落价时成交价已覆盖 {@code salesPrice/totalPrice}，这两个字段是退款、统计与
	 * 对账口径，不能为展示而改写；确认页的划线价要显示「被活动价取代的原价」，
	 * 否则出现「划线价 = 实付价」的假折扣（拼团单截图即 25.90 划 25.90）。
	 *
	 * <p>原价取 SKU 当前售价（与拼团详情页「单独购买 ¥xx」同口径）：拼团/秒杀/限时折扣/
	 * 新人价都以 salesPrice 为让利基准，原价小计与成交小计之差即活动让利。成交小计
	 * （{@code totalPrice}）只受活动价影响，券/会员折扣/积分只改 paymentPrice，
	 * 因此该差额不会与券行、积分行重复计数。
	 */
	public void fillOriginalPriceDisplay(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			List<GoodsSku> goodsSkuList) {
		Map<String, GoodsSku> skuMap = goodsSkuList == null ? Map.of() : goodsSkuList.stream()
			.filter(sku -> sku.getId() != null)
			.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (first, second) -> first));
		BigDecimal originalTotalPrice = BigDecimal.ZERO;
		BigDecimal activityDiscountPrice = BigDecimal.ZERO;
		for (OrderItemEntity item : orderItemEntityList) {
			BigDecimal actualTotalPrice = item.getTotalPrice() == null ? BigDecimal.ZERO : item.getTotalPrice();
			BigDecimal quantity = BigDecimal.valueOf(item.getBuyQuantity() == null ? 0 : item.getBuyQuantity());
			GoodsSku sku = skuMap.get(item.getSkuId());
			BigDecimal originalUnitPrice = sku != null && sku.getSalesPrice() != null
					? sku.getSalesPrice() : item.getSalesPrice();
			if (originalUnitPrice == null) {
				originalUnitPrice = actualTotalPrice;
			}
			BigDecimal originalTotal = originalUnitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
			BigDecimal discount = originalTotal.subtract(actualTotalPrice).max(BigDecimal.ZERO)
				.setScale(2, RoundingMode.HALF_UP);
			item.setOriginalPrice(originalUnitPrice);
			item.setOriginalTotalPrice(originalTotal);
			item.setActivityDiscountPrice(discount);
			originalTotalPrice = originalTotalPrice.add(originalTotal);
			activityDiscountPrice = activityDiscountPrice.add(discount);
		}
		orderInfo.setOriginalTotalPrice(originalTotalPrice.setScale(2, RoundingMode.HALF_UP));
		orderInfo.setActivityDiscountPrice(activityDiscountPrice.setScale(2, RoundingMode.HALF_UP));
	}

	/**
	 * 结算页叠加策略展示回填：明细活动类型/叠加标记 + 订单级券门槛可计算金额。
	 *
	 * <p>在会员折扣与券处理之后调用：券门槛基数 = Σ「允许叠券明细」的
	 * {@code totalPrice - memberDiscountPrice}（指定商品券由前端再按券 SPU 交集收敛）。
	 * {@code pointsEligibleAmount} 由积分处理器按券后商品应付写入，此处不重复计算。
	 */
	public void fillStackPolicyDisplay(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionPricePlan plan) {
		BigDecimal couponEligibleAmount = BigDecimal.ZERO;
		for (OrderItemEntity item : orderItemEntityList) {
			PromotionPricePlan.ActivityPriceHit hit = plan == null ? null : plan.hit(item.getSkuId());
			if (hit != null) {
				item.setActivityType(hit.activityType().getCode());
				item.setActivityId(hit.activityId());
				item.setCouponStackable(plan.couponStackable(item.getSkuId()));
				item.setPointsStackable(plan.pointsStackable(item.getSkuId()));
			}
			else {
				item.setActivityType(null);
				item.setActivityId(null);
				item.setCouponStackable(null);
				item.setPointsStackable(null);
			}
			if (plan == null || plan.couponStackable(item.getSkuId())) {
				couponEligibleAmount = couponEligibleAmount.add(discountedGoodsPrice(item));
			}
		}
		orderInfo.setCouponEligibleAmount(couponEligibleAmount);
	}

	public void computeItemPayPrice(OrderItemEntity orderItemEntity) {
		BigDecimal promoPrice = orderItemEntity.getPromoPrice() != null ? orderItemEntity.getPromoPrice()
				: BigDecimal.ZERO;
		BigDecimal itemRealPrice = orderItemEntity.getTotalPrice()
			.subtract(orderItemEntity.getMemberDiscountPrice())
			.subtract(orderItemEntity.getCouponPrice())
			.subtract(promoPrice);

		if (itemRealPrice.compareTo(BigDecimal.ZERO) < 0) {
			itemRealPrice = BigDecimal.ZERO;
		}

		orderItemEntity.setPaymentPrice(itemRealPrice.add(orderItemEntity.getFreightPrice()));

	}

	/**
	 * 积分抵现：在运费计算完成、应付金额校验之前调用。
	 *
	 * <p>抵扣基数为商品应付金额（不含运费），单笔最多抵扣 {@link MallOrderConstants#POINTS_DEDUCT_MAX_RATIO}；
	 * 抵扣金额按明细商品应付比例分摊到 {@code order_item.pointsPrice}，
	 * 使逐行退款时不会把积分抵扣部分重复退给用户。
	 *
	 * <p>仅海员个人购买（{@code purchaseScene=1}）可抵现：船供采购是 B 端成本价，
	 * 叠加积分会侵蚀毛利并污染船供对账口径。
	 *
	 * @param orderInfo 订单
	 * @param orderItemEntityList 订单明细
	 * @param requestedPoints 用户请求使用的积分
	 * @param useMaxPoints 是否使用全部可用积分（确认页开关）
	 * @param context 用户域返回的抵扣上下文（可用积分、每元所需积分）
	 * @param strict 下单时传 true：请求额度与可用额度不一致直接拒绝，避免用户确认金额与实际扣款不一致
	 */
	public void orderPointsDeductHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			Integer requestedPoints, boolean useMaxPoints, PointsDeductContextVO context, boolean strict) {
		orderPointsDeductHandler(orderInfo, orderItemEntityList, requestedPoints, useMaxPoints, context, strict, null);
	}

	/**
	 * 积分抵现（带叠加策略）：在运费计算完成、应付金额校验之前调用。
	 *
	 * <p>抵扣基数与分摊范围只统计「允许积分」的明细（秒杀/新人价等强制不可叠）；
	 * 20% 上限按该基数计算，确认页 {@code pointsDeductMax} 随之收敛，开关行自然隐藏。
	 * eligible 为空时：结算（strict=false）返回 0 额度并记录 {@code pointsStackReason}；
	 * 提交（strict=true）且用户显式请求积分则直接拒绝 60068。
	 *
	 * @param plan   活动价计划（null 时所有明细视为可叠，等同现网行为）
	 * @param strict 下单时传 true：请求额度与可用额度不一致直接拒绝，避免用户确认金额与实际扣款不一致
	 */
	public void orderPointsDeductHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			Integer requestedPoints, boolean useMaxPoints, PointsDeductContextVO context, boolean strict,
			PromotionPricePlan plan) {
		clearPointsDeduct(orderInfo, orderItemEntityList);
		computeOrderPrice(orderInfo, orderItemEntityList);
		orderInfo.setPointsStackReason(null);

		// 非个人购买不参与积分抵现（仅显式声明场景时拦截，历史空场景仍按个人购买处理）：
		// 船供采购（2）与企业采购（3）都是 B 端成本价，叠加积分会侵蚀毛利并污染对账口径
		boolean personalPurchase = PurchaseSceneEnum.PERSONAL.getCode().equals(orderInfo.getPurchaseScene())
				|| !StringUtils.hasText(orderInfo.getPurchaseScene());
		if (!personalPurchase) {
			orderInfo.setPointsDeductRate(0);
			orderInfo.setPointsDeductMax(0);
			orderInfo.setPointsDeductMaxPrice(BigDecimal.ZERO);
			int sceneRequested = useMaxPoints ? 0
					: (requestedPoints == null ? 0 : Math.max(requestedPoints, 0));
			if (strict && sceneRequested > 0) {
				throw new ArynBusinessException(PurchaseSceneEnum.ENTERPRISE.getCode()
						.equals(orderInfo.getPurchaseScene()) ? "企业采购订单不支持积分抵现" : "船供采购订单不支持积分抵现");
			}
			return;
		}

		// 叠加策略：积分只作用于允许积分的明细
		List<OrderItemEntity> pointsEligibleItems = plan == null ? orderItemEntityList
				: orderItemEntityList.stream()
					.filter(item -> plan.pointsStackable(item.getSkuId()))
					.collect(Collectors.toList());
		if (pointsEligibleItems.isEmpty()) {
			orderInfo.setPointsDeductRate(0);
			orderInfo.setPointsDeductMax(0);
			orderInfo.setPointsDeductMaxPrice(BigDecimal.ZERO);
			orderInfo.setPointsEligibleAmount(BigDecimal.ZERO);
			orderInfo.setPointsStackReason("本单商品不支持积分抵现");
			int requested = useMaxPoints ? 0 : (requestedPoints == null ? 0 : Math.max(requestedPoints, 0));
			if (strict && requested > 0) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60068.getCode(),
						MallErrorCodeEnum.ERROR_60068.getMsg());
			}
			return;
		}

		int rate = context != null && context.isEnabled() && context.getPointsPerYuan() != null
				&& context.getPointsPerYuan() > 0 ? context.getPointsPerYuan() : 0;
		int available = context == null || context.getAvailablePoints() == null ? 0
				: Math.max(context.getAvailablePoints(), 0);
		BigDecimal goodsPayable = pointsEligibleItems.stream()
			.map(this::itemGoodsPayable)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal maxDeductAmount = goodsPayable.multiply(MallOrderConstants.POINTS_DEDUCT_MAX_RATIO)
			.setScale(2, RoundingMode.DOWN);
		int maxPointsByCap = maxDeductAmount.multiply(BigDecimal.valueOf(rate))
			.setScale(0, RoundingMode.DOWN)
			.intValue();
		int maxUsable = Math.min(available, maxPointsByCap);
		orderInfo.setPointsDeductRate(rate);
		orderInfo.setPointsDeductMax(maxUsable);
		orderInfo.setPointsDeductMaxPrice(rate > 0
				? BigDecimal.valueOf(maxUsable).divide(BigDecimal.valueOf(rate), 2, RoundingMode.DOWN)
				: BigDecimal.ZERO);
		orderInfo.setPointsEligibleAmount(goodsPayable);

		int requested = useMaxPoints ? maxUsable : (requestedPoints == null ? 0 : Math.max(requestedPoints, 0));
		if (requested <= 0) {
			return;
		}
		if (rate <= 0 || maxUsable <= 0) {
			if (strict) {
				throw new ArynBusinessException("当前订单不可使用积分");
			}
			return;
		}
		int actualPoints = Math.min(requested, maxUsable);
		if (strict && actualPoints != requested) {
			throw new ArynBusinessException("积分可用额度已变化，请返回确认页核对最新价格后重新提交");
		}
		BigDecimal deductAmount = BigDecimal.valueOf(actualPoints)
			.divide(BigDecimal.valueOf(rate), 2, RoundingMode.DOWN);
		// 除不尽时按实际抵扣金额反算积分，再回算金额，保证「积分 × 兑换比例 = 抵扣金额」
		actualPoints = deductAmount.multiply(BigDecimal.valueOf(rate))
			.setScale(0, RoundingMode.DOWN)
			.intValue();
		if (actualPoints <= 0) {
			return;
		}
		deductAmount = BigDecimal.valueOf(actualPoints).divide(BigDecimal.valueOf(rate), 2, RoundingMode.DOWN);
		orderInfo.setPointsUsed(actualPoints).setPointsDeductPrice(deductAmount);
		allocatePointsDeduct(pointsEligibleItems, deductAmount);
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	private void clearPointsDeduct(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		orderInfo.setPointsUsed(0).setPointsDeductPrice(BigDecimal.ZERO);
		for (OrderItemEntity item : orderItemEntityList) {
			BigDecimal previous = item.getPointsPrice();
			if (previous != null && previous.compareTo(BigDecimal.ZERO) > 0 && item.getPaymentPrice() != null) {
				item.setPaymentPrice(item.getPaymentPrice().add(previous));
			}
			item.setPointsPrice(BigDecimal.ZERO);
		}
	}

	/**
	 * 把抵扣金额按各明细商品应付占比分摊到 {@code order_item.pointsPrice}。
	 *
	 * <p>先按比例向下取整到分，再把尾差逐分补到小数部分最大的明细上（不超过该明细商品应付），
	 * 保证「Σ明细分摊 == 订单抵扣金额」。旧实现按末位明细兜底并在越界时 clamp，
	 * 遇到极低价明细会静默丢失抵扣金额，导致「扣了积分但应付没减够」。
	 */
	private void allocatePointsDeduct(List<OrderItemEntity> orderItemEntityList, BigDecimal deductAmount) {
		List<OrderItemEntity> eligible = orderItemEntityList.stream()
			.filter(item -> itemGoodsPayable(item).compareTo(BigDecimal.ZERO) > 0)
			.toList();
		BigDecimal total = eligible.stream()
			.map(this::itemGoodsPayable)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(BigDecimal.ZERO) <= 0 || deductAmount.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		int size = eligible.size();
		BigDecimal[] shares = new BigDecimal[size];
		BigDecimal[] remainders = new BigDecimal[size];
		BigDecimal allocated = BigDecimal.ZERO;
		for (int index = 0; index < size; index++) {
			BigDecimal exact = deductAmount.multiply(itemGoodsPayable(eligible.get(index)))
				.divide(total, 4, RoundingMode.DOWN);
			shares[index] = exact.setScale(2, RoundingMode.DOWN);
			remainders[index] = exact.subtract(shares[index]);
			allocated = allocated.add(shares[index]);
		}
		Integer[] indexes = new Integer[size];
		for (int index = 0; index < size; index++) {
			indexes[index] = index;
		}
		Arrays.sort(indexes, (left, right) -> {
			int compared = remainders[right].compareTo(remainders[left]);
			return compared != 0 ? compared : Integer.compare(right, left);
		});
		BigDecimal cent = new BigDecimal("0.01");
		BigDecimal leftover = deductAmount.subtract(allocated);
		while (leftover.compareTo(BigDecimal.ZERO) > 0) {
			boolean progressed = false;
			for (Integer index : indexes) {
				if (leftover.compareTo(BigDecimal.ZERO) <= 0) {
					break;
				}
				if (shares[index].add(cent).compareTo(itemGoodsPayable(eligible.get(index))) > 0) {
					continue;
				}
				shares[index] = shares[index].add(cent);
				leftover = leftover.subtract(cent);
				progressed = true;
			}
			if (!progressed) {
				// 单笔上限 20% ≤ 商品应付总额，理论上不可达；防御性抛错避免账实不一致
				throw new ArynBusinessException("积分抵扣分摊失败，请重试");
			}
		}
		for (int index = 0; index < size; index++) {
			OrderItemEntity item = eligible.get(index);
			BigDecimal paymentPrice = item.getPaymentPrice() == null ? BigDecimal.ZERO : item.getPaymentPrice();
			item.setPointsPrice(shares[index]);
			item.setPaymentPrice(paymentPrice.subtract(shares[index]).max(BigDecimal.ZERO));
		}
	}

	private BigDecimal itemGoodsPayable(OrderItemEntity item) {
		BigDecimal paymentPrice = item.getPaymentPrice() == null ? BigDecimal.ZERO : item.getPaymentPrice();
		BigDecimal freightPrice = item.getFreightPrice() == null ? BigDecimal.ZERO : item.getFreightPrice();
		return paymentPrice.subtract(freightPrice).max(BigDecimal.ZERO);
	}

	public void orderMemberBenefitHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			MemberBenefitsVO benefits) {
		orderMemberBenefitHandler(orderInfo, orderItemEntityList, benefits, null);
	}

	/**
	 * 会员权益落价：会员折扣不与活动价叠加，活动价明细只享受活动价。
	 * 积分倍率与免邮不受此限制（免邮在运费处理器中生效）。
	 */
	public void orderMemberBenefitHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			MemberBenefitsVO benefits, PromotionPricePlan plan) {
		BigDecimal discountRate = benefits == null ? BigDecimal.ONE : benefits.getDiscountRate();
		BigDecimal pointsMultiplier = benefits == null || benefits.getPointsMultiplier() == null
				? BigDecimal.ONE : benefits.getPointsMultiplier();
		if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0
				|| discountRate.compareTo(BigDecimal.ONE) > 0) {
			throw new ArynBusinessException("会员折扣配置不合法");
		}
		if (pointsMultiplier.compareTo(BigDecimal.ONE) < 0) {
			throw new ArynBusinessException("会员积分倍率配置不合法");
		}
		orderInfo.setPointsMultiplier(pointsMultiplier);
		for (OrderItemEntity item : orderItemEntityList) {
			boolean activityPriced = plan != null && plan.hit(item.getSkuId()) != null;
			BigDecimal discountPrice = activityPriced ? BigDecimal.ZERO
					: item.getTotalPrice().multiply(BigDecimal.ONE.subtract(discountRate))
						.setScale(2, RoundingMode.HALF_UP);
			item.setMemberDiscountPrice(discountPrice);
			computeItemPayPrice(item);
		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	public void orderCouponHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		orderCouponHandler(orderInfo, orderItemEntityList, null, false);
	}

	/**
	 * 优惠券处理（带叠加策略与严格模式）。
	 *
	 * <p>活动叠加规则：门槛基数与分摊范围只统计「允许叠券」的明细（券范围 ∩ 可叠 SKU）；
	 * 不可叠明细的券额恒为 0。eligible 为空时：结算（strict=false）不抛错、
	 * 记录 {@code couponStackReason} 供确认页置灰提示；提交（strict=true）直接拒绝 60067，
	 * 防止绕过确认页强行用券。
	 *
	 * <p>最低实付兜底：券额上限 = 可叠基数 − 最低实付金额，保证券后商品应付不低于
	 * 最低支付金额（叠加链路不出 0 元单）；关闭开关时回退为「券额 ≤ 可叠基数」。
	 *
	 * @param plan   活动价计划（null 时所有明细视为可叠，等同现网行为）
	 * @param strict 提交订单时传 true：不可叠仍用券直接拒绝
	 */
	public void orderCouponHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionPricePlan plan, boolean strict) {
		orderInfo.setCouponStackReason(null);
		if (!StringUtils.hasText(orderInfo.getCouponUserId())) {
			return;
		}
		BigDecimal couponTotalAmount = BigDecimal.ZERO;
		BigDecimal totalPrice = BigDecimal.ZERO;
		List<OrderItemEntity> listCouponGoods = null;
		String couponUseRange = MallEventConstants.USE_RANGE_1;
		CouponUserRespVO couponUserRespVO = remoteCouponUserService.getById(orderInfo.getCouponUserId(), orderInfo.getUserId());
		if (Objects.isNull(couponUserRespVO)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}
		CouponInfo couponInfo = couponUserRespVO.getCouponInfo();
		couponUseRange = couponInfo.getUseRange();
		List<OrderItemEntity> scopeItems;
		if (MallEventConstants.USE_RANGE_2.equals(couponInfo.getUseRange())) {
			List<CouponGoods> couponGoodsList = couponUserRespVO.getCouponGoodsList();
			if (CollUtil.isEmpty(couponGoodsList)) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
						MallErrorCodeEnum.ERROR_60060.getMsg());
			}
			listCouponGoods = orderItemEntityList.stream()
				.map(map -> couponGoodsList.stream()
					.filter(m -> Objects.equals(m.getSpuId(), map.getSpuId()))
					.findFirst()
					.map(m -> {
						return map;
					})
					.orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
			scopeItems = listCouponGoods;
		}
		else {
			scopeItems = orderItemEntityList;
		}
		// 叠加策略：券只作用于「券范围 ∩ 允许叠券」的明细（秒杀/新人价等强制不可叠）
		List<OrderItemEntity> couponEligibleItems = scopeItems.stream()
			.filter(item -> plan == null || plan.couponStackable(item.getSkuId()))
			.collect(Collectors.toList());
		if (couponEligibleItems.isEmpty()) {
			orderInfo.setCouponStackReason("本单商品不支持使用优惠券");
			clearCouponAllocations(orderInfo, orderItemEntityList);
			if (strict) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60067.getCode(),
						MallErrorCodeEnum.ERROR_60067.getMsg());
			}
			return;
		}
		totalPrice = this.verifyCoupon(couponEligibleItems, couponUserRespVO);
		couponTotalAmount = this.couponCompute(totalPrice, couponUserRespVO, couponInfo).min(couponCap(totalPrice));
		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			BigDecimal couponPrice = BigDecimal.ZERO;
			if (couponTotalAmount.compareTo(BigDecimal.ZERO) > 0) {
				boolean isComputeCoupon = plan == null || plan.couponStackable(orderItemEntity.getSkuId());
				if (MallEventConstants.USE_RANGE_2.equals(couponUseRange)) {
					isComputeCoupon = isComputeCoupon && listCouponGoods.stream()
						.anyMatch(a -> a.getSkuId().equals(orderItemEntity.getSkuId()));
				}
				if (isComputeCoupon) {
					BigDecimal discountedItemPrice = discountedGoodsPrice(orderItemEntity);
					BigDecimal oneMoneyScope = discountedItemPrice
						.divide(totalPrice, 2, RoundingMode.HALF_EVEN);
					couponPrice = oneMoneyScope.multiply(couponTotalAmount).setScale(2, RoundingMode.HALF_EVEN);
					if (couponPrice.compareTo(discountedItemPrice) > 0) {
						couponPrice = discountedItemPrice;
					}
					couponTotalAmount = couponTotalAmount.subtract(couponPrice);
					totalPrice = totalPrice.subtract(discountedItemPrice);
				}
			}
			orderItemEntity.setCouponPrice(couponPrice);
			computeItemPayPrice(orderItemEntity);
		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	/**
	 * 券额上限：可叠基数扣除最低实付金额；基数本身低于最低实付时不允许券抵扣（避免击穿成 0 元单）。
	 */
	private BigDecimal couponCap(BigDecimal eligibleBase) {
		if (!minPayableEnabled || minPayableAmount == null || minPayableAmount.compareTo(BigDecimal.ZERO) <= 0) {
			return eligibleBase;
		}
		if (eligibleBase.compareTo(minPayableAmount) < 0) {
			return BigDecimal.ZERO;
		}
		return eligibleBase.subtract(minPayableAmount);
	}

	private void clearCouponAllocations(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		for (OrderItemEntity item : orderItemEntityList) {
			item.setCouponPrice(BigDecimal.ZERO);
			computeItemPayPrice(item);
		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	public BigDecimal verifyCoupon(List<OrderItemEntity> listCouponGoods, CouponUserRespVO couponUserRespVO) {
		List<OrderItemEntity> orderItemEntities = listCouponGoods;
		if (!couponUserRespVO.getStatus().equals(CouponUserStatusEnum.STATUS_0.getCode())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60061.getCode(),
					MallErrorCodeEnum.ERROR_60061.getMsg());
		}
		if (couponUserRespVO.getValidatTime().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}

		return orderItemEntities.stream().map(this::discountedGoodsPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal discountedGoodsPrice(OrderItemEntity item) {
		return item.getTotalPrice().subtract(item.getMemberDiscountPrice());
	}

	public BigDecimal couponCompute(BigDecimal totalPrice, CouponUserRespVO couponUser, CouponInfo couponInfo) {

		if (!couponUser.getStatus().equals(CouponUserStatusEnum.STATUS_0.getCode())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60061.getCode(),
					MallErrorCodeEnum.ERROR_60061.getMsg());
		}
		if (couponUser.getValidatTime().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}

		if (couponInfo.getThreshold().compareTo(BigDecimal.ZERO) > 0
				&& totalPrice.compareTo(couponInfo.getThreshold()) < 0) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}
		couponUser.setStatus(CouponUserStatusEnum.STATUS_3.getCode());

		if (MallEventConstants.COUPON_TYPE_1.equals(couponInfo.getCouponType())) {
			return couponInfo.getAmount();
		}
		else if (MallEventConstants.COUPON_TYPE_2.equals(couponInfo.getCouponType())) {
			BigDecimal discount = couponInfo.getDiscount().divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_EVEN);
			return totalPrice.subtract(totalPrice.multiply(discount));
		}
		else {
			return BigDecimal.ZERO;
		}
	}

	/**
	 * 生成订单明细。
	 *
	 * <p>以「请求行」为粒度生成，一个 {@link CreateOrderSkuReqDTO} 对应一条明细。
	 * 共享购物车按成员拆分提交时，同一 SKU 会有多行（每人一行），用于配送贴标签区分归属；
	 * 因此不能按 goodsSkuList 去重后遍历，否则同 SKU 的其余成员明细会被静默丢弃。
	 */
	public List<OrderItemEntity> generateOrderItems(List<GoodsSku> goodsSkuList,
			List<CreateOrderSkuReqDTO> skuReqList) {
		Map<String, GoodsSku> skuMap = goodsSkuList.stream()
			.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (first, second) -> first));
		return skuReqList.stream().map(placeOrderSku -> {
			GoodsSku sku = skuMap.get(placeOrderSku.getSkuId());
			if (sku == null) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
						MallErrorCodeEnum.ERROR_60008.getMsg());
			}
			BigDecimal salesPrice = sku.getSalesPrice();
			GoodsSpu goodsSpu = sku.getGoodsSpu();
			OrderItemEntity orderItemEntity = new OrderItemEntity();
			orderItemEntity.setBuyQuantity(placeOrderSku.getQuantity());
			orderItemEntity.setSpuName(goodsSpu.getName());
			orderItemEntity.setSpuId(goodsSpu.getId());
			orderItemEntity.setSkuId(sku.getId());
			orderItemEntity.setSalesPrice(salesPrice);
			orderItemEntity.setTotalPrice(
					orderItemEntity.getSalesPrice().multiply(BigDecimal.valueOf(placeOrderSku.getQuantity())));
			orderItemEntity.setFreightPrice(BigDecimal.ZERO);
			orderItemEntity.setCouponPrice(BigDecimal.ZERO);
			orderItemEntity.setMemberDiscountPrice(BigDecimal.ZERO);
			orderItemEntity.setPaymentPrice(orderItemEntity.getTotalPrice());
			orderItemEntity.setSpecsInfo(placeOrderSku.getSpecsInfo());
			orderItemEntity.setPicUrl(resolveItemPicUrl(placeOrderSku.getPicUrl(), sku));
			orderItemEntity.setContributorUserId(placeOrderSku.getContributorUserId());
			orderItemEntity.setContributorName(placeOrderSku.getContributorName());
			orderItemEntity.setMemberRemark(placeOrderSku.getMemberRemark());
			return orderItemEntity;
		}).collect(Collectors.toList());

	}

	/**
	 * 从营销试算结果中提取命中新人价的 SKU 集合（供促销价处理跳过折扣分支与限购校验）。
	 */
	public Set<String> newbiePriceSkuIds(PromotionCalculationVO calculation) {
		if (calculation == null || calculation.getLadderOverrides() == null) {
			return Set.of();
		}
		return calculation.getLadderOverrides().stream()
			.filter(override -> override.getActivityType() != null
					&& override.getActivityType().equals(com.aryn.cloud.promotion.api.entity.PromotionActivity.TYPE_NEWBIE_PRICE))
			.map(PromotionCalculationVO.UnitPriceOverride::getSkuId)
			.collect(Collectors.toSet());
	}

	/**
	 * 新人价限购校验：命中新人价的 SKU 在整单内合计数量必须为 1。
	 *
	 * <p>按 SKU 聚合数量（共享购物车按成员拆行后同一 SKU 会有多条明细），
	 * 超量直接拦截整单；仅在提交订单时调用，结算页不拦（用户改数量时才提示）。
	 */
	public void assertNewbieQuantityLimit(PromotionCalculationVO calculation, List<OrderItemEntity> orderItemEntityList) {
		Set<String> newbieSkuIds = newbiePriceSkuIds(calculation);
		if (newbieSkuIds.isEmpty()) {
			return;
		}
		Map<String, Integer> quantityBySku = orderItemEntityList.stream()
			.filter(item -> newbieSkuIds.contains(item.getSkuId()))
			.collect(Collectors.toMap(OrderItemEntity::getSkuId, OrderItemEntity::getBuyQuantity, Integer::sum));
		boolean overLimit = quantityBySku.values().stream().anyMatch(quantity -> quantity != null && quantity > 1);
		if (overLimit) {
			throw new ArynBusinessException("新人专享商品每人限购 1 件");
		}
	}

	/**
	 * 买赠赠品按真实库存过滤：库存不足的赠品不赠（结算页同步不展示），
	 * 并回填商品名/图片供确认页展示。库存查询失败按无赠品处理（fail-closed），
	 * 不再以伪造库存放行赠品。
	 */
	public List<PromotionCalculationVO.GiftItem> availableGifts(PromotionCalculationVO calculation) {
		if (calculation == null || calculation.getGifts() == null || calculation.getGifts().isEmpty()) {
			return List.of();
		}
		List<String> skuIds = calculation.getGifts().stream()
			.map(PromotionCalculationVO.GiftItem::getSkuId)
			.distinct()
			.toList();
		Map<String, GoodsSku> skuMap;
		try {
			skuMap = remoteGoodsSkuService.getBySkuIds(skuIds).stream()
				.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		}
		catch (Exception ex) {
			log.warn("赠品库存查询失败，本次按无赠品处理", ex);
			return List.of();
		}
		List<PromotionCalculationVO.GiftItem> available = new ArrayList<>();
		for (PromotionCalculationVO.GiftItem gift : calculation.getGifts()) {
			GoodsSku sku = skuMap.get(gift.getSkuId());
			if (sku == null || sku.getStock() == null || sku.getStock() < gift.getQuantity()) {
				log.warn("赠品SKU[{}]不存在或库存不足，本次不赠", gift.getSkuId());
				continue;
			}
			gift.setSpuName(sku.getGoodsSpu() != null ? sku.getGoodsSpu().getName() : null);
			gift.setPicUrl(sku.getPicUrl());
			available.add(gift);
		}
		return available;
	}

	/**
	 * 订单明细图片三级兜底：请求快照 → SKU 图 → SPU 主图首张。
	 *
	 * <p>请求 DTO 的 picUrl 由购物车链路带入；共享购物车按成员拆分下单等链路不带该字段，
	 * 不能因缺省就把已取到的商品图覆盖成 null，否则订单列表/详情整页无图。
	 * spu_urls 存量数据存在字面量 "[]"（JsonArrayStringTypeHandler 会解析成单元素 ["[]"]），
	 * 作图地址时须跳过；同时不盲目取下标，避免空数组导致下单失败。
	 */
	private String resolveItemPicUrl(String reqPicUrl, GoodsSku sku) {
		if (StringUtils.hasText(reqPicUrl)) {
			return reqPicUrl;
		}
		if (StringUtils.hasText(sku.getPicUrl())) {
			return sku.getPicUrl();
		}
		GoodsSpu goodsSpu = sku.getGoodsSpu();
		if (goodsSpu == null || goodsSpu.getSpuUrls() == null) {
			return null;
		}
		for (String url : goodsSpu.getSpuUrls()) {
			if (StringUtils.hasText(url) && !"[]".equals(url.trim())) {
				return url;
			}
		}
		return null;
	}


	/**
	 * 营销试算与阶梯价改价：在会员折扣/优惠券之前调用（改 SKU 成交基价）。
	 * 返回计算结果供券后整单优惠分摊；营销服务不可用时按无优惠处理，不阻断交易。
	 */
	public PromotionCalculationVO applyPromotionLadder(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionPricePlan plan) {
		try {
			PromotionContextDTO context = buildPromotionContext(orderInfo, orderItemEntityList, plan);
			if (context == null) {
				return null;
			}
			PromotionCalculationVO calculation = remotePromotionEngine.preview(context);
			if (calculation == null || calculation.getLadderOverrides() == null) {
				return calculation;
			}
			for (OrderItemEntity item : orderItemEntityList) {
				calculation.getLadderOverrides().stream()
					.filter(override -> override.getSkuId().equals(item.getSkuId()))
					.findFirst()
					.ifPresent(override -> {
						item.setSalesPrice(override.getUnitPrice());
						item.setTotalPrice(override.getUnitPrice().multiply(BigDecimal.valueOf(item.getBuyQuantity())));
						item.setPaymentPrice(item.getTotalPrice());
					});
			}
			return calculation;
		}
		catch (Exception ex) {
			log.warn("订单[{}]营销试算失败，按无优惠处理", orderInfo.getId(), ex);
			return null;
		}
	}

	/**
	 * 整船/整单优惠分摊：在优惠券之后调用，只分摊给未享活动价的明细。
	 *
	 * <p>已享活动价（拼团/秒杀/新人价/阶梯价/限时折扣）的 SKU 不参与门槛也不参与分摊，
	 * 否则活动价商品会被整船优惠二次让利；判定集合与营销试算保持同一口径。
	 */
	public void applyShipWholeDiscount(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionCalculationVO calculation) {
		applyShipWholeDiscount(orderInfo, orderItemEntityList, calculation, null);
	}

	public void applyShipWholeDiscount(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionCalculationVO calculation, PromotionPricePlan plan) {
		if (calculation == null || calculation.getWholeDiscount() == null
				|| calculation.getWholeDiscount().compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		Set<String> activityPricedSkuIds = new HashSet<>();
		if (plan != null) {
			activityPricedSkuIds.addAll(plan.activityPricedSkuIds());
		}
		if (calculation.getLadderOverrides() != null) {
			calculation.getLadderOverrides().forEach(override -> activityPricedSkuIds.add(override.getSkuId()));
		}
		List<OrderItemEntity> eligibleItems = orderItemEntityList.stream()
			.filter(item -> !activityPricedSkuIds.contains(item.getSkuId()))
			.toList();
		BigDecimal discountableBase = eligibleItems.stream()
			.map(this::discountedGoodsPrice)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (discountableBase.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		BigDecimal wholeDiscount = calculation.getWholeDiscount().setScale(2, RoundingMode.DOWN);
		if (wholeDiscount.compareTo(discountableBase) > 0) {
			wholeDiscount = discountableBase;
		}
		allocateWholeDiscount(eligibleItems, discountableBase, wholeDiscount);
		for (OrderItemEntity item : orderItemEntityList) {
			if (activityPricedSkuIds.contains(item.getSkuId())) {
				// 活动价明细不承接整船优惠，显式清零避免历史分摊残留
				item.setPromoPrice(BigDecimal.ZERO);
				computeItemPayPrice(item);
			}
		}
		orderInfo.setPromotionDetails(calculation.getDetails());
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	/**
	 * 按可折金额占比分摊：分币向下取整后把尾差补到余数最大的明细，
	 * 保证「Σ明细整船优惠 == 活动优惠金额」且单行不超过该行可折金额。
	 */
	private void allocateWholeDiscount(List<OrderItemEntity> eligibleItems, BigDecimal discountableBase,
			BigDecimal wholeDiscount) {
		int size = eligibleItems.size();
		BigDecimal[] shares = new BigDecimal[size];
		BigDecimal[] remainders = new BigDecimal[size];
		BigDecimal allocated = BigDecimal.ZERO;
		for (int i = 0; i < size; i++) {
			BigDecimal exact = discountedGoodsPrice(eligibleItems.get(i))
				.divide(discountableBase, 6, RoundingMode.DOWN)
				.multiply(wholeDiscount);
			shares[i] = exact.setScale(2, RoundingMode.DOWN);
			remainders[i] = exact.subtract(shares[i]);
			allocated = allocated.add(shares[i]);
		}
		Integer[] indexes = new Integer[size];
		for (int i = 0; i < size; i++) {
			indexes[i] = i;
		}
		Arrays.sort(indexes, (left, right) -> {
			int compared = remainders[right].compareTo(remainders[left]);
			return compared != 0 ? compared : Integer.compare(right, left);
		});
		BigDecimal cent = new BigDecimal("0.01");
		BigDecimal leftover = wholeDiscount.subtract(allocated);
		while (leftover.compareTo(BigDecimal.ZERO) > 0) {
			boolean progressed = false;
			for (Integer index : indexes) {
				if (leftover.compareTo(BigDecimal.ZERO) <= 0) {
					break;
				}
				if (shares[index].add(cent).compareTo(discountedGoodsPrice(eligibleItems.get(index))) > 0) {
					continue;
				}
				shares[index] = shares[index].add(cent);
				leftover = leftover.subtract(cent);
				progressed = true;
			}
			if (!progressed) {
				// 优惠已按可折金额封顶，理论上不可达；防御性退出避免死循环
				break;
			}
		}
		for (int i = 0; i < size; i++) {
			OrderItemEntity item = eligibleItems.get(i);
			item.setPromoPrice(shares[i]);
			computeItemPayPrice(item);
		}
	}

	private PromotionContextDTO buildPromotionContext(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionPricePlan plan) {
		return buildPromotionContext(orderInfo, orderItemEntityList, Map.of(), plan);
	}

	private PromotionContextDTO buildPromotionContext(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			Map<String, BigDecimal> originalPriceBySku, PromotionPricePlan plan) {
		String tenantId = StringUtils.hasText(orderInfo.getTenantId()) ? orderInfo.getTenantId()
				: ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId) || CollUtil.isEmpty(orderItemEntityList)) {
			return null;
		}
		PromotionContextDTO context = new PromotionContextDTO();
		context.setTenantId(tenantId);
		context.setUserId(orderInfo.getUserId());
		context.setOrderId(orderInfo.getId());
		context.setOrderNo(orderInfo.getOrderNo());
		context.setPurchaseScene(orderInfo.getPurchaseScene());
		context.setVesselId(orderInfo.getVesselId());
		context.setVesselCallId(orderInfo.getVesselCallId());
		context.setPortCode(orderInfo.getPortCode());
		// 按 SKU 聚合后再交给营销引擎：共享购物车按成员拆行后，
		// 同一 SKU 会有多条明细（每人一行），若逐行传入则阶梯价的「满 N 件」档位
		// 会按单行数量判定而永不触发，导致整船优惠失效。聚合后语义为整单该 SKU 总量。
		Map<String, PromotionContextDTO.SkuItem> mergedBySku = new LinkedHashMap<>();
		for (OrderItemEntity item : orderItemEntityList) {
			PromotionContextDTO.SkuItem skuItem = mergedBySku.get(item.getSkuId());
			if (skuItem == null) {
				skuItem = new PromotionContextDTO.SkuItem();
				skuItem.setSkuId(item.getSkuId());
				skuItem.setQuantity(item.getBuyQuantity());
				// 已改价的明细（阶梯价/新人价）必须按原价重算：否则第二次 preview 看到
				// 活动价等于基价，命中判定失败，锁不落库，预算也就永远不扣
				BigDecimal originalPrice = originalPriceBySku.get(item.getSkuId());
				skuItem.setSalesPrice(originalPrice != null ? originalPrice : item.getSalesPrice());
				// 外部活动价（拼团/秒杀/限时折扣）由订单侧预取；阶梯价/新人价由引擎自行覆盖
				skuItem.setActivityPriced(plan != null && plan.isActivityPriced(item.getSkuId()));
				mergedBySku.put(item.getSkuId(), skuItem);
			}
			else {
				skuItem.setQuantity(skuItem.getQuantity() + item.getBuyQuantity());
			}
		}
		context.setSkuItems(List.copyOf(mergedBySku.values()));
		return context;
	}


	/**
	 * 营销锁定：下单时预留（订单+活动幂等）。营销服务不可用时按无优惠继续下单。
	 *
	 * <p>锁定时用 SKU 原价重建上下文：下单前的 preview 已把阶梯价/新人价写进明细
	 * salesPrice，若按改后价再次试算，活动价与基价相等会判为未命中，锁与预算都不会落库。
	 *
	 * <p>业务异常（额度竞态、新人价在途唯一）必须向上抛出；若锁定结果与下单试算
	 * 的让利额不一致，说明优惠在确认后发生变化，同样拒绝成单，避免静默涨价。
	 */
	public PromotionCalculationVO reservePromotion(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			List<GoodsSku> goodsSkuList, PromotionCalculationVO expected) {
		return reservePromotion(orderInfo, orderItemEntityList, goodsSkuList, expected, null);
	}

	public PromotionCalculationVO reservePromotion(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			List<GoodsSku> goodsSkuList, PromotionCalculationVO expected, PromotionPricePlan plan) {
		PromotionCalculationVO reserved = null;
		try {
			Map<String, BigDecimal> originalPriceBySku = new HashMap<>();
			if (goodsSkuList != null) {
				for (GoodsSku sku : goodsSkuList) {
					if (sku.getId() != null && sku.getSalesPrice() != null) {
						originalPriceBySku.put(sku.getId(), sku.getSalesPrice());
					}
				}
			}
			PromotionContextDTO context = buildPromotionContext(orderInfo, orderItemEntityList, originalPriceBySku,
					plan);
			if (context != null) {
				reserved = remotePromotionEngine.reserve(context);
			}
			// 折扣商品级限量：与营销锁定同事务窗口占用；失败直接拦单，不放行超卖
			reserveDiscountStock(orderInfo, orderItemEntityList);
		}
		catch (ArynBusinessException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.warn("订单[{}]营销锁定失败，按无优惠继续", orderInfo.getId(), ex);
		}
		assertPromotionUnchanged(expected, reserved);
		return reserved;
	}

	private void assertPromotionUnchanged(PromotionCalculationVO expected, PromotionCalculationVO reserved) {
		// 一侧失败而另一侧已产生让利：价格与锁定不一致，继续成单会出现
		// 「按优惠价成交但没有锁」或「按原价成交却占用了预算」，两者都不可接受
		if (reserved == null) {
			if (hasDiscount(expected)) {
				throw new ArynBusinessException("优惠服务暂不可用，请稍后重新提交订单");
			}
			return;
		}
		if (expected == null) {
			if (hasDiscount(reserved)) {
				throw new ArynBusinessException("优惠服务暂不可用，请稍后重新提交订单");
			}
			return;
		}
		BigDecimal expectedDiscount = expected.getTotalDiscount() != null ? expected.getTotalDiscount()
				: BigDecimal.ZERO;
		BigDecimal reservedDiscount = reserved.getTotalDiscount() != null ? reserved.getTotalDiscount()
				: BigDecimal.ZERO;
		if (expectedDiscount.compareTo(reservedDiscount) != 0) {
			throw new ArynBusinessException("优惠已变更，请返回订单确认页核对最新价格后重新提交");
		}
	}

	private boolean hasDiscount(PromotionCalculationVO calculation) {
		return calculation != null && calculation.getTotalDiscount() != null
				&& calculation.getTotalDiscount().compareTo(BigDecimal.ZERO) > 0;
	}

	/**
	 * 营销释放：取消/超时时调用（幂等）。
	 */
	public void releasePromotion(String tenantId, String orderId, String reason) {
		remotePromotionEngine.release(tenantId, orderId, reason);
		try {
			remoteDiscountService.releaseStock(tenantId, orderId, null, reason);
		} catch (Exception ex) {
			// 释放失败不能阻塞取消/退款：库存由补偿任务与明细幂等兜底，这里只告警
			log.warn("订单[{}]折扣库存释放失败, reason={}", orderId, reason, ex);
		}
	}

	/**
	 * 折扣库存占用：只把有折扣价的 SKU 送过去（无折扣的 SKU 营销侧直接跳过）。
	 * 远程调用失败按「营销服务不可用」处理（告警放行），返回 success=false 则拦单。
	 */
	private void reserveDiscountStock(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		if (orderItemEntityList == null || orderItemEntityList.isEmpty()) {
			return;
		}
		List<DiscountStockItemDTO> items = orderItemEntityList.stream()
			.filter(item -> item.getSkuId() != null && item.getBuyQuantity() != null && item.getBuyQuantity() > 0)
			.map(item -> new DiscountStockItemDTO(item.getSkuId(), item.getBuyQuantity(), item.getId()))
			.collect(Collectors.toList());
		if (items.isEmpty()) {
			return;
		}
		// 新建订单实体的 tenantId 由租户拦截器在 insert 时补写，内存对象上可能为空：
		// 为空时回落到当前线程的租户上下文，二者都取不到才放弃（记录告警，不静默放行超卖）
		String tenantId = orderInfo.getTenantId();
		if (tenantId == null || tenantId.isBlank()) {
			tenantId = ArynTenantContextHolder.getTenantId();
		}
		if (tenantId == null || tenantId.isBlank()) {
			log.warn("订单[{}]缺少租户上下文，跳过折扣库存占用", orderInfo.getId());
			return;
		}
		try {
			DiscountStockReserveResultDTO result = remoteDiscountService.reserveStock(
					tenantId, orderInfo.getId(), items);
			if (result != null && !result.isSuccess()) {
				throw new ArynBusinessException(result.getMessage() == null
						? "折扣商品库存不足，请调整数量后重试" : result.getMessage());
			}
		} catch (ArynBusinessException business) {
			throw business;
		} catch (Exception ex) {
			log.warn("订单[{}]折扣库存占用失败，按无折扣库存限制继续", orderInfo.getId(), ex);
		}
	}

}
