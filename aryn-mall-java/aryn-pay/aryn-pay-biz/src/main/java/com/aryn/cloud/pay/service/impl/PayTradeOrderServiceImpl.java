package com.aryn.cloud.pay.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayPrepayGateResult;
import com.aryn.cloud.pay.api.enums.PayTradeStatusEnum;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付订单生命周期。所有状态推进都是条件更新，旧 pay_status 仅做 0/1 兼容投影。
 */
@Service
public class PayTradeOrderServiceImpl extends ServiceImpl<PayTradeOrderMapper, PayTradeOrder>
		implements IPayTradeOrderService {

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public PayPrepayGateResult beginPrepay(PayTradeOrder order) {
		String tenantId = tenantId(order);
		if (CommonConstants.YES.equals(order.getPayStatus())) {
			return PayPrepayGateResult.ALREADY_PAID;
		}
		int updated = baseMapper.markSubmitting(tenantId, order.getId());
		if (updated == 1) {
			order.setTradeStatus(PayTradeStatusEnum.SUBMITTING.name());
			return PayPrepayGateResult.STARTED;
		}
		PayTradeOrder current = baseMapper.selectById(order.getId());
		if (current == null) {
			throw new ArynBusinessException("支付单不存在");
		}
		if (CommonConstants.YES.equals(current.getPayStatus())) {
			return PayPrepayGateResult.ALREADY_PAID;
		}
		PayTradeStatusEnum status = PayTradeStatusEnum.fromCode(current.getTradeStatus());
		if (status == PayTradeStatusEnum.CLOSED) {
			return PayPrepayGateResult.CLOSED;
		}
		return PayPrepayGateResult.IN_PROGRESS;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markPending(PayTradeOrder order) {
		baseMapper.markPending(tenantId(order), order.getId());
		order.setTradeStatus(PayTradeStatusEnum.PENDING.name());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markUnknown(PayTradeOrder order, String errCode, String errMsg) {
		baseMapper.markUnknown(tenantId(order), order.getId(), errCode, truncate(errMsg));
		order.setTradeStatus(PayTradeStatusEnum.UNKNOWN.name());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markClosing(PayTradeOrder order) {
		baseMapper.markClosing(tenantId(order), order.getId());
		order.setTradeStatus(PayTradeStatusEnum.CLOSING.name());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public boolean markClosed(PayTradeOrder order) {
		int updated = baseMapper.markClosed(tenantId(order), order.getId());
		if (updated == 1) {
			order.setTradeStatus(PayTradeStatusEnum.CLOSED.name());
			return true;
		}
		return false;
	}

	private String tenantId(PayTradeOrder order) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			tenantId = order.getTenantId();
		}
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("缺少租户上下文");
		}
		return tenantId;
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() > 240 ? value.substring(0, 240) : value;
	}

}
