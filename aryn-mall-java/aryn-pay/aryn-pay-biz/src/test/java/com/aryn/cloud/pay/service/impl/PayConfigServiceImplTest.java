package com.aryn.cloud.pay.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayConfigServiceImplTest {

	private PayConfigMapper payConfigMapper;

	private PayTradeOrderMapper tradeOrderMapper;

	private PayConfigServiceImpl service;

	@BeforeEach
	void setUp() {
		payConfigMapper = mock(PayConfigMapper.class);
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		service = new PayConfigServiceImpl(mock(PayConfigRevisionMapper.class), tradeOrderMapper);
		ReflectionTestUtils.setField(service, "baseMapper", payConfigMapper);
	}

	@Test
	void configWithAnyHistoricalOrderCannotBeDeleted() {
		PayConfig config = PayTestFixtures.config("config-1", "1", "0");
		when(payConfigMapper.selectById("config-1")).thenReturn(config);
		when(tradeOrderMapper.countByChannelTerminal(PayTestFixtures.TENANT, "0", "WX_")).thenReturn(1L);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.deleteConfig("config-1"));
		assertFalse(exception.getMsg().isEmpty());
		verify(payConfigMapper, never()).deleteById(anyString());
	}

	@Test
	void unusedConfigCanBeLogicallyDeleted() {
		PayConfig config = PayTestFixtures.config("config-1", "1", "0");
		when(payConfigMapper.selectById("config-1")).thenReturn(config);
		when(tradeOrderMapper.countByChannelTerminal(PayTestFixtures.TENANT, "0", "WX_")).thenReturn(0L);
		when(payConfigMapper.deleteById("config-1")).thenReturn(1);

		service.deleteConfig("config-1");

		verify(payConfigMapper).deleteById("config-1");
	}

}
