package com.aryn.cloud.order.service.impl;

import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.remote.RemoteOrderContextService;
import com.aryn.cloud.order.api.remote.RemoteOrderScenarioService;
import com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO;
import com.aryn.cloud.order.api.vo.OrderScenarioFactsVO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.user.api.constant.EnterpriseMemberConstants;
import com.aryn.cloud.user.api.remote.RemoteEnterpriseUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 捕获一次订单事实，原方案/草稿共用正式计算方法；不调用下单、预占或发券服务。 */
@Service
@DubboService
@RequiredArgsConstructor
public class OrderScenarioPricingService implements RemoteOrderScenarioService {
	private final OrderPriceComputeService pricing;

	@DubboReference private RemoteGoodsSkuService remoteGoodsSkuService;
	@DubboReference private RemoteMallUserService remoteMallUserService;
	@DubboReference private RemoteEnterpriseUserService remoteEnterpriseUserService;
	@DubboReference private RemoteVesselService remoteVesselService;
	@DubboReference private RemoteOrderContextService remoteOrderContextService;
	@DubboReference private RemoteCouponUserService remoteCouponUserService;
	@DubboReference private RemoteMallZoneSettingService remoteMallZoneSettingService;

	@Override
	public OrderScenarioFactsVO capture(PromotionContextDTO requested, String couponUserId) {
		requireTenant(requested);
		if (!StringUtils.hasText(requested.getUserId()) || !List.of("1", "2", "3").contains(requested.getPurchaseScene())) {
			throw new ArynBusinessException("请提供样本用户和合法采购场景");
		}
		var user = remoteMallUserService.getUserById(requested.getUserId());
		if (user == null || !requested.getTenantId().equals(user.getTenantId())
				|| !requested.getUserId().equals(user.getId()) || !"0".equals(user.getStatus())) {
			throw new ArynBusinessException("样本用户不存在或不可用");
		}
		PromotionContextDTO context = new PromotionContextDTO();
		context.setTenantId(requested.getTenantId());
		context.setUserId(requested.getUserId());
		context.setPurchaseScene(requested.getPurchaseScene());
		context.setActivityTime(requested.getActivityTime() == null ? LocalDateTime.now() : requested.getActivityTime());
		// 先校验真实成员，再向营销域交付主体事实，不能用任意船/企业窥探合同价。
		if (StringUtils.hasText(requested.getVesselId())) {
			if (!remoteVesselService.isVesselMember(context.getTenantId(), requested.getVesselId(), context.getUserId())) {
				throw new ArynBusinessException("样本用户不属于指定船舶");
			}
			context.setVesselId(requested.getVesselId());
		}
		if (StringUtils.hasText(requested.getBuyerOrgId())) {
			if (!remoteOrderContextService.isUsableBuyerOrgMember(context.getTenantId(), context.getUserId(), requested.getBuyerOrgId())) {
				throw new ArynBusinessException("样本用户不属于指定采购主体");
			}
			context.setBuyerOrgId(requested.getBuyerOrgId());
		}
		if ("2".equals(context.getPurchaseScene()) && context.getVesselId() == null
				|| "3".equals(context.getPurchaseScene()) && context.getBuyerOrgId() == null) {
			throw new ArynBusinessException("采购场景缺少有效主体");
		}
		if (StringUtils.hasText(requested.getVesselCallId())) {
			var call = remoteVesselService.getVesselCallSnapshot(context.getTenantId(), requested.getVesselCallId());
			if (call == null || !Objects.equals(context.getVesselId(), call.getVesselId())) {
				throw new ArynBusinessException("靠港计划与船舶不匹配");
			}
			context.setVesselCallId(call.getVesselCallId());
			context.setPortCode(call.getPortCode());
		}
		List<PromotionContextDTO.SkuItem> requestedItems = requested.getSkuItems();
		if (requestedItems == null || requestedItems.isEmpty() || requestedItems.size() > 100) {
			throw new ArynBusinessException("每个样本需1至100个SKU");
		}
		Set<String> ids = new LinkedHashSet<>();
		for (var item : requestedItems) {
			if (item == null || !StringUtils.hasText(item.getSkuId()) || !ids.add(item.getSkuId())
					|| item.getQuantity() == null || item.getQuantity() < 1) {
				throw new ArynBusinessException("SKU不得重复，数量必须为正整数");
			}
		}
		List<GoodsSku> skus = remoteGoodsSkuService.getBySkuIds(new ArrayList<>(ids));
		if (skus == null || skus.size() != ids.size()) { throw new ArynBusinessException("样本商品已失效"); }
		Map<String, GoodsSku> byId = skus.stream().collect(Collectors.toMap(GoodsSku::getId, sku -> sku));
		List<PromotionContextDTO.SkuItem> items = new ArrayList<>();
		for (var source : requestedItems) {
			GoodsSku sku = byId.get(source.getSkuId());
			if (sku == null || !context.getTenantId().equals(sku.getTenantId()) || !"0".equals(sku.getStatus())
					|| sku.getGoodsSpu() == null || !"1".equals(sku.getGoodsSpu().getStatus())
					|| sku.getSalesPrice() == null || sku.getSalesPrice().signum() <= 0) {
				throw new ArynBusinessException("样本商品不可销售");
			}
			var item = new PromotionContextDTO.SkuItem();
			item.setSkuId(sku.getId());
			item.setQuantity(source.getQuantity());
			item.setSalesPrice(sku.getSalesPrice());
			item.setActivityPriced(false);
			items.add(item);
		}
		context.setSkuItems(items);
		OrderScenarioFactsVO facts = new OrderScenarioFactsVO();
		facts.setContext(context);
		facts.setSkus(skus);
		String subjectType = EnterpriseMemberConstants.subjectTypeForScene(context.getPurchaseScene());
		if (subjectType == null) {
			facts.setMemberBenefits(remoteMallUserService.getMemberBenefits(context.getUserId()));
			var setting = remoteMallZoneSettingService.getSetting(context.getTenantId(), "member");
			facts.setMemberCostFloorMultiplier(setting == null ? null : setting.getCostFloorMultiplier());
		}
		else {
			String subjectId = "2".equals(context.getPurchaseScene()) ? context.getVesselId() : context.getBuyerOrgId();
			var benefits = remoteEnterpriseUserService.getSubjectBenefits(context.getTenantId(), subjectType, subjectId);
			if (benefits == null) { benefits = new com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO(); }
			facts.setEnterpriseBenefits(benefits);
		}
		if (StringUtils.hasText(couponUserId)) {
			CouponUserRespVO coupon = remoteCouponUserService.getById(couponUserId, context.getUserId());
			if (coupon == null || coupon.getCouponInfo() == null
					|| !context.getUserId().equals(coupon.getUserId())
					|| !context.getTenantId().equals(coupon.getTenantId())
					|| !context.getTenantId().equals(coupon.getCouponInfo().getTenantId())) {
				throw new ArynBusinessException("样本券不存在或不属于当前用户");
			}
			facts.setCoupon(coupon);
		}
		pricing.captureScenarioSettings(facts);
		return facts;
	}

