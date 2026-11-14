package com.aryn.cloud.user.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.service.IEnterpriseMemberService;
import com.aryn.cloud.user.service.IMemberOrderGrowthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_COMPLETE_NOTIFY_TOPIC,
		consumerGroup = "user-service-member-growth-group")
public class MemberOrderCompleteListener implements RocketMQListener<OrderCompleteEvent> {

	private final IMemberOrderGrowthService growthService;

	private final IEnterpriseMemberService enterpriseMemberService;

	@Override
	public void onMessage(OrderCompleteEvent event) {
		try {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			growthService.processOrderComplete(event);
			// 双轨会员企业轨：scene=2/3 订单按主体累计采购额并重算企业等级；
			// fail-open（实现内部捕获），不影响个人积分/等级主流程
			enterpriseMemberService.accumulateOrderGrowth(event);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
