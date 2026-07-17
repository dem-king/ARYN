package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.mapper.DistributionConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionConfigServiceImplTest {

	@Test
	void rejectsInvalidCommissionRatesAndSettlementCycle() {
		DistributionConfigServiceImpl service = serviceWithMapper(mock(DistributionConfigMapper.class));

		assertThatThrownBy(() -> service.save(validConfig().setCommissionRate(new BigDecimal("1.01"))))
			.isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> service.save(validConfig()
			.setCommissionRate(new BigDecimal("0.80"))
			.setCommissionRateLevel2(new BigDecimal("0.30"))))
			.isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> service.save(validConfig().setSettleCycleDays(-1)))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void savingEnabledConfigDisablesPreviousActiveConfig() {
		DistributionConfigMapper mapper = mock(DistributionConfigMapper.class);
		DistributionConfigServiceImpl service = serviceWithMapper(mapper);
		DistributionConfig previous = validConfig().setId("old");
		DistributionConfig target = validConfig().setId("new");
		when(mapper.selectList(any())).thenReturn(List.of(previous));
		when(mapper.updateById(any(DistributionConfig.class))).thenReturn(1);
		when(mapper.insert(any(DistributionConfig.class))).thenReturn(1);

		assertThat(service.save(target)).isTrue();

		assertThat(previous.getStatus()).isEqualTo(MallEventConstants.DISTRIBUTION_CONFIG_STATUS_DISABLE);
		verify(mapper).updateById(previous);
		verify(mapper).insert(target);
	}

	private DistributionConfigServiceImpl serviceWithMapper(DistributionConfigMapper mapper) {
		DistributionConfigServiceImpl service = new DistributionConfigServiceImpl();
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		return service;
	}

	private DistributionConfig validConfig() {
		return new DistributionConfig()
			.setConfigName("default")
			.setCommissionRate(new BigDecimal("0.10"))
			.setCommissionRateLevel2(new BigDecimal("0.05"))
			.setMinWithdrawAmount(new BigDecimal("10.00"))
			.setSettleCycleDays(7)
			.setStatus(MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE);
	}
}
