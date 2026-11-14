package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.vo.FrequentPurchaseVO;
import com.aryn.cloud.order.mapper.BuyerOrgMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService;
import com.aryn.cloud.promotion.api.vo.FirstOrderEligibilityVO;
import com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 买家画像远程实现：常购 SKU 判定（近 90 天有效订单统计）与新人资格判定。 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteBuyerProfileServiceImpl implements RemoteBuyerProfileService {

	private static final int FREQUENT_LIMIT = 100;

	private final OrderItemMapper orderItemMapper;

	private final OrderInfoMapper orderInfoMapper;

	private final BuyerOrgMapper buyerOrgMapper;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	@Override
	public List<String> frequentSkuIds(String tenantId, String userId) {
		if (tenantId == null || userId == null) {
			return List.of();
		}
		List<FrequentPurchaseVO> frequent = orderItemMapper.selectFrequentPurchase(tenantId, userId,
				LocalDateTime.now().minusDays(90), FREQUENT_LIMIT);
		return frequent.stream().map(FrequentPurchaseVO::getSkuId).toList();
	}

	/**
	 * 新人资格判定：本租户未成功支付过订单 + 注册 N 天内。
	 *
	 * <p>查询前把租户上下文快照替换为显式入参 tenantId 再还原：order_info/user_info 均在
	 * 租户拦截表白名单内，若上下文缺失会被注入 tenant_id = NULL 而返回 0 行，
	 * 「查不到已支付订单」将误判为新人（fail-open，白送折扣）。显式租户保证判定口径确定，
	 * 任何异常一律按非新人返回（fail-closed）。
	 */
	@Override
	public NewbieEligibilityVO checkNewbieEligibility(String tenantId, String userId, Integer registerWithinDays) {
		return checkPersonalNewbieEligibility(tenantId, userId, registerWithinDays);
	}

	@Override
	public NewbieEligibilityVO checkPersonalNewbieEligibility(String tenantId, String userId,
			Integer registerWithinDays) {
		NewbieEligibilityVO result = new NewbieEligibilityVO();
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
			return reject(result, "PARAM_INVALID");
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			// 口径：付过款即消耗资格（含退款），pay_status 为单向状态由支付回调/确认收款推进。
			// 只算个人单（scene=1 或历史空场景），船供/企业单不消耗个人新人资格。
			Long paidOrders = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getUserId, userId)
				.eq(OrderInfo::getPayStatus, CommonConstants.YES)
				.and(w -> w.eq(OrderInfo::getPurchaseScene, "1").or().isNull(OrderInfo::getPurchaseScene)));
			if (paidOrders != null && paidOrders > 0) {
				return reject(result, "ALREADY_PAID");
			}
			if (registerWithinDays != null && registerWithinDays > 0) {
				LocalDateTime registerTime = remoteMallUserService.getUserRegisterTime(userId);
				if (registerTime == null) {
					// 用户不存在或跨租户查询：无法证明是新人，按不合格处理
					return reject(result, "PARAM_INVALID");
				}
				if (registerTime.plusDays(registerWithinDays).isBefore(LocalDateTime.now())) {
					return reject(result, "REGISTER_TIMEOUT");
				}
			}
			result.setEligible(Boolean.TRUE);
			return result;
		}
		catch (Exception ex) {
			log.warn("新人资格判定异常，按非新人处理 tenant={} user={}", tenantId, userId, ex);
			return reject(result, "ERROR");
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
	public FirstOrderEligibilityVO checkShipFirstOrderEligibility(String tenantId, String vesselId) {
		FirstOrderEligibilityVO result = firstOrderResult(FirstOrderEligibilityVO.SUBJECT_SHIP, vesselId);
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(vesselId)) {
			result.setReason("PARAM_INVALID");
			return result;
		}
		return withTenant(tenantId, () -> {
			Long paidOrders = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getVesselId, vesselId)
				.eq(OrderInfo::getPurchaseScene, "2")
				.eq(OrderInfo::getPayStatus, CommonConstants.YES));
			result.setEligible(paidOrders == null || paidOrders == 0);
			if (!Boolean.TRUE.equals(result.getEligible())) {
				result.setReason("ALREADY_PAID");
			}
			return result;
		}, result, "ERROR");
	}

	@Override
	public FirstOrderEligibilityVO checkBuyerOrgFirstOrderEligibility(String tenantId, String buyerOrgId) {
		FirstOrderEligibilityVO result = firstOrderResult(FirstOrderEligibilityVO.SUBJECT_BUYER_ORG, buyerOrgId);
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(buyerOrgId)) {
			result.setReason("PARAM_INVALID");
			return result;
		}
		return withTenant(tenantId, () -> {
			BuyerOrg org = buyerOrgMapper.selectOne(Wrappers.<BuyerOrg>lambdaQuery()
				.eq(BuyerOrg::getTenantId, tenantId)
				.eq(BuyerOrg::getId, buyerOrgId)
				.last("LIMIT 1"));
			if (org == null) {
				result.setEligible(Boolean.FALSE);
				result.setReason("ORG_NOT_FOUND");
				return result;
			}
			if (!"1".equals(org.getAuditStatus())) {
				result.setEligible(Boolean.FALSE);
				result.setReason("ORG_NOT_APPROVED");
				return result;
			}
			if (!"1".equals(org.getStatus())) {
				result.setEligible(Boolean.FALSE);
				result.setReason("ORG_DISABLED");
				return result;
			}
			// 只统计「下单时主体已审核通过」的已支付单：待审核期间下的单不享首单，
			// 支付后也不消耗首单，否则工厂审核通过反而失去首单资格。
			Long paidOrders = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getBuyerOrgId, buyerOrgId)
				.eq(OrderInfo::getPurchaseScene, "3")
				.eq(OrderInfo::getPayStatus, CommonConstants.YES)
				.eq(OrderInfo::getBuyerOrgAuditStatus, "1"));
			result.setEligible(paidOrders == null || paidOrders == 0);
			if (!Boolean.TRUE.equals(result.getEligible())) {
				result.setReason("ALREADY_PAID");
			}
			return result;
		}, result, "ERROR");
	}

	private FirstOrderEligibilityVO firstOrderResult(String subjectType, String subjectId) {
		FirstOrderEligibilityVO result = new FirstOrderEligibilityVO();
		result.setSubjectType(subjectType);
		result.setSubjectId(subjectId);
		result.setEligible(Boolean.FALSE);
		return result;
	}

	private <T> T withTenant(String tenantId, java.util.function.Supplier<T> supplier, T fallback, String reason) {
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			return supplier.get();
		}
		catch (Exception ex) {
			log.warn("首单资格判定异常，按不合格处理 tenant={}: {}", tenantId, ex.getMessage());
			if (fallback instanceof FirstOrderEligibilityVO firstOrder) {
				firstOrder.setEligible(Boolean.FALSE);
				firstOrder.setReason(reason);
			}
			return fallback;
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

	private NewbieEligibilityVO reject(NewbieEligibilityVO result, String reason) {
		result.setEligible(Boolean.FALSE);
		result.setReason(reason);
		return result;
	}

}
