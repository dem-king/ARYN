package com.aryn.cloud.vessel.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.vessel.api.constant.VesselMemberBindSource;
import com.aryn.cloud.vessel.api.constant.VesselMemberRole;
import com.aryn.cloud.vessel.api.constant.VesselType;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.entity.VesselMemberAuditLog;
import com.aryn.cloud.vessel.mapper.VesselCallMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberAuditLogMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.impl.VesselServiceImpl;
import com.aryn.cloud.vessel.support.VesselMemberRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
	@DisplayName("新增船舶时空白 IMO 归一化为 null：唯一键对 NULL 放行，对空串会互相撞键")
	void saveVesselNormalizesBlankImoToNull() {
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L);
		VesselInfo vessel = new VesselInfo();
		vessel.setVesselName("  新船  ");
		vessel.setImoCode("   ");
		vessel.setCallSign("");
		vessel.setVesselNameEn(" ");
		service.saveVessel(TENANT, vessel);

		// uk_vessel_info_imo(tenant_id, imo_code) 允许重复 NULL、不允许重复 ''：
		// 空串必须落成 null，否则第二条「没填 IMO」的船就会撞唯一键 1062
		assertNull(vessel.getImoCode());
		assertNull(vessel.getCallSign());
		assertNull(vessel.getVesselNameEn());
		assertEquals("新船", vessel.getVesselName());
	}

	@Test
	@DisplayName("修改船舶时同样归一化空白，且不允许把船名改成空白")
	void updateVesselNormalizesAndRejectsBlankName() {
		VesselInfo exists = vessel(VESSEL_ID);
		when(vesselInfoMapper.selectOne(any())).thenReturn(exists);

		VesselInfo rename = new VesselInfo();
		rename.setId(VESSEL_ID);
		rename.setVesselName("  改名后的船  ");
		rename.setImoCode("  ");
		VesselInfo updated = service.updateVessel(TENANT, rename);
		assertEquals("改名后的船", updated.getVesselName());
		assertNull(updated.getImoCode());

		// 只改状态（不动船名）必须放行：不能把「未传」也当成「改成空」
		VesselInfo statusOnly = new VesselInfo();
		statusOnly.setId(VESSEL_ID);
		statusOnly.setStatus("0");
		assertEquals("0", service.updateVessel(TENANT, statusOnly).getStatus());

		// 显式传空白船名则拒绝（NOT NULL 列被写成空串后，列表与下拉全变空白）
		VesselInfo blankName = new VesselInfo();
		blankName.setId(VESSEL_ID);
		blankName.setVesselName("   ");
		assertThrows(ArynBusinessException.class, () -> service.updateVessel(TENANT, blankName));
	}

	@Test
	@DisplayName("船型缺省落「其他」，非法码值拒绝（DTO 旧口径会传出错值）")
	void saveVesselNormalizesVesselType() {
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L);

		VesselInfo defaulted = new VesselInfo();
		defaulted.setVesselName("没选船型");
		assertEquals(VesselType.DEFAULT, service.saveVessel(TENANT, defaulted).getVesselType());

		VesselInfo explicit = new VesselInfo();
		explicit.setVesselName("油轮");
		explicit.setVesselType(VesselType.TANKER);
		assertEquals(VesselType.TANKER, service.saveVessel(TENANT, explicit).getVesselType());

		VesselInfo invalid = new VesselInfo();
		invalid.setVesselName("错船型");
		invalid.setVesselType("9");
		assertThrows(ArynBusinessException.class, () -> service.saveVessel(TENANT, invalid));
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
	@DisplayName("海员申报靠港：标记来源为海员申报并记录申报人，且不写入配送时间窗")
	void declareCallMarksCrewSourceAndSkipsDeliveryWindow() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		when(vesselCallMapper.selectList(any())).thenReturn(List.of());

		LocalDateTime eta = LocalDateTime.of(2026, 9, 20, 8, 0);
		LocalDateTime etd = LocalDateTime.of(2026, 9, 20, 20, 0);
		VesselCall declared = call(null, "1", eta, etd);
		// 配送时间窗属于公司排产决策，海员即使传了也不应落库
		declared.setDeliveryWindowStart(eta.plusHours(1));
		declared.setDeliveryWindowEnd(eta.plusHours(3));

		VesselCall saved = service.declareCall(TENANT, USER, declared);

		assertEquals(VesselCall.SOURCE_CREW, saved.getSource());
		assertEquals(USER, saved.getDeclaredBy());
		assertEquals(null, saved.getDeliveryWindowStart());
		assertEquals(null, saved.getDeliveryWindowEnd());
	}

	@Test
	@DisplayName("海员申报靠港：同船 ETA 相近时复用已有计划，不重复建")
	void declareCallReusesNearbyCall() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);

		LocalDateTime eta = LocalDateTime.of(2026, 9, 20, 8, 0);
		VesselCall existing = call("call-existing", "1", eta, eta.plusHours(12));
		when(vesselCallMapper.selectList(any())).thenReturn(List.of(existing));

		// ±72 小时内视为同一航次
		VesselCall declared = call(null, "1", eta.plusHours(48), eta.plusHours(60));
		VesselCall saved = service.declareCall(TENANT, USER, declared);

		assertEquals("call-existing", saved.getId());
	}

	@Test
	@DisplayName("海员申报靠港：非本船成员被拒绝")
	void declareCallRejectsNonMember() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		LocalDateTime eta = LocalDateTime.of(2026, 9, 20, 8, 0);
		VesselCall declared = call(null, "1", eta, eta.plusHours(12));
		assertThrows(ArynBusinessException.class, () -> service.declareCall(TENANT, USER, declared));
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
	@DisplayName("展示快照对已结束的靠港照样返回船名与港口，仅以 callOrderable 标记失效")
	void snapshotKeepsNameForFinishedCall() {
		// 历史单据（已提交/已关闭的共享购物车）绑定的靠港必然已经结束。
		// 「能不能下单」与「这班船当时去哪」是两回事：前者返回 null，后者必须有值，
		// 否则历史单卡片会永久退化成「船舶信息加载中」占位文案。
		LocalDateTime now = LocalDateTime.now();
		VesselCall finished = call("call-old", "3", now.minusDays(3), now.minusDays(2));
		when(vesselCallMapper.selectOne(any())).thenReturn(finished);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));

		VesselContextDTO snapshot = service.snapshotByCallId(TENANT, "call-old");

		assertNotNull(snapshot);
		assertEquals("测试轮", snapshot.getVesselName());
		assertEquals("上海港", snapshot.getPortName());
		assertEquals(Boolean.FALSE, snapshot.getCallOrderable());
		// 可下单查询对同一靠港仍返回 null：两条路径的语义必须分开
		assertNull(service.contextByCallId(TENANT, "call-old"));
	}

	@Test
	@DisplayName("仍可下单的靠港：快照与上下文都取得到，标记为 true")
	void snapshotMarksOrderableCall() {
		LocalDateTime now = LocalDateTime.now();
		VesselCall active = call("call-live", "1", now.plusHours(2), now.plusHours(20));
		when(vesselCallMapper.selectOne(any())).thenReturn(active);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));

		VesselContextDTO snapshot = service.snapshotByCallId(TENANT, "call-live");

		assertEquals(Boolean.TRUE, snapshot.getCallOrderable());
		assertEquals("call-live", service.contextByCallId(TENANT, "call-live").getVesselCallId());
	}

	@Test
	@DisplayName("靠港记录不存在或ID为空时快照返回 null，不抛异常")
	void snapshotReturnsNullForMissingCall() {
		when(vesselCallMapper.selectOne(any())).thenReturn(null);
		assertNull(service.snapshotByCallId(TENANT, "call-missing"));
		assertNull(service.snapshotByCallId(TENANT, " "));
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

	// ---------------------------------------------------------------------
	// 管理端绑定成员：在营校验 + 角色白名单 + 来源标记
	// ---------------------------------------------------------------------

	/** VesselServiceImpl 的 registrar 是字段注入（历史原因），单测需反射塞入 */
	private VesselMemberRegistrar injectRegistrar(VesselMemberAuditLogMapper auditLogMapper) {
		VesselMemberRegistrar registrar = new VesselMemberRegistrar(vesselMemberMapper, auditLogMapper);
		ReflectionTestUtils.setField(service, "memberRegistrar", registrar);
		return registrar;
	}

	@Test
	@DisplayName("停用船不能添加成员——停用船无法下单，却会让人看到成员与靠港数据")
	void addMemberRejectsInactiveVessel() {
		VesselInfo disabled = vessel(VESSEL_ID);
		disabled.setStatus("0");
		when(vesselInfoMapper.selectOne(any())).thenReturn(disabled);
		VesselMember member = new VesselMember();
		member.setVesselId(VESSEL_ID);
		member.setUserId(USER);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class, () -> service.addMember(TENANT, member));
		assertTrue(ex.getMsg().contains("停用"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("管理端绑定成员时角色传「1」（发起人）→ 拒绝")
	void addMemberRejectsOwnerRole() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		VesselMember member = new VesselMember();
		member.setVesselId(VESSEL_ID);
		member.setUserId(USER);
		member.setMemberRole(VesselMember.ROLE_OWNER);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class, () -> service.addMember(TENANT, member));
		assertTrue(ex.getMsg().contains("成员角色不合法"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("管理端直接绑定来源记 ADMIN_ADD，分享链接补建记 SHARE_CART——两条路径不能混")
	void addMemberMarksBindSource() {
		VesselMemberAuditLogMapper auditLogMapper = mock(VesselMemberAuditLogMapper.class);
		injectRegistrar(auditLogMapper);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselMemberMapper.selectOne(any())).thenReturn(null);

		VesselMember adminMember = new VesselMember();
		adminMember.setVesselId(VESSEL_ID);
		adminMember.setUserId(USER);
		adminMember.setMemberRole(VesselMemberRole.CREW);
		service.addMember(TENANT, adminMember);

		ArgumentCaptor<VesselMember> adminCaptor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(adminCaptor.capture());
		assertEquals(VesselMemberBindSource.ADMIN_ADD, adminCaptor.getValue().getBindSource());

		VesselMember shareMember = new VesselMember();
		shareMember.setVesselId(VESSEL_ID);
		shareMember.setUserId("share-user");
		shareMember.setMemberRole(VesselMemberRole.CREW);
		service.addMember(TENANT, shareMember, VesselMemberBindSource.SHARE_CART);

		ArgumentCaptor<VesselMember> shareCaptor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper, times(2)).insert(shareCaptor.capture());
		assertEquals(VesselMemberBindSource.SHARE_CART, shareCaptor.getAllValues().get(1).getBindSource());
		// 每次绑定都必须留 BIND 审计：没有审计的绑定在误绑事故里等于没发生过
		verify(auditLogMapper, times(2)).insert(any(VesselMemberAuditLog.class));
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