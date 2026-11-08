package com.aryn.cloud.order.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 订单级支付提交门禁：在调用支付渠道前先用短事务写入提交标记，
 * 与取消订单的条件更新争用同一列，避免「本地未建支付单时取消释放库存」。
 */
@Service
@RequiredArgsConstructor
public class OrderPaySubmitGateService {

	private static final long STALE_MINUTES = 2;

	private static final String CANCEL_LOCK = "2";

	private final OrderInfoMapper orderInfoMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void begin(String orderId) {
		String tenantId = requireTenant();
		LocalDateTime now = LocalDateTime.now();
		int updated = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderId)
			.eq(OrderInfo::getTenantId, tenantId)
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
			.and(wrapper -> wrapper.eq(OrderInfo::getPaySubmitting, CommonConstants.NO)
				.or()
				.isNull(OrderInfo::getPaySubmitting)
				.or(stale -> stale.in(OrderInfo::getPaySubmitting, CommonConstants.YES, CANCEL_LOCK)
					.lt(OrderInfo::getPaySubmitTime, now.minusMinutes(STALE_MINUTES))))
			.set(OrderInfo::getPaySubmitting, CommonConstants.YES)
			.set(OrderInfo::getPaySubmitTime, now));
		if (updated == 0) {
			throw new ArynBusinessException("订单正在发起支付或取消，请稍后重试");
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void beginCancel(String orderId) {
		String tenantId = requireTenant();
		LocalDateTime now = LocalDateTime.now();
		int updated = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderId)
			.eq(OrderInfo::getTenantId, tenantId)
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
			.and(wrapper -> wrapper.eq(OrderInfo::getPaySubmitting, CommonConstants.NO)
				.or()
				.isNull(OrderInfo::getPaySubmitting)
				.or(stale -> stale.in(OrderInfo::getPaySubmitting, CommonConstants.YES, CANCEL_LOCK)
					.lt(OrderInfo::getPaySubmitTime, now.minusMinutes(STALE_MINUTES))))
			.set(OrderInfo::getPaySubmitting, CANCEL_LOCK)
			.set(OrderInfo::getPaySubmitTime, now));
		if (updated == 0) {
			throw new ArynBusinessException("订单正在支付或状态已变化，暂不能取消");
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void clear(String orderId) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return;
		}
		orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderId)
			.eq(OrderInfo::getTenantId, tenantId)
			.set(OrderInfo::getPaySubmitting, CommonConstants.NO)
			.set(OrderInfo::getPaySubmitTime, null));
	}

	public boolean isSubmittingRecently(OrderInfo orderInfo) {
		if (orderInfo == null || orderInfo.getPaySubmitTime() == null) {
			return false;
		}
		boolean locked = CommonConstants.YES.equals(orderInfo.getPaySubmitting())
				|| CANCEL_LOCK.equals(orderInfo.getPaySubmitting());
		return locked && orderInfo.getPaySubmitTime().isAfter(LocalDateTime.now().minusMinutes(STALE_MINUTES));
	}

	private String requireTenant() {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("缺少租户上下文");
		}
		return tenantId;
	}

}
