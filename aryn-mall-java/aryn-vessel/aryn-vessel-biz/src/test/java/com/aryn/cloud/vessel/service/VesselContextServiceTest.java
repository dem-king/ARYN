package com.aryn.cloud.vessel.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.mapper.VesselCallMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.impl.VesselServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 船舶上下文服务契约测试。
 */
class VesselContextServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private static final String VESSEL_ID = "vessel-1";

	private static final String OTHER_VESSEL_ID = "vessel-other";

	private VesselInfoMapper vesselInfoMapper;

	private VesselMemberMapper vesselMemberMapper;

	private VesselCallMapper vesselCallMapper;

	private com.aryn.cloud.vessel.mapper.VesselCallChangeLogMapper changeLogMapper;

	private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

	private VesselServiceImpl service;

	@BeforeEach
	void setUp() {
		vesselInfoMapper = mock(VesselInfoMapper.class);
		vesselMemberMapper = mock(VesselMemberMapper.class);
		vesselCallMapper = mock(VesselCallMapper.class);
		changeLogMapper = mock(com.aryn.cloud.vessel.mapper.VesselCallChangeLogMapper.class);
		rocketMQTemplate = mock(org.apache.rocketmq.spring.core.RocketMQTemplate.class);
		service = new VesselServiceImpl(vesselInfoMapper, vesselMemberMapper, vesselCallMapper, changeLogMapper,
				rocketMQTemplate);
	}

	private VesselInfo vessel(String id) {
		VesselInfo vessel = new VesselInfo();
		vessel.setId(id);
		vessel.setTenantId(TENANT);
		vessel.setVesselName("测试轮");
		vessel.setStatus("1");
		return vessel;
	}

	private VesselCall call(String id, String status, LocalDateTime eta, LocalDateTime etd) {
		VesselCall call = new VesselCall();
		call.setId(id);
		call.setTenantId(TENANT);
		call.setVesselId(VESSEL_ID);
		call.setPortCode("CNSHA");
		call.setPortName("上海港");
		call.setStatus(status);
		call.setEta(eta);
		call.setEtd(etd);
		return call;
	}

	@Test
	@DisplayName("无船舶成员关系的用户拿不到船舶列表")
	void myVesselsEmptyWithoutMembership() {
		when(vesselMemberMapper.selectList(any())).thenReturn(List.of());
		assertTrue(service.myVessels(TENANT, USER).isEmpty());
	}

	@Test
	@DisplayName("用户只能读取自己作为成员的船舶")
	void myVesselsReturnOnlyMemberedVessels() {
		VesselMember membership = new VesselMember();
		membership.setTenantId(TENANT);
		membership.setVesselId(VESSEL_ID);
		membership.setUserId(USER);
		membership.setStatus("1");
		when(vesselMemberMapper.selectList(any())).thenReturn(List.of(membership));
		when(vesselInfoMapper.selectList(any())).thenReturn(List.of(vessel(VESSEL_ID)));

		List<VesselInfo> vessels = service.myVessels(TENANT, USER);
		assertEquals(1, vessels.size());
		assertEquals(VESSEL_ID, vessels.get(0).getId());
	}

	@Test
	@DisplayName("非船舶成员访问上下文被拒绝，不能读取其他船舶数据")
	void currentContextRejectedWithoutMembership() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		assertThrows(ArynBusinessException.class, () -> service.currentContext(TENANT, USER, VESSEL_ID));
	}

	@Test
	@DisplayName("成员关系指向已删除船舶时静默过滤，不抛异常也不读其他租户数据")
	void staleMembershipFilteredSilently() {
		when(vesselInfoMapper.selectList(any())).thenReturn(List.of());
		VesselMember membership = new VesselMember();
		membership.setVesselId(VESSEL_ID);
		membership.setUserId(USER);
		membership.setStatus("1");
		when(vesselMemberMapper.selectList(any())).thenReturn(List.of(membership));
		assertTrue(service.myVessels(TENANT, USER).isEmpty());
	}

	@Test
	@DisplayName("管理端可以新增船舶，未指定状态默认在营")
	void saveVesselDefaultsActive() {
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L);
		VesselInfo vessel = new VesselInfo();
		vessel.setVesselName("新船");
		VesselInfo saved = service.saveVessel(TENANT, vessel);
		assertEquals("1", saved.getStatus());
		assertEquals(TENANT, saved.getTenantId());
	}

	@Test
	@DisplayName("同一租户下 IMO 编号不能重复")
	void saveVesselRejectsDuplicateImo() {
		when(vesselInfoMapper.selectCount(any())).thenReturn(1L);
		VesselInfo vessel = new VesselInfo();
		vessel.setVesselName("新船");
		vessel.setImoCode("IMO1234567");
		assertThrows(ArynBusinessException.class, () -> service.saveVessel(TENANT, vessel));
	}

	@Test
	@DisplayName("管理端可以修改和停用船舶")
	void updateVesselSupportsDisable() {
		VesselInfo exists = vessel(VESSEL_ID);
		when(vesselInfoMapper.selectOne(any())).thenReturn(exists);
		VesselInfo change = new VesselInfo();
		change.setId(VESSEL_ID);
		change.setStatus("0");
		VesselInfo updated = service.updateVessel(TENANT, change);
		assertEquals("0", updated.getStatus());
	}

	@Test
	@DisplayName("靠港计划 ETA 必须早于 ETD")
	void saveCallRejectsInvalidEtaEtd() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));
		LocalDateTime now = LocalDateTime.of(2026, 9, 12, 8, 0);
		VesselCall invalid = call(null, "1", now.plusHours(4), now);
		assertThrows(ArynBusinessException.class, () -> service.saveCall(TENANT, invalid));
	}

	@Test
	@DisplayName("配送时间窗必须落在 ETA 与 ETD 之间")
	void saveCallRejectsWindowOutsideEtaEtd() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));
		LocalDateTime eta = LocalDateTime.of(2026, 9, 12, 8, 0);
		LocalDateTime etd = LocalDateTime.of(2026, 9, 12, 20, 0);
		VesselCall invalid = call(null, "1", eta, etd);
		invalid.setDeliveryWindowStart(eta.minusHours(2));
		invalid.setDeliveryWindowEnd(eta.plusHours(1));
		assertThrows(ArynBusinessException.class, () -> service.saveCall(TENANT, invalid));

		VesselCall valid = call(null, "1", eta, etd);
		valid.setDeliveryWindowStart(eta.plusHours(1));
		valid.setDeliveryWindowEnd(eta.plusHours(3));
		VesselCall saved = service.saveCall(TENANT, valid);
		assertNotNull(saved);
	}

	@Test
	@DisplayName("已离港、已完成和已取消的靠港计划不能作为新订单配送计划")
	void expiredCallsNotOrderable() {
		LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
		assertFalse(VesselServiceImpl.isOrderableCall(
				call("c1", "1", now.minusHours(10), now.minusHours(1)), now), "已离港（ETD 已过）不可用");
		assertFalse(VesselServiceImpl.isOrderableCall(
				call("c2", "3", now.minusHours(2), now.plusHours(2)), now), "已完成不可用");
		assertFalse(VesselServiceImpl.isOrderableCall(
				call("c3", "4", now.minusHours(1), now.plusHours(5)), now), "已取消不可用");
		assertTrue(VesselServiceImpl.isOrderableCall(
				call("c4", "1", now.minusHours(1), now.plusHours(5)), now), "靠泊中可用");
		assertTrue(VesselServiceImpl.isOrderableCall(
				call("c5", "2", now.minusHours(1), now.plusHours(5)), now), "计划中可用");
	}

	@Test
	@DisplayName("成员可以获取下一靠港上下文")
	void currentContextReturnsNextCall() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));
		LocalDateTime eta = LocalDateTime.now().plusDays(1);
		LocalDateTime etd = eta.plusHours(12);
		VesselCall next = call("call-1", "1", eta, etd);
		next.setBerth("3号泊位");
		when(vesselCallMapper.selectList(any())).thenReturn(List.of(next));

		VesselContextDTO context = service.currentContext(TENANT, USER, VESSEL_ID);
		assertEquals("call-1", context.getVesselCallId());
		assertEquals("测试轮", context.getVesselName());
		assertEquals("3号泊位", context.getBerth());
	}

	@Test
	@DisplayName("重复绑定成员被拒绝")
	void addMemberRejectsDuplicate() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		VesselMember member = new VesselMember();
		member.setVesselId(VESSEL_ID);
		member.setUserId(USER);
		assertThrows(ArynBusinessException.class, () -> service.addMember(TENANT, member));
	}

	@Test
	@DisplayName("仅计划中的靠港允许修改")
	void updateCallOnlyForPlanned() {
		when(vesselCallMapper.selectOne(any())).thenReturn(
				call("call-1", "3", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusHours(1)));
		VesselCall change = new VesselCall();
		change.setId("call-1");
		assertThrows(ArynBusinessException.class, () -> service.updateCall(TENANT, change));
	}

	@Test
	@DisplayName("其他租户的船舶上下文不可读取：服务层显式携带租户条件")
	void tenantScopedMembershipRequired() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		assertThrows(ArynBusinessException.class, () -> service.currentContext("tenant-2", USER, OTHER_VESSEL_ID));
	}


	@Test
	@DisplayName("修改 ETA/泊位/时间窗写入变更日志并发布提醒事件")
	void updateCallLogsAndPublishesChange() {
		when(vesselCallMapper.selectOne(any())).thenReturn(
				call("call-1", "1", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(12)));

		VesselCall change = new VesselCall();
		change.setId("call-1");
		change.setEta(LocalDateTime.now().plusDays(2));
		change.setEtd(LocalDateTime.now().plusDays(2).plusHours(12));
		change.setOperatorId("op-1");
		change.setOperatorName("调度员");

		service.updateCall(TENANT, change);

		org.mockito.ArgumentCaptor<com.aryn.cloud.vessel.api.entity.VesselCallChangeLog> logCaptor =
				org.mockito.ArgumentCaptor.forClass(com.aryn.cloud.vessel.api.entity.VesselCallChangeLog.class);
		org.mockito.Mockito.verify(changeLogMapper).insert(logCaptor.capture());
		assertNotNull(logCaptor.getValue().getNewEta());
		org.mockito.ArgumentCaptor<String> topicCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.ArgumentCaptor<com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice> noticeCaptor =
				org.mockito.ArgumentCaptor.forClass(com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice.class);
		org.mockito.Mockito.verify(rocketMQTemplate).convertAndSend(topicCaptor.capture(), noticeCaptor.capture());
		assertEquals(com.aryn.cloud.common.core.constant.RocketMqConstants.VESSEL_CALL_CHANGED_TOPIC,
				topicCaptor.getValue());
	}

	@Test
	@DisplayName("无实质变化的修改不触发变更日志与提醒")
	void updateCallWithoutChangeSkipsNotify() {
		LocalDateTime eta = LocalDateTime.now().plusDays(1);
		LocalDateTime etd = eta.plusHours(12);
		when(vesselCallMapper.selectOne(any())).thenReturn(call("call-1", "1", eta, etd));

		VesselCall change = new VesselCall();
		change.setId("call-1");
		change.setEta(eta);
		change.setEtd(etd);

		service.updateCall(TENANT, change);

		org.mockito.Mockito.verify(changeLogMapper, org.mockito.Mockito.never())
				.insert(any(com.aryn.cloud.vessel.api.entity.VesselCallChangeLog.class));
		org.mockito.Mockito.verify(rocketMQTemplate, org.mockito.Mockito.never())
				.convertAndSend(org.mockito.ArgumentMatchers.<String>any(),
						org.mockito.ArgumentMatchers.<com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice>any());
	}

	@Test
	@DisplayName("事件发布失败不影响变更主流程（fail-open）")
	void publishFailureDoesNotBreakUpdate() {
		when(vesselCallMapper.selectOne(any())).thenReturn(
				call("call-1", "1", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(12)));
		org.mockito.Mockito.doThrow(new RuntimeException("mq down")).when(rocketMQTemplate)
				.convertAndSend(org.mockito.ArgumentMatchers.anyString(),
						org.mockito.ArgumentMatchers.any(com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice.class));

		VesselCall change = new VesselCall();
		change.setId("call-1");
		change.setEta(LocalDateTime.now().plusDays(3));
		change.setEtd(LocalDateTime.now().plusDays(3).plusHours(12));

		VesselCall updated = service.updateCall(TENANT, change);
		assertNotNull(updated);
		org.mockito.Mockito.verify(changeLogMapper).insert(any(com.aryn.cloud.vessel.api.entity.VesselCallChangeLog.class));
	}

}