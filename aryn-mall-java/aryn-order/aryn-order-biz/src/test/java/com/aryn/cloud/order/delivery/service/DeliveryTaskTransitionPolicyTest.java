package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import org.junit.jupiter.api.Test;

import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.ASSIGNED;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.CLOSED;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.DELIVERED;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.DELIVERING;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.EXCEPTION;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.PICKING;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.RETURN_PENDING;
import static com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum.WAITING_ASSIGNMENT;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTaskTransitionPolicyTest {

	private final DeliveryTaskTransitionPolicy policy = new DeliveryTaskTransitionPolicy();

	@Test
	void acceptsAllDocumentedTransitions() {
		assertAllowed(WAITING_ASSIGNMENT, ASSIGNED);
		assertAllowed(WAITING_ASSIGNMENT, CLOSED);
		assertAllowed(ASSIGNED, PICKING);
		assertAllowed(ASSIGNED, EXCEPTION);
		assertAllowed(ASSIGNED, CLOSED);
		assertAllowed(PICKING, DELIVERING);
		assertAllowed(PICKING, EXCEPTION);
		assertAllowed(PICKING, CLOSED);
		assertAllowed(DELIVERING, DELIVERED);
		assertAllowed(DELIVERING, EXCEPTION);
		assertAllowed(DELIVERING, RETURN_PENDING);
		assertAllowed(DELIVERED, RETURN_PENDING);
		assertAllowed(EXCEPTION, ASSIGNED);
		assertAllowed(EXCEPTION, RETURN_PENDING);
		assertAllowed(EXCEPTION, CLOSED);
		assertAllowed(RETURN_PENDING, CLOSED);
	}

	@Test
	void rejectsTerminalRollbackSkippingAndSelfTransitions() {
		assertRejected(DELIVERED, ASSIGNED);
		assertRejected(CLOSED, PICKING);
		assertRejected(WAITING_ASSIGNMENT, DELIVERED);
		assertRejected(ASSIGNED, DELIVERING);
		assertRejected(DELIVERING, PICKING);
		for (DeliveryTaskStatusEnum status : DeliveryTaskStatusEnum.values()) {
			assertRejected(status, status);
		}
	}

	@Test
	void rejectsNullStates() {
		assertThat(policy.canTransit(null, ASSIGNED)).isFalse();
		assertThat(policy.canTransit(ASSIGNED, null)).isFalse();
	}

	private void assertAllowed(DeliveryTaskStatusEnum from, DeliveryTaskStatusEnum to) {
		assertThat(policy.canTransit(from, to)).as(from + " -> " + to).isTrue();
	}

	private void assertRejected(DeliveryTaskStatusEnum from, DeliveryTaskStatusEnum to) {
		assertThat(policy.canTransit(from, to)).as(from + " -> " + to).isFalse();
	}

}
