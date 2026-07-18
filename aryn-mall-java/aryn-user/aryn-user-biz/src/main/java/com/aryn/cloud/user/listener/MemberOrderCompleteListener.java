package com.aryn.cloud.user.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.service.IMemberOrderGrowthService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_COMPLETE_NOTIFY_TOPIC,
		consumerGroup = "user-service-member-growth-group")
public class MemberOrderCompleteListener implements RocketMQListener<OrderCompleteEvent> {

	private final IMemberOrderGrowthService growthService;

	@Override
	public void onMessage(OrderCompleteEvent event) {
		try {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			growthService.processOrderComplete(event);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
