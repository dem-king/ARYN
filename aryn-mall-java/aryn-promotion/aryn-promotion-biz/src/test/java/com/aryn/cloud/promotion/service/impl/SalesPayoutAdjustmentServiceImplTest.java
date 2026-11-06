package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;
import com.aryn.cloud.promotion.mapper.SalesPayoutAdjustmentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提成负数结转服务单测：待结转调整必须按「创建时间 < 台账月次月 1 日 0 点」过滤，避免之后产生的扣减被提前结转
 */
class SalesPayoutAdjustmentServiceImplTest {

	private static final LocalDateTime CREATE_TIME_BEFORE = LocalDateTime.of(2026, 11, 1, 0, 0);

	private SalesPayoutAdjustmentMapper mapper;

	private SalesPayoutAdjustmentServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				SalesPayoutAdjustment.class);
		mapper = mock(SalesPayoutAdjustmentMapper.class);
		service = new SalesPayoutAdjustmentServiceImpl();
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
	}

	@Test
	void listPendingFiltersByStatusAndCreatedTimeBefore() {
		when(mapper.selectList(any())).thenReturn(List.of());

		service.listPending(CREATE_TIME_BEFORE);

		AbstractWrapper<?, ?, ?> wrapper = capturedWrapper();
		// 条件必须是 create_time < 台账月末，而不是仅存在 create_time 字段
		assertThat(wrapper.getSqlSegment()).contains("status", "create_time <");
		assertThat(wrapper.getParamNameValuePairs().values()).contains("0", CREATE_TIME_BEFORE);
	}

	@Test
	void listPendingByStaffFiltersByStaffStatusAndCreatedTimeBefore() {
		when(mapper.selectList(any())).thenReturn(List.of());

		service.listPendingByStaff("staff-1", CREATE_TIME_BEFORE);

		AbstractWrapper<?, ?, ?> wrapper = capturedWrapper();
		assertThat(wrapper.getSqlSegment()).contains("sales_staff_id", "status", "create_time <");
		assertThat(wrapper.getParamNameValuePairs().values()).contains("staff-1", "0", CREATE_TIME_BEFORE);
	}

	private AbstractWrapper<?, ?, ?> capturedWrapper() {
		ArgumentCaptor<Wrapper<SalesPayoutAdjustment>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).selectList(captor.capture());
		return (AbstractWrapper<?, ?, ?>) captor.getValue();
	}

}
