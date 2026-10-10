package com.aryn.cloud.vessel.service;

import com.aryn.cloud.vessel.mapper.VesselCallMapper;
import com.aryn.cloud.vessel.service.impl.VesselServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 靠港状态按时间推进的契约测试：计划中→靠泊中→已完成。
 */
class VesselCallStatusRefreshTest {

	private static final String TENANT = "tenant-1";

	private VesselCallMapper vesselCallMapper;

	private VesselServiceImpl service;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), VesselCall.class);
	}

	@BeforeEach
	void setUp() {
		vesselCallMapper = mock(VesselCallMapper.class);
		service = new VesselServiceImpl(mock(com.aryn.cloud.vessel.mapper.VesselInfoMapper.class),
				mock(com.aryn.cloud.vessel.mapper.VesselMemberMapper.class), vesselCallMapper,
				mock(com.aryn.cloud.vessel.mapper.VesselCallChangeLogMapper.class),
				mock(org.apache.rocketmq.spring.core.RocketMQTemplate.class));
	}

	@Test
	@DisplayName("按时间推进：先推靠泊中再推已完成，返回合计条数")
	void refreshCallStatusIssuesTwoAdvances() {
		when(vesselCallMapper.update(isNull(), any(Wrapper.class))).thenReturn(3, 5);

		int changed = service.refreshCallStatus(TENANT);

		assertEquals(8, changed);
		ArgumentCaptor<Wrapper<VesselCall>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(vesselCallMapper, times(2)).update(isNull(), captor.capture());

		// 第一条：计划中(1) → 靠泊中(2)，限定租户（eq/le/gt 参数惰性绑定，渲染后才入 map）
		LambdaUpdateWrapper<VesselCall> first = (LambdaUpdateWrapper<VesselCall>) captor.getAllValues().get(0);
		first.getSqlSegment();
		first.getSqlSet();
		Collection<Object> firstParams = first.getParamNameValuePairs().values();
		assertTrue(firstParams.contains(TENANT));
		assertTrue(firstParams.contains("1"));
		assertTrue(firstParams.contains("2"));

		// 第二条：计划中/靠泊中(1,2) → 已完成(3)
		LambdaUpdateWrapper<VesselCall> second = (LambdaUpdateWrapper<VesselCall>) captor.getAllValues().get(1);
		second.getSqlSegment();
		second.getSqlSet();
		Collection<Object> secondParams = second.getParamNameValuePairs().values();
		assertTrue(secondParams.contains(TENANT));
		assertTrue(secondParams.contains("3"));
	}

	@Test
	@DisplayName("两条推进均未命中时返回 0 且不抛错")
	void refreshCallStatusReturnsZeroWhenNothingToAdvance() {
		when(vesselCallMapper.update(isNull(), any(Wrapper.class))).thenReturn(0, 0);

		assertEquals(0, service.refreshCallStatus(TENANT));
		verify(vesselCallMapper, times(2)).update(isNull(), any(Wrapper.class));
	}

}
