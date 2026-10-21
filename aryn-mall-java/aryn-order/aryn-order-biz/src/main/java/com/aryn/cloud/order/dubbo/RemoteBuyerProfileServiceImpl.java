package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.vo.FrequentPurchaseVO;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService;
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
		NewbieEligibilityVO result = new NewbieEligibilityVO();
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
			return reject(result, "PARAM_INVALID");
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			// 口径：付过款即消耗资格（含退款），pay_status 为单向状态由支付回调/确认收款推进
			Long paidOrders = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getUserId, userId)
				.eq(OrderInfo::getPayStatus, CommonConstants.YES));
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

	private NewbieEligibilityVO reject(NewbieEligibilityVO result, String reason) {
		result.setEligible(Boolean.FALSE);
		result.setReason(reason);
		return result;
	}

}
