package com.aryn.cloud.vessel.service.impl;

import com.aryn.cloud.order.api.remote.RemoteShoppingCartService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.mapper.VesselCallChangeLogMapper;
import com.aryn.cloud.vessel.mapper.VesselCallMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 靠港申报/排产触发购物车归属顺延的单测。
 *
 * <p>背景（2026-10-04）：「有船无靠港」期间加购的行 vessel_call_id 落空，靠港结束后
 * 新靠港出现时用户期望这批商品顺延到新航次而非删掉重加。申报（含 ±72h 去重复用路径）
 * 与运营排产两条路都要触发迁移；不可用的靠港（已完成/已取消/ETD 已过）不触发，
 * 避免把行挂上不可达的靠港。
 */
class VesselServiceImplTest {

	private static final String TENANT = "tenant-1";
	private static final String VESSEL = "vessel-1";
	private static final String USER = "user-1";

	private VesselInfoMapper vesselInfoMapper;
	private VesselMemberMapper vesselMemberMapper;
	private VesselCallMapper vesselCallMapper;
	private RemoteShoppingCartService remoteShoppingCartService;
	private VesselServiceImpl service;

	@BeforeEach
	void setUp() {
		vesselInfoMapper = mock(VesselInfoMapper.class);
		vesselMemberMapper = mock(VesselMemberMapper.class);
		vesselCallMapper = mock(VesselCallMapper.class);
		remoteShoppingCartService = mock(RemoteShoppingCartService.class);
		service = new VesselServiceImpl(vesselInfoMapper, vesselMemberMapper, vesselCallMapper,
				mock(VesselCallChangeLogMapper.class), mock(RocketMQTemplate.class));
		// @DubboReference 字段刻意不走构造器（双候选会炸 boot 启动），测试里反射注入
		ReflectionTestUtils.setField(service, "remoteShoppingCartService", remoteShoppingCartService);
	}

	@Test
	void declareCallMigratesCartRowsToNewCall() {
		stubMembership();
		// 第一次 selectList 是申报去重查询（无相近靠港），第二次是已结束靠港清单
		when(vesselCallMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of())
			.thenReturn(List.of(vesselCall("call-1", "3", -6, -3)));
		stubInsertAssignsId("call-2");

		VesselCall declared = service.declareCall(TENANT, USER, futureCall(null));

		assertThat(declared.getId()).isEqualTo("call-2");
		verify(remoteShoppingCartService).reattachRowsToVesselCall(TENANT, VESSEL, "call-2", List.of("call-1"));
	}

	@Test
	void declareCallReusePathAlsoMigratesCartRows() {
		stubMembership();
		// ±72h 去重命中既有靠港（上线前申报的）：借这次申报把滞留行顺延过去
		VesselCall existing = vesselCall("call-9", "1", 1, 3);
		when(vesselCallMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(existing))
			.thenReturn(List.of(vesselCall("call-1", "3", -6, -3)));

		VesselCall declared = service.declareCall(TENANT, USER, futureCall(null));

		assertThat(declared.getId()).isEqualTo("call-9");
		verify(remoteShoppingCartService).reattachRowsToVesselCall(TENANT, VESSEL, "call-9", List.of("call-1"));
	}

	@Test
	void saveCallMigratesCartRowsToNewCall() {
		stubVessel();
		when(vesselCallMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(vesselCall("call-1", "3", -6, -3)));
		stubInsertAssignsId("call-2");

		service.saveCall(TENANT, futureCall(null));

		verify(remoteShoppingCartService).reattachRowsToVesselCall(TENANT, VESSEL, "call-2", List.of("call-1"));
	}

	@Test
	void nonOrderableCallSkipsMigration() {
		stubVessel();
		// 运营录入已完成的历史靠港：不可用，不能把购物车行挂上不可达的靠港
		VesselCall past = vesselCall(null, "3", -6, -3);
		stubInsertAssignsId("call-2");

		service.saveCall(TENANT, past);

		verify(vesselCallMapper, never()).selectList(any(Wrapper.class));
		verify(remoteShoppingCartService, never()).reattachRowsToVesselCall(anyString(), anyString(), anyString(), any());
	}

	private void stubMembership() {
		when(vesselMemberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
	}

	private void stubVessel() {
		when(vesselInfoMapper.selectOne(any(Wrapper.class))).thenReturn(new VesselInfo());
	}

	private void stubInsertAssignsId(String id) {
		doAnswer(invocation -> {
			VesselCall call = invocation.getArgument(0);
			call.setId(id);
			return 1;
		}).when(vesselCallMapper).insert(any(VesselCall.class));
	}

	private VesselCall futureCall(String id) {
		return vesselCall(id, "1", 2, 5);
	}

	@Test
	void resolveAvailableCallReturnsEarliestEtaOrderableCall() {
		// SQL 侧按 ETA 升序，mock 以桩的返回顺序模拟：call-2 的 ETA 更早排在前
		when(vesselCallMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(
				vesselCall("call-2", "1", 2, 4),
				vesselCall("call-3", "1", 6, 8)));
		when(vesselInfoMapper.selectOne(any(Wrapper.class))).thenReturn(new VesselInfo());

		VesselContextDTO context = service.resolveAvailableCall(TENANT, VESSEL);

		assertThat(context).isNotNull();
		assertThat(context.getVesselCallId()).isEqualTo("call-2");
	}

	@Test
	void resolveAvailableCallReturnsNullWhenVesselHasNoAvailableCall() {
		when(vesselCallMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		assertThat(service.resolveAvailableCall(TENANT, VESSEL)).isNull();
	}

	/** 组装靠港：状态 + 相对当前时刻的 ETA/ETD（天），负数表示过去 */
	private VesselCall vesselCall(String id, String status, int etaDays, int etdDays) {
		VesselCall call = new VesselCall();
		call.setId(id);
		call.setTenantId(TENANT);
		call.setVesselId(VESSEL);
		call.setPortCode("CNSHA");
		call.setPortName("上海港");
		call.setStatus(status);
		call.setEta(LocalDateTime.now().plusDays(etaDays));
		call.setEtd(LocalDateTime.now().plusDays(etdDays));
		return call;
	}

}
