package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.mapper.SalesOrderMapper;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售业绩单服务单测：批量置已发放时手工推进 version，堵住并发退款把已发放订单回写为可发放的窗口
 */
class SalesOrderServiceImplTest {

	private SalesOrderMapper mapper;

	private SalesOrderServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				SalesOrder.class);
		mapper = mock(SalesOrderMapper.class);
		service = new SalesOrderServiceImpl(mock(ISalesStaffService.class), mock(SalesUserEnricher.class));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
	}

	@Test
	void markPayoutPaidAdvancesVersionAndOnlyTargetsPayableOrdersInBatch() {
		when(mapper.update(any(SalesOrder.class), any(Wrapper.class))).thenReturn(2);
		LocalDateTime settleTime = LocalDateTime.of(2026, 11, 1, 0, 0);

		assertThat(service.markPayoutPaid("payout-1", settleTime)).isEqualTo(2);

		ArgumentCaptor<SalesOrder> entityCaptor = ArgumentCaptor.forClass(SalesOrder.class);
		ArgumentCaptor<Wrapper<SalesOrder>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).update(entityCaptor.capture(), wrapperCaptor.capture());
		SalesOrder update = entityCaptor.getValue();
		assertThat(update.getStatus()).isEqualTo("2");
		assertThat(update.getSettleTime()).isEqualTo(settleTime);
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		// 批量更新不经过 @Version 乐观锁，必须显式推进 version，否则并发退款的旧 version updateById 仍能命中
		assertThat(wrapper.getSqlSet()).contains("version = COALESCE(version, 0) + 1");
		assertThat(wrapper.getSqlSegment()).contains("payout_id", "status");
		assertThat(wrapper.getParamNameValuePairs().values()).contains("payout-1", "1");
	}

	@Test
	void assignPayoutAdvancesVersionAndOnlyTargetsUnbatchedPayableOrdersInMonth() {
		when(mapper.update(any(SalesOrder.class), any(Wrapper.class))).thenReturn(1);
		LocalDateTime payTimeBefore = LocalDateTime.of(2026, 11, 1, 0, 0);

		assertThat(service.assignPayout("staff-1", "payout-1", payTimeBefore)).isEqualTo(1);

		ArgumentCaptor<SalesOrder> entityCaptor = ArgumentCaptor.forClass(SalesOrder.class);
		ArgumentCaptor<Wrapper<SalesOrder>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).update(entityCaptor.capture(), wrapperCaptor.capture());
		assertThat(entityCaptor.getValue().getPayoutId()).isEqualTo("payout-1");
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		// 入批同样是批量更新，必须推进 version，否则并发退款按旧 version 仍能命中并跳过重算造成部分退款超付
		assertThat(wrapper.getSqlSet()).contains("version = COALESCE(version, 0) + 1");
		assertThat(wrapper.getSqlSegment()).contains("sales_staff_id", "status", "payout_id", "pay_time");
		assertThat(wrapper.getParamNameValuePairs().values()).contains("staff-1", "1", payTimeBefore);
	}

}