	@Override
	public CampaignOrderComparisonVO compare(OrderScenarioFactsVO facts, PromotionCalculationVO baseline,
											  PromotionCalculationVO proposed) {
		requireTenant(facts == null ? null : facts.getContext());
		// 克隆数据与设置：计算中的券状态和订单明细变更不能污染共享事实或另一方案。
		var immutableInput = JSONUtil.toBean(JSONUtil.toJsonStr(facts), OrderScenarioFactsVO.class);
		OrderPriceComputeService pure = OrderPriceComputeService.forScenario(immutableInput);
		var comparison = new CampaignOrderComparisonVO();
		comparison.setBaseline(evaluate(pure, immutableInput, baseline));
		if (immutableInput.getProposedCoupon() != null) { immutableInput.setCoupon(immutableInput.getProposedCoupon()); }
		comparison.setProposed(evaluate(pure, immutableInput, proposed));
		return comparison;
	}

	private CampaignOrderComparisonVO.Quote evaluate(OrderPriceComputeService pure, OrderScenarioFactsVO facts,
													 PromotionCalculationVO calculation) {
		var context = facts.getContext();
		List<CreateOrderSkuReqDTO> requests = context.getSkuItems().stream().map(item -> {
			var request = new CreateOrderSkuReqDTO();
			request.setSkuId(item.getSkuId());
			request.setQuantity(item.getQuantity());
			return request;
		}).toList();
		var items = pure.generateOrderItems(facts.getSkus(), requests);
		OrderInfo order = new OrderInfo().setUserId(context.getUserId()).setPurchaseScene(context.getPurchaseScene());
		if (calculation != null && calculation.getLadderOverrides() != null) { pure.applyLadderOverrides(items, calculation); }
		var plan = pure.scenarioPricePlan(calculation);
		pure.applyZoneSourceSnapshots(items, calculation);
		pure.computeOrderPrice(order, items);
		if (facts.getEnterpriseBenefits() != null) {
			pure.orderEnterpriseBenefitHandler(order, items, facts.getEnterpriseBenefits(), plan);
		}
		else {
			pure.orderMemberBenefitHandler(order, items, facts.getMemberBenefits(), plan, facts.getMemberCostFloorMultiplier());
		}
		String couponReason = null;
		if (facts.getCoupon() != null) {
			order.setCouponUserId("captured-only");
			CouponUserRespVO coupon = JSONUtil.toBean(JSONUtil.toJsonStr(facts.getCoupon()), CouponUserRespVO.class);
			try { pure.orderCouponHandler(order, items, plan, false, coupon, context.getActivityTime()); }
			catch (ArynBusinessException invalidCoupon) { couponReason = invalidCoupon.getMsg(); }
		}
		pure.applyShipWholeDiscount(order, items, calculation, plan);
		CampaignOrderComparisonVO.Quote quote = new CampaignOrderComparisonVO.Quote();
		quote.setGoodsPayable(order.getPaymentPrice());
		quote.setCouponDiscount(order.getCouponPrice());
		quote.setMemberDiscount(order.getMemberDiscountPrice());
		quote.setWholeDiscount(order.getPromoPrice());
		quote.setCouponReason(couponReason == null ? order.getCouponStackReason() : couponReason);
		quote.setLines(items.stream().map(item -> {
			var line = new CampaignOrderComparisonVO.Line();
			line.setSkuId(item.getSkuId());
			line.setQuantity(item.getBuyQuantity());
			line.setUnitPrice(item.getSalesPrice());
			line.setGoodsPayable(item.getPaymentPrice());
			line.setCouponDiscount(item.getCouponPrice());
			line.setMemberDiscount(item.getMemberDiscountPrice());
			line.setWholeDiscount(item.getPromoPrice());
			return line;
		}).toList());
		return quote;
	}

	private void requireTenant(PromotionContextDTO context) {
		String tenant = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenant) || context == null || !tenant.equals(context.getTenantId())) {
			throw new ArynBusinessException("模拟租户上下文不匹配");
		}
	}
}
