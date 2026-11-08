package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderPaySubmitGateServiceTest {

	private final OrderInfoMapper mapper = mock(OrderInfoMapper.class);

	private final OrderPaySubmitGateService service = new OrderPaySubmitGateService(mapper);

	@BeforeAll
	static void initTableInfo() {
		com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
				new org.apache.ibatis.builder.MapperBuilderAssistant(
						new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
				OrderInfo.class);
	}

	@AfterEach
	void tearDown() {
		com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void beginRejectsWhenAnotherSubmitWonTheGate() {
		com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.setTenantId("tenant-1");
		when(mapper.update(any(), any())).thenReturn(0);

		assertThrows(ArynBusinessException.class, () -> service.begin("order-1"));
	}

	@Test
	void beginCommitsWhenGateIsFree() {
		com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.setTenantId("tenant-1");
		when(mapper.update(any(), any())).thenReturn(1);

		assertDoesNotThrow(() -> service.begin("order-1"));
	}

	@Test
	void beginCancelRejectsWhenPaymentOrAnotherCancelWonTheGate() {
		com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.setTenantId("tenant-1");
		when(mapper.update(any(), any())).thenReturn(0);

		assertThrows(ArynBusinessException.class, () -> service.beginCancel("order-1"));
	}

	@Test
	void beginCancelAcquiresCancelLock() {
		com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.setTenantId("tenant-1");
		when(mapper.update(any(), any())).thenReturn(1);

		assertDoesNotThrow(() -> service.beginCancel("order-1"));
	}

	@Test
	void recentSubmittingMarkerBlocksCancelButStaleMarkerDoesNot() {
		OrderInfo recent = new OrderInfo().setPaySubmitting("1")
			.setPaySubmitTime(java.time.LocalDateTime.now().minusSeconds(30));
		OrderInfo stale = new OrderInfo().setPaySubmitting("1")
			.setPaySubmitTime(java.time.LocalDateTime.now().minusMinutes(10));

		OrderInfo canceling = new OrderInfo().setPaySubmitting("2")
			.setPaySubmitTime(java.time.LocalDateTime.now().minusSeconds(30));
		assertTrue(service.isSubmittingRecently(recent));
		assertTrue(service.isSubmittingRecently(canceling));
		assertFalse(service.isSubmittingRecently(stale));
		assertFalse(service.isSubmittingRecently(new OrderInfo().setPaySubmitting("0")));
	}

}
