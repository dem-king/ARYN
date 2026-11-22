package com.aryn.cloud.user.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.service.IEnterpriseMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 退款成功事件监听器-企业成长值回扣（2026-11-15 审查修复新增）。
 *
 * <p>双轨会员企业轨：scene=2/3 订单退款按主体回扣采购额（回扣到原订单完成月份）
 * 并重算企业等级；幂等按 refundNo 落台账，fail-open 不影响其他退款消费方。
 * 与积分返还（订单侧 bizNo 幂等）消费同一 topic、独立消费组。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "user-service-enterprise-growth-refund-group")
public class EnterpriseGrowthRefundListener implements RocketMQListener<OrderRefundSuccessEvent> {

	private final IEnterpriseMemberService enterpriseMemberService;

	@Override
	public void onMessage(OrderRefundSuccessEvent event) {
		try {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			enterpriseMemberService.deductOrderGrowth(event);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}