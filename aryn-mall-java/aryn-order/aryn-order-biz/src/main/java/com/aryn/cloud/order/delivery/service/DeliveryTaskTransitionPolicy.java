package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.ASSIGNED;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.CLOSED;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.DELIVERED;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.DELIVERING;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.EXCEPTION;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.PICKING;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.RETURN_PENDING;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.WAITING_ASSIGNMENT;

/**
 * 商城配送任务纯状态转换策略，不访问数据库或外部服务。
 */
@Component
public class DeliveryTaskTransitionPolicy {

	private static final Map<DeliveryTaskStatusEnum, Set<DeliveryTaskStatusEnum>> TRANSITIONS = transitions();

	public boolean canTransit(DeliveryTaskStatusEnum from, DeliveryTaskStatusEnum to) {
		return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
	}

	private static Map<DeliveryTaskStatusEnum, Set<DeliveryTaskStatusEnum>> transitions() {
		Map<DeliveryTaskStatusEnum, Set<DeliveryTaskStatusEnum>> transitions =
				new EnumMap<>(DeliveryTaskStatusEnum.class);
		transitions.put(WAITING_ASSIGNMENT, EnumSet.of(ASSIGNED, CLOSED));
		transitions.put(ASSIGNED, EnumSet.of(PICKING, EXCEPTION, CLOSED));
		transitions.put(PICKING, EnumSet.of(DELIVERING, EXCEPTION, CLOSED));
		transitions.put(DELIVERING, EnumSet.of(DELIVERED, EXCEPTION, RETURN_PENDING));
		transitions.put(DELIVERED, EnumSet.of(RETURN_PENDING));
		transitions.put(EXCEPTION, EnumSet.of(ASSIGNED, RETURN_PENDING, CLOSED));
		transitions.put(RETURN_PENDING, EnumSet.of(CLOSED));
		return Map.copyOf(transitions);
	}

}
