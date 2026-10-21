package com.aryn.cloud.vessel.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.constant.VesselMemberBindSource;
import com.aryn.cloud.vessel.api.constant.VesselMemberRole;
import com.aryn.cloud.vessel.api.constant.VesselType;
import com.aryn.cloud.vessel.api.dto.VesselBindApplyDTO;
import com.aryn.cloud.vessel.api.dto.VesselBindAuditDTO;
import com.aryn.cloud.vessel.api.dto.VesselMemberAddDTO;
import com.aryn.cloud.vessel.api.entity.VesselBindApply;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselInviteCode;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.entity.VesselMemberAuditLog;
import com.aryn.cloud.vessel.api.vo.VesselBindApplyVO;
import com.aryn.cloud.vessel.api.vo.VesselBindPrecheckVO;
import com.aryn.cloud.vessel.api.vo.VesselBindVesselOptionVO;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.aryn.cloud.vessel.mapper.VesselBindApplyMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselInviteCodeMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberAuditLogMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.impl.VesselBindServiceImpl;
import com.aryn.cloud.vessel.support.VesselMemberRegistrar;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
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
 * 船舶自助绑定服务契约测试。
 *
 * <p>覆盖《船舶自助绑定-实施进度.md》点名的风险点：权限收敛、审核建船、
 * 邀请码限次与过期、重复绑定拦截、手机号脱敏。
 */
class VesselBindServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String CREW = "crew-user";

	private static final String SALES = "sales-user";

	private static final String VESSEL_ID = "vessel-1";

	private VesselMemberMapper vesselMemberMapper;

	private VesselBindApplyMapper vesselBindApplyMapper;

	private VesselInviteCodeMapper vesselInviteCodeMapper;

	private VesselInfoMapper vesselInfoMapper;

	private VesselMemberAuditLogMapper vesselMemberAuditLogMapper;

	private RemoteMallUserService remoteMallUserService;

	private com.aryn.cloud.upms.api.remote.RemoteMessageStaffService remoteMessageStaffService;

	private RocketMQTemplate rocketMQTemplate;

	private VesselBindServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		vesselMemberMapper = mock(VesselMemberMapper.class);
		vesselBindApplyMapper = mock(VesselBindApplyMapper.class);
		vesselInviteCodeMapper = mock(VesselInviteCodeMapper.class);
		vesselInfoMapper = mock(VesselInfoMapper.class);
		vesselMemberAuditLogMapper = mock(VesselMemberAuditLogMapper.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		remoteMessageStaffService = mock(com.aryn.cloud.upms.api.remote.RemoteMessageStaffService.class);
		rocketMQTemplate = mock(RocketMQTemplate.class);
		service = new VesselBindServiceImpl(vesselMemberMapper, vesselBindApplyMapper, vesselInviteCodeMapper,
				vesselInfoMapper, new VesselMemberRegistrar(vesselMemberMapper, vesselMemberAuditLogMapper),
				vesselMemberAuditLogMapper, rocketMQTemplate, new ObjectMapper());
		// @DubboReference 字段在单测中无容器注入，反射塞入 mock
		Field field = VesselBindServiceImpl.class.getDeclaredField("remoteMallUserService");
		field.setAccessible(true);
		field.set(service, remoteMallUserService);
		Field staffField = VesselBindServiceImpl.class.getDeclaredField("remoteMessageStaffService");
		staffField.setAccessible(true);
		staffField.set(service, remoteMessageStaffService);
	}

	/** Registrar 单测用：只关心落库语义，审计 mapper 用 mock 即可 */
	private VesselMemberRegistrar registrar() {
		return new VesselMemberRegistrar(vesselMemberMapper, vesselMemberAuditLogMapper);
	}

	// ---------------------------------------------------------------------
	// 夹具
	// ---------------------------------------------------------------------

	private VesselInfo activeVessel() {
		VesselInfo vessel = new VesselInfo();
		vessel.setId(VESSEL_ID);
		vessel.setTenantId(TENANT);
		vessel.setVesselName("悦航1号");
		vessel.setStatus("1");
		return vessel;
	}

	private VesselMember member(String userId, String role) {
		VesselMember member = new VesselMember();
		member.setId("m-" + userId);
		member.setTenantId(TENANT);
		member.setVesselId(VESSEL_ID);
		member.setUserId(userId);
		member.setMemberRole(role);
		member.setStatus(VesselMember.STATUS_ONBOARD);
		return member;
	}

	private UserInfoVO user(String id, String nickname, String phone) {
		UserInfoVO vo = new UserInfoVO();
		vo.setId(id);
		vo.setNickname(nickname);
		vo.setPhone(phone);
		return vo;
	}

	private VesselInviteCode invite(String code, int maxUses, int usedCount, LocalDateTime expiresAt,
			String status) {
		VesselInviteCode entity = new VesselInviteCode();
		entity.setId("invite-1");
		entity.setTenantId(TENANT);
		entity.setVesselId(VESSEL_ID);
		entity.setCode(code);
		entity.setOwnerUserId(SALES);
		entity.setMaxUses(maxUses);
		entity.setUsedCount(usedCount);
		entity.setExpiresAt(expiresAt);
		entity.setStatus(status);
		return entity;
	}

	// ---------------------------------------------------------------------
	// 权限收敛
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("普通船员不能添加成员——成员管理权限收敛到发起人/确认人/业务员")
	void crewCannotAddMember() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(CREW, VesselMember.ROLE_CREW));
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId("other");

		assertThrows(ArynBusinessException.class,
				() -> service.addMember(TENANT, CREW, VESSEL_ID, dto));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("非成员不能读取成员列表")
	void nonMemberCannotListMembers() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		assertThrows(ArynBusinessException.class, () -> service.listMembers(TENANT, "stranger", VESSEL_ID));
	}

	@Test
	@DisplayName("非成员不能生成邀请码")
	void nonMemberCannotGenerateInviteCode() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(null);
		assertThrows(ArynBusinessException.class,
				() -> service.generateInviteCode(TENANT, "stranger", VESSEL_ID, 1, 24, null));
	}

	@Test
	@DisplayName("业务员（角色4）可以添加成员——地推现场拉人是主路径")
	void salesCanAddMember() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of(user(CREW, "船员甲", "13800001234")));
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(CREW);
		dto.setMemberRole(VesselMember.ROLE_CREW);

		VesselMemberVO vo = service.addMember(TENANT, SALES, VESSEL_ID, dto);

		assertEquals(CREW, vo.getUserId());
		assertEquals(VesselMember.ROLE_CREW, vo.getMemberRole());
		verify(vesselMemberMapper).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("添加成员时手机号脱敏，不返回完整号码")
	void addMemberMasksPhone() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of(user(CREW, "船员甲", "13800001234")));
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(CREW);

		VesselMemberVO vo = service.addMember(TENANT, SALES, VESSEL_ID, dto);

		assertEquals("138****1234", vo.getPhone());
	}

	@Test
	@DisplayName("用户域不可用时降级为只返回用户ID，不阻断成员管理主流程")
	void addMemberDegradesWhenUserDomainDown() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(remoteMallUserService.getUserByIds(any())).thenThrow(new RuntimeException("dubbo down"));
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(CREW);

		VesselMemberVO vo = service.addMember(TENANT, SALES, VESSEL_ID, dto);

		assertEquals(CREW, vo.getUserId());
		assertEquals(null, vo.getNickname());
	}

	@Test
	@DisplayName("不能移除自己，也不能移除发起人")
	void removeMemberGuardsSelfAndOwner() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));

		VesselMember self = member(SALES, VesselMember.ROLE_SALES);
		when(vesselMemberMapper.selectById("m-" + SALES)).thenReturn(self);
		assertThrows(ArynBusinessException.class,
				() -> service.removeMember(TENANT, SALES, VESSEL_ID, "m-" + SALES));

		VesselMember owner = member("owner-user", VesselMember.ROLE_OWNER);
		when(vesselMemberMapper.selectById("m-owner-user")).thenReturn(owner);
		assertThrows(ArynBusinessException.class,
				() -> service.removeMember(TENANT, SALES, VESSEL_ID, "m-owner-user"));
		verify(vesselMemberMapper, never()).deleteById(any(String.class));
	}

	// ---------------------------------------------------------------------
	// 退出船舶
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("普通成员可自助退出船舶，退出后本人有效邀请码一并作废")
	void crewCanLeaveShip() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(CREW, VesselMember.ROLE_CREW));

		service.leaveShip(TENANT, CREW, VESSEL_ID);

		verify(vesselMemberMapper).deleteById("m-" + CREW);
		verify(vesselInviteCodeMapper).delete(any());
	}

	@Test
	@DisplayName("发起人不允许自助退出——退出后该船将没有管理角色")
	void ownerCannotLeaveShip() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member("owner-user", VesselMember.ROLE_OWNER));

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.leaveShip(TENANT, "owner-user", VESSEL_ID));
		assertTrue(ex.getMsg().contains("发起人"));
		verify(vesselMemberMapper, never()).deleteById(any(String.class));
		verify(vesselInviteCodeMapper, never()).delete(any());
	}

	@Test
	@DisplayName("非成员不能调用退出接口")
	void nonMemberCannotLeaveShip() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(null);

		assertThrows(ArynBusinessException.class, () -> service.leaveShip(TENANT, "stranger", VESSEL_ID));
		verify(vesselMemberMapper, never()).deleteById(any(String.class));
	}

	// ---------------------------------------------------------------------
	// 申请
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("已在船上的用户也可以提交申请——加入其他船或认领新船，由运营审核把关")
	void submitApplyAllowedWhenAlreadyMember() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航3号");

		service.submitApply(TENANT, CREW, dto);

		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).insert(captor.capture());
		assertEquals(VesselBindApply.STATUS_PENDING, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("已有待审核申请时不允许重复提交")
	void submitApplyRejectedWhenPendingExists() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(1L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航1号");

		assertThrows(ArynBusinessException.class, () -> service.submitApply(TENANT, CREW, dto));
	}

	@Test
	@DisplayName("申请角色只允许普通船员(2)与业务员(4)")
	void submitApplyRejectsUnknownRole() {
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航1号");
		dto.setApplyRole("9");

		assertThrows(ArynBusinessException.class, () -> service.submitApply(TENANT, CREW, dto));
	}

	@Test
	@DisplayName("业务员认领船舶：applyRole=4 的申请被原样落库")
	void submitApplySupportsSalesClaim() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航2号");
		dto.setApplyRole(VesselBindApply.ROLE_SALES);

		service.submitApply(TENANT, SALES, dto);

		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).insert(captor.capture());
		assertEquals(VesselBindApply.ROLE_SALES, captor.getValue().getApplyRole());
		assertEquals(VesselBindApply.STATUS_PENDING, captor.getValue().getStatus());
		assertEquals(TENANT, captor.getValue().getTenantId());
	}

	@Test
	@DisplayName("提交申请后按审核权限通知运营，文案带船名/申请人/查看入口")
	void submitApplyNotifiesReviewers() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		when(remoteMessageStaffService.queryRecipientsByPermission(TENANT,
				List.of("vessel:bindapply:audit")))
			.thenReturn(List.of(staffRecipient("ops-1", "王运营"), staffRecipient("ops-2", "李运营")));
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航7号");
		dto.setApplyVesselImo("321123");
		dto.setApplyPortName("福建港");
		dto.setRealName("刘二蛋");
		dto.setPhone("18899997777");

		service.submitApply(TENANT, CREW, dto);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate, times(2)).convertAndSend(any(String.class), captor.capture());
		List<MessageSendCommand> commands = captor.getAllValues().stream()
			.map(payload -> (MessageSendCommand) payload)
			.toList();
		assertEquals(List.of("ops-1", "ops-2"),
				commands.stream().map(MessageSendCommand::getRecipientId).toList());
		assertEquals(List.of("王运营", "李运营"),
				commands.stream().map(MessageSendCommand::getRecipientName).toList());
		MessageSendCommand first = commands.get(0);
		assertEquals("SYS_USER", first.getRecipientType());
		assertEquals("VESSEL_BIND_TODO", first.getCategory());
		assertEquals("VESSEL_BIND_APPLY_TODO", first.getBizType());
		assertEquals(TENANT, first.getTenantId());
		assertEquals("有新的船员绑定申请", first.getTitle());
		// 运营要靠这些字段判断该不该通过，缺一个就得回列表里翻
		assertTrue(first.getContent().contains("刘二蛋"));
		assertTrue(first.getContent().contains("悦航7号"));
		assertTrue(first.getContent().contains("321123"));
		assertTrue(first.getContent().contains("福建港"));
		assertTrue(first.getContent().contains("18899997777"));
		// 跳转必须指向管理端绑定申请列表，且是 JSON 对象（裸串会被消费端校验拒绝）
		assertTrue(first.getJumpPayload().contains("/vessel/bind-apply"));
		assertTrue(first.getJumpPayload().startsWith("{"));
	}

	@Test
	@DisplayName("业务员认领船舶的通知标题与船员申请区分开")
	void salesClaimNotifiesWithDistinctTitle() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		when(remoteMessageStaffService.queryRecipientsByPermission(TENANT,
				List.of("vessel:bindapply:audit")))
			.thenReturn(List.of(staffRecipient("ops-1", "王运营")));
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航2号");
		dto.setApplyRole(VesselBindApply.ROLE_SALES);

		service.submitApply(TENANT, SALES, dto);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate).convertAndSend(any(String.class), captor.capture());
		MessageSendCommand command = (MessageSendCommand) captor.getValue();
		assertEquals("有新的业务员认领船舶申请", command.getTitle());
		assertTrue(command.getContent().contains("商城业务员"));
	}

	@Test
	@DisplayName("审核权限尚未授出时回落租户管理员，别让申请静默")
	void submitApplyFallsBackToTenantAdminWhenNoAuditHolder() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		when(remoteMessageStaffService.queryRecipientsByPermission(any(), any())).thenReturn(List.of());
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT, "ROLE_ADMIN"))
			.thenReturn(List.of(staffRecipient("admin-1", "租户管理员")));
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航5号");
		dto.setRealName("张三");

		service.submitApply(TENANT, CREW, dto);

		// 新租户还没跑菜单授权脚本时，审核权限无人持有；若直接跳过，
		// 船员提交申请后运营依旧完全不知道——这里必须有兜底触达
		verify(remoteMessageStaffService).queryRecipientsByRoleCode(TENANT, "ROLE_ADMIN");
		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate).convertAndSend(any(String.class), captor.capture());
		MessageSendCommand command = (MessageSendCommand) captor.getValue();
		assertEquals("admin-1", command.getRecipientId());
		assertEquals("SYS_USER", command.getRecipientType());
		assertEquals("VESSEL_BIND_APPLY_TODO", command.getBizType());
		assertEquals("有新的船员绑定申请", command.getTitle());
	}

	@Test
	@DisplayName("既无审核权限人、也无管理员时跳过通知，申请照常落库")
	void submitApplySkipsNotificationWhenNoReviewer() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		when(remoteMessageStaffService.queryRecipientsByPermission(any(), any())).thenReturn(List.of());
		when(remoteMessageStaffService.queryRecipientsByRoleCode(any(), any())).thenReturn(List.of());
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航3号");

		service.submitApply(TENANT, CREW, dto);

		verify(vesselBindApplyMapper).insert(any(VesselBindApply.class));
		verify(rocketMQTemplate, never()).convertAndSend(any(String.class), any(Object.class));
	}

	@Test
	@DisplayName("审核人查询失败不影响申请提交")
	void submitApplySurvivesReviewerLookupFailure() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		when(remoteMessageStaffService.queryRecipientsByPermission(any(), any()))
			.thenThrow(new RuntimeException("upms down"));
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航4号");

		service.submitApply(TENANT, CREW, dto);

		verify(vesselBindApplyMapper).insert(any(VesselBindApply.class));
		verify(rocketMQTemplate, never()).convertAndSend(any(String.class), any(Object.class));
	}

	private com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO staffRecipient(String id, String nickname) {
		com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO recipient
			= new com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO();
		recipient.setId(id);
		recipient.setNickname(nickname);
		return recipient;
	}

	@Test
	@DisplayName("只能撤回自己的待审核申请")
	void cancelApplyOnlyOwnPending() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId("someone-else");
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);

		assertThrows(ArynBusinessException.class, () -> service.cancelApply(TENANT, CREW, "apply-1"));
	}

	// ---------------------------------------------------------------------
	// 审核
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("审核通过且船未录入系统时先建船再绑定——用户比运营更早接触船的场景")
	void approveCreatesVesselWhenAbsent() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyRole(VesselBindApply.ROLE_SALES);
		apply.setApplyVesselName("悦航新船");
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName("悦航新船");
		audit.setNewVesselImo("9800099");
		audit.setNewVesselCallSign("A3K9MP");
		audit.setMemberRole(VesselMember.ROLE_SALES);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselInfo> vesselCaptor = ArgumentCaptor.forClass(VesselInfo.class);
		verify(vesselInfoMapper).insert(vesselCaptor.capture());
		assertEquals("悦航新船", vesselCaptor.getValue().getVesselName());
		assertEquals("9800099", vesselCaptor.getValue().getImoCode());
		assertEquals("A3K9MP", vesselCaptor.getValue().getCallSign());
		assertEquals("1", vesselCaptor.getValue().getStatus());

		ArgumentCaptor<VesselMember> memberCaptor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(memberCaptor.capture());
		assertEquals(VesselMember.ROLE_SALES, memberCaptor.getValue().getMemberRole());
		assertEquals("1", memberCaptor.getValue().getCanEdit());
		assertEquals("0", memberCaptor.getValue().getCanConfirm());
	}

	@Test
	@DisplayName("审核建船时申请单的常靠港随备注落进档案，运营不必回查申请单")
	void approveCreatesVesselCarriesApplyPortInRemark() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyVesselName("悦航新船");
		apply.setApplyPortName("上海港");
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName("悦航新船");

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselInfo> vesselCaptor = ArgumentCaptor.forClass(VesselInfo.class);
		verify(vesselInfoMapper).insert(vesselCaptor.capture());
		assertTrue(vesselCaptor.getValue().getRemark().contains("申请人常靠港：上海港"));
	}

	@Test
	@DisplayName("审核通过时船名匹配不到且未指定新船，返回可执行的提示而不是静默失败")
	void approveFailsWithActionableMessageWhenVesselNotFound() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyVesselName("不存在的船");
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselInfoMapper.selectOne(any())).thenReturn(null);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", new VesselBindAuditDTO()));
		assertTrue(ex.getMsg().contains("不存在的船"));
	}

	@Test
	@DisplayName("审核通过指定已有船舶且船名/IMO/呼号一致时直接绑定，不再建船")
	void approveUsesExistingVesselWhenSpecified() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyRole(VesselBindApply.ROLE_CREW);
		// 归一化后与目标船「悦航1号」相同（多一个空格）：属于「写法差异」，应放行
		apply.setApplyVesselName("悦航 1 号");
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);

		String vesselId = service.approveApply(TENANT, "admin-1", "apply-1", audit);

		assertEquals(VESSEL_ID, vesselId);
		verify(vesselInfoMapper, never()).insert(any(VesselInfo.class));
	}

	// ---------------------------------------------------------------------
	// 跨船绑定一致性门槛（本次安全加固核心）
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("申请船名与所选船舶完全不一致且未确认 → 拒绝，提示含「不一致」")
	void approveRejectsVesselMismatchWithoutConfirmation() {
		// 事故原型：申请「悦航8号」，运营在弹窗里选了「悦航1号」，此前零校验直接绑上
		VesselBindApply apply = pendingApply("悦航8号", null);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", audit));
		assertTrue(ex.getMsg().contains("不一致"));
		// 关键：拒绝时不能留下任何成员关系/申请单状态变更
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
		verify(vesselBindApplyMapper, never()).updateById(any(VesselBindApply.class));
	}

	@Test
	@DisplayName("不一致但未填审核意见 → 拒绝：确认位之外还必须有理由才能留痕")
	void approveRejectsVesselMismatchWithoutReason() {
		VesselBindApply apply = pendingApply("悦航8号", null);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);
		audit.setConfirmVesselMismatch(Boolean.TRUE);
		audit.setAuditRemark("   ");

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", audit));
		assertTrue(ex.getMsg().contains("必须填写审核意见"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("不一致 + 已确认 + 有理由 → 成功，成员来源 APPLY_AUDIT_MISMATCH 且审计落 BIND")
	void approveAllowsConfirmedVesselMismatch() {
		VesselBindApply apply = pendingApply("悦航8号", null);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);
		audit.setConfirmVesselMismatch(Boolean.TRUE);
		audit.setAuditRemark("船已改名，运营核对 IMO 后确认同一条船");

		String vesselId = service.approveApply(TENANT, "admin-1", "apply-1", audit);

		assertEquals(VESSEL_ID, vesselId);
		ArgumentCaptor<VesselMember> memberCaptor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(memberCaptor.capture());
		assertEquals(VesselMemberBindSource.APPLY_AUDIT_MISMATCH, memberCaptor.getValue().getBindSource());
		// bind_reason 必须是运营填写的偏离理由，这正是事后复核「为什么把 8 号绑到 7 号」的依据
		assertEquals("船已改名，运营核对 IMO 后确认同一条船", memberCaptor.getValue().getBindReason());

		ArgumentCaptor<VesselMemberAuditLog> auditCaptor = ArgumentCaptor.forClass(VesselMemberAuditLog.class);
		verify(vesselMemberAuditLogMapper).insert(auditCaptor.capture());
		assertEquals(VesselMemberAuditLog.ACTION_BIND, auditCaptor.getValue().getAction());
		assertEquals(VesselMemberBindSource.APPLY_AUDIT_MISMATCH, auditCaptor.getValue().getBindSource());
		assertNotNull(auditCaptor.getValue().getReason());
	}

	@Test
	@DisplayName("IMO 一致但船名不同 → 放行（强身份键优先），来源 APPLY_AUDIT")
	void approveAllowsImoMatchDespiteDifferentName() {
		VesselBindApply apply = pendingApply("悦航8号", "IMO1234567");
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		VesselInfo target = vessel(VESSEL_ID, "悦航7号", "IMO1234567", null);
		when(vesselInfoMapper.selectOne(any())).thenReturn(target);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselMember> captor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(captor.capture());
		assertEquals(VesselMemberBindSource.APPLY_AUDIT, captor.getValue().getBindSource());
	}

	@Test
	@DisplayName("呼号一致但船名不同 → 放行（呼号强于船名）")
	void approveAllowsCallSignMatchDespiteDifferentName() {
		VesselBindApply apply = pendingApply("悦航8号", null, "A3K9MP");
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID, "悦航7号", null, "A3K9MP"));

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselMember> captor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(captor.capture());
		assertEquals(VesselMemberBindSource.APPLY_AUDIT, captor.getValue().getBindSource());
	}

	@Test
	@DisplayName("船名归一化相等（「悦航 8 号」vs「悦航8号」）→ 放行，不必走确认流程")
	void approveAllowsNormalizedSameName() {
		VesselBindApply apply = pendingApply("悦航 8 号", null);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel(VESSEL_ID, "悦航8号", null, null));

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselMember> captor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(captor.capture());
		assertEquals(VesselMemberBindSource.APPLY_AUDIT, captor.getValue().getBindSource());
	}

	@Test
	@DisplayName("审核通过的 memberRole 传「1」（发起人）→ 拒绝：越权面必须堵住")
	void approveRejectsOwnerRole() {
		VesselBindApply apply = pendingApply("悦航1号", null);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);
		audit.setMemberRole(VesselMember.ROLE_OWNER);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", audit));
		assertTrue(ex.getMsg().contains("成员角色不合法"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("addMember 的 memberRole 传「1」（发起人）→ 拒绝")
	void addMemberRejectsOwnerRole() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMemberRole.SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(CREW);
		dto.setMemberRole(VesselMember.ROLE_OWNER);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.addMember(TENANT, SALES, VESSEL_ID, dto));
		assertTrue(ex.getMsg().contains("成员角色不合法"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("成员角色变更（已在船上但角色不同）→ 更新角色 + 写 ROLE_CHANGE 审计")
	void onboardingUpdatesRoleAndWritesAudit() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(CREW, VesselMemberRole.CREW));

		VesselMemberRegistrar.OnboardResult result = registrar().ensureOnboard(TENANT, VESSEL_ID, CREW,
				VesselMemberRole.CONFIRMER, "1", "1", "审核升级为采购确认人", VesselMemberBindSource.APPLY_AUDIT);

		assertEquals(VesselMemberRegistrar.Outcome.ROLE_UPDATED, result.outcome());
		assertEquals(VesselMemberRole.CONFIRMER, result.member().getMemberRole());
		verify(vesselMemberMapper).updateById(any(VesselMember.class));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));

		ArgumentCaptor<VesselMemberAuditLog> auditCaptor = ArgumentCaptor.forClass(VesselMemberAuditLog.class);
		verify(vesselMemberAuditLogMapper).insert(auditCaptor.capture());
		VesselMemberAuditLog audit = auditCaptor.getValue();
		assertEquals(VesselMemberAuditLog.ACTION_ROLE_CHANGE, audit.getAction());
		// 前后角色都要留：只看新角色无法判断这次变更影响面
		assertEquals(VesselMemberRole.CREW, audit.getOldMemberRole());
		assertEquals(VesselMemberRole.CONFIRMER, audit.getNewMemberRole());
	}

	@Test
	@DisplayName("Registrar 兜底拒绝非法角色：任何入口都绕不过白名单")
	void registrarRejectsNonAssignableRole() {
		// 入口层校验可能被将来新增的通道漏掉，这里必须独立拒绝，且不产生任何写操作
		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> registrar().ensureOnboard(TENANT, VESSEL_ID, CREW, VesselMember.ROLE_OWNER, "1", "0", null,
						VesselMemberBindSource.MANUAL));
		assertTrue(ex.getMsg().contains("成员角色不合法"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
		verify(vesselMemberMapper, never()).reviveRemovedMember(any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any());
	}

	@Test
	@DisplayName("审核通过时已是成员则只更新申请单，不重复插入成员")
	void approveDoesNotDuplicateExistingMembership() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyVesselName("悦航1号");
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);

		service.approveApply(TENANT, "admin-1", "apply-1", new VesselBindAuditDTO());

		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).updateById(captor.capture());
		assertEquals(VesselBindApply.STATUS_APPROVED, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("已处理的申请不能重复审核")
	void approveRejectsAlreadyHandled() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setStatus(VesselBindApply.STATUS_APPROVED);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);

		assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", new VesselBindAuditDTO()));
	}

	@Test
	@DisplayName("驳回必须填写原因")
	void rejectRequiresReason() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);

		assertThrows(ArynBusinessException.class, () -> service.rejectApply(TENANT, "admin-1", "apply-1", "  "));
		assertThrows(ArynBusinessException.class, () -> service.rejectApply(TENANT, "admin-1", "apply-1", null));
	}

	@Test
	@DisplayName("驳回写入原因与审核人")
	void rejectWritesReasonAndAuditor() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);

		service.rejectApply(TENANT, "admin-1", "apply-1", "船名无法核实");

		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).updateById(captor.capture());
		assertEquals(VesselBindApply.STATUS_REJECTED, captor.getValue().getStatus());
		assertEquals("admin-1", captor.getValue().getAuditBy());
		assertEquals("船名无法核实", captor.getValue().getAuditRemark());
	}

	// ---------------------------------------------------------------------
	// 邀请码
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("邀请码过期后不能兑换")
	void redeemRejectsExpiredCode() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 0, 0, LocalDateTime.now().minusMinutes(1), VesselInviteCode.STATUS_ACTIVE));

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.redeemInviteCode(TENANT, CREW, "ABC234"));
		assertTrue(ex.getMsg().contains("过期"));
	}

	@Test
	@DisplayName("邀请码已撤销后不能兑换")
	void redeemRejectsRevokedCode() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 0, 0, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_REVOKED));

		assertThrows(ArynBusinessException.class, () -> service.redeemInviteCode(TENANT, CREW, "ABC234"));
	}

	@Test
	@DisplayName("邀请码用完最大次数后不能兑换")
	void redeemRejectsWhenMaxUsesReached() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 1, 1, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE));

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.redeemInviteCode(TENANT, CREW, "ABC234"));
		assertTrue(ex.getMsg().contains("上限"));
	}

	@Test
	@DisplayName("maxUses=0 表示不限次，使用后仍可兑换")
	void redeemAllowsUnlimitedCode() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 0, 99, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of(user(CREW, "船员甲", "13800001234")));

		assertNotNull(service.redeemInviteCode(TENANT, CREW, "ABC234"));
	}

	@Test
	@DisplayName("已是该船成员时不能再用邀请码重复加入")
	void redeemRejectsExistingMember() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 0, 0, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE));
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.redeemInviteCode(TENANT, CREW, "ABC234"));
		assertTrue(ex.getMsg().contains("已是该船成员"));
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("兑换成功后已用次数自增，成员以普通船员身份入船")
	void redeemIncrementsUsedCountAndJoinsAsCrew() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 5, 2, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of(user(CREW, "船员甲", "13800001234")));

		VesselMemberVO vo = service.redeemInviteCode(TENANT, CREW, "ABC234");

		assertEquals(VesselMember.ROLE_CREW, vo.getMemberRole());
		ArgumentCaptor<VesselInviteCode> captor = ArgumentCaptor.forClass(VesselInviteCode.class);
		verify(vesselInviteCodeMapper).updateById(captor.capture());
		assertEquals(3, captor.getValue().getUsedCount());
	}

	@Test
	@DisplayName("邀请码大小写与空格不敏感，便于口头或手抄传递")
	void redeemIsCaseInsensitive() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 0, 0, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of(user(CREW, "船员甲", "13800001234")));

		assertNotNull(service.redeemInviteCode(TENANT, CREW, "  abc234  "));
	}

	@Test
	@DisplayName("不能撤销他人生成的邀请码")
	void revokeOnlyByOwner() {
		VesselInviteCode code = invite("ABC234", 0, 0, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE);
		code.setOwnerUserId(SALES);
		when(vesselInviteCodeMapper.selectById("invite-1")).thenReturn(code);

		assertThrows(ArynBusinessException.class, () -> service.revokeInviteCode(TENANT, CREW, "invite-1"));
	}

	@Test
	@DisplayName("生成的邀请码为 6 位且不含易混字符 0/O/1/I")
	void generatedCodeAvoidsAmbiguousChars() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselInviteCodeMapper.selectCount(any())).thenReturn(0L);

		for (int i = 0; i < 30; i++) {
			VesselInviteCode code = service.generateInviteCode(TENANT, SALES, VESSEL_ID, 1, 24, null);
			assertNotNull(code.getCode());
			assertEquals(6, code.getCode().length());
			assertTrue(code.getCode().matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}"),
					"邀请码含易混字符: " + code.getCode());
		}
	}

	@Test
	@DisplayName("邀请码有效期默认 24 小时，且被限制在 30 天内")
	void generatedCodeClampsExpiry() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselInviteCodeMapper.selectCount(any())).thenReturn(0L);

		VesselInviteCode defaulted = service.generateInviteCode(TENANT, SALES, VESSEL_ID, 1, null, null);
		assertTrue(defaulted.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));

		VesselInviteCode clamped = service.generateInviteCode(TENANT, SALES, VESSEL_ID, 1, 24 * 999, null);
		assertTrue(clamped.getExpiresAt().isBefore(LocalDateTime.now().plusHours(24 * 30 + 1)),
				"有效期应被限制在 30 天内");
	}

	@Test
	@DisplayName("同船同人重新生成时先清掉旧的有效码，避免多个码并存")
	void generateReplacesPreviousActiveCode() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselInviteCodeMapper.selectCount(any())).thenReturn(0L);

		service.generateInviteCode(TENANT, SALES, VESSEL_ID, 1, 24, null);

		verify(vesselInviteCodeMapper).delete(any());
	}

	// ---------------------------------------------------------------------
	// 成员检索
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("检索候选时标记已在船用户，前端据此禁用添加按钮")
	void searchCandidatesFlagsExistingMembers() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselMemberMapper.selectList(any())).thenReturn(List.of(member(CREW, VesselMember.ROLE_CREW)));
		when(remoteMallUserService.searchUsersForBinding("138", 20))
				.thenReturn(List.of(user(CREW, "船员甲", "13800001234"), user("new-user", "新人", "13900001234")));

		List<VesselMemberVO> candidates = service.searchCandidates(TENANT, SALES, VESSEL_ID, "138");

		assertEquals(2, candidates.size());
		VesselMemberVO onboard = candidates.stream().filter(c -> CREW.equals(c.getUserId())).findFirst().orElseThrow();
		VesselMemberVO fresh = candidates.stream().filter(c -> "new-user".equals(c.getUserId())).findFirst().orElseThrow();
		assertEquals("1", onboard.getStatus(), "已在船用户应标记为 1");
		assertEquals("0", fresh.getStatus(), "不在船用户应标记为 0");
	}

	@Test
	@DisplayName("空关键词不检索，避免全量拉取用户")
	void searchCandidatesRequiresKeyword() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));

		assertTrue(service.searchCandidates(TENANT, SALES, VESSEL_ID, "   ").isEmpty());
		verify(remoteMallUserService, never()).searchUsersForBinding(any(), any(Integer.class));
	}

	@Test
	@DisplayName("按手机号添加成员时手机号不存在给出可操作提示")
	void addMemberByPhoneReportsUnknownUser() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(remoteMallUserService.getUserByPhone("13900000000")).thenReturn(null);
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setPhone("13900000000");

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.addMember(TENANT, SALES, VESSEL_ID, dto));
		assertTrue(ex.getMsg().contains("已注册"));
	}

	@Test
	@DisplayName("既没给用户ID也没给手机号时明确报错")
	void addMemberRequiresIdentifier() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		assertThrows(ArynBusinessException.class,
				() -> service.addMember(TENANT, SALES, VESSEL_ID, new VesselMemberAddDTO()));
	}

	@Test
	@DisplayName("不能把自己重复加为成员")
	void addMemberRejectsSelf() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(SALES);

		assertThrows(ArynBusinessException.class, () -> service.addMember(TENANT, SALES, VESSEL_ID, dto));
	}

	@Test
	@DisplayName("已在船的用户不能重复添加")
	void addMemberRejectsDuplicateTarget() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(CREW);

		assertThrows(ArynBusinessException.class, () -> service.addMember(TENANT, SALES, VESSEL_ID, dto));
	}

	@Test
	@DisplayName("已停用船舶不能添加成员或生成邀请码")
	void inactiveVesselRejectsWrites() {
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMember.ROLE_SALES));
		when(vesselInfoMapper.selectOne(any())).thenReturn(null);

		VesselMemberAddDTO dto = new VesselMemberAddDTO();
		dto.setUserId(CREW);
		assertThrows(ArynBusinessException.class, () -> service.addMember(TENANT, SALES, VESSEL_ID, dto));
		assertThrows(ArynBusinessException.class,
				() -> service.generateInviteCode(TENANT, SALES, VESSEL_ID, 1, 24, null));
	}

	@Test
	@DisplayName("任何写入都带租户条件，不跨租户读写")
	void writesAreTenantScoped() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航2号");

		service.submitApply(TENANT, SALES, dto);

		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).insert(captor.capture());
		assertEquals(TENANT, captor.getValue().getTenantId());
	}

	// ---------------------------------------------------------------------
	// 审核预检与认船键（IMO/呼号）
	// ---------------------------------------------------------------------

	/**
	 * 只填 IMO 的申请单。
	 *
	 * <p>呼号已拆成独立字段（applyVesselCallSign）：此前这里把「IMO 或呼号」
	 * 都塞进 applyVesselImo，导致服务端要靠位数猜，现在两个入参分开。
	 */
	private VesselBindApply pendingApply(String name, String imo) {
		return pendingApply(name, imo, null);
	}

	private VesselBindApply pendingApply(String name, String imo, String callSign) {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyVesselName(name);
		apply.setApplyVesselImo(imo);
		apply.setApplyVesselCallSign(callSign);
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		return apply;
	}

	private VesselInfo vessel(String id, String name, String imo, String callSign) {
		VesselInfo vessel = new VesselInfo();
		vessel.setId(id);
		vessel.setTenantId(TENANT);
		vessel.setVesselName(name);
		vessel.setImoCode(imo);
		vessel.setCallSign(callSign);
		vessel.setStatus("1");
		return vessel;
	}

	@Test
	@DisplayName("预检按 IMO 命中时建议绑定已有船并直接预选——船名写歪也能认出同一条船")
	void precheckSuggestExistingWhenImoMatches() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航七号", "IMO9800999"));
		when(vesselInfoMapper.selectList(any()))
			.thenReturn(List.of(vessel("v-9", "悦航验收测试船", "IMO9800999", null)));

		VesselBindPrecheckVO precheck = service.precheckApply(TENANT, "apply-1");

		assertEquals("existing", precheck.getSuggestedMode());
		assertEquals("v-9", precheck.getSuggestedVesselId());
		assertEquals("IMO一致", precheck.getCandidates().get(0).getMatchReason());
		assertTrue(precheck.getSuggestion().contains("悦航验收测试船"));
	}

	@Test
	@DisplayName("预检把申请里填的呼号也当认船键——呼号命中同样建议绑定已有")
	void precheckMatchesCallSign() {
		when(vesselBindApplyMapper.selectOne(any()))
			.thenReturn(pendingApply("写错的船名", null, "A3K9MP"));
		when(vesselInfoMapper.selectList(any()))
			.thenReturn(List.of(vessel("v-2", "悦航6号", null, "A3K9MP")));

		VesselBindPrecheckVO precheck = service.precheckApply(TENANT, "apply-1");

		assertEquals("existing", precheck.getSuggestedMode());
		assertEquals("呼号一致", precheck.getCandidates().get(0).getMatchReason());
	}

	@Test
	@DisplayName("预检无任何命中时建议新建，并说明申请未填 IMO/呼号只能按船名匹配")
	void precheckSuggestsNewWhenNothingMatches() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("崭新号", null));
		when(vesselInfoMapper.selectList(any())).thenReturn(List.of());

		VesselBindPrecheckVO precheck = service.precheckApply(TENANT, "apply-1");

		assertEquals("new", precheck.getSuggestedMode());
		assertTrue(precheck.getSuggestion().contains("未填 IMO/呼号"));
		assertTrue(precheck.getCandidates().isEmpty());
	}

	@Test
	@DisplayName("预检发现相近船名时只提示可能同船、不自动预选，避免误绑")
	void precheckFlagsSimilarNameWithoutPreselecting() {
		// 申请单写成「悦航 6 号」（含空格），档案里是「悦航6号」：精确匹配落空，只能靠归一化后的相近判定提示
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航 6 号", null));
		when(vesselInfoMapper.selectList(any()))
			.thenReturn(List.of(), List.of(vessel("v-6", "悦航6号", "1234556", null)));

		VesselBindPrecheckVO precheck = service.precheckApply(TENANT, "apply-1");

		assertEquals("existing", precheck.getSuggestedMode());
		assertEquals(null, precheck.getSuggestedVesselId());
		assertEquals("船名相近", precheck.getCandidates().get(0).getMatchReason());
	}

	@Test
	@DisplayName("同系列船名不会互相误报为相近——「悦航1号」不应提示匹配到「悦航2号」")
	void precheckDoesNotConfuseSiblingVesselNames() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航3号", null));
		when(vesselInfoMapper.selectList(any()))
			.thenReturn(List.of(), List.of(vessel("v-1", "悦航1号", null, null), vessel("v-2", "悦航2号", null, null)));

		VesselBindPrecheckVO precheck = service.precheckApply(TENANT, "apply-1");

		assertEquals("new", precheck.getSuggestedMode());
		assertTrue(precheck.getCandidates().isEmpty());
	}

	@Test
	@DisplayName("自动匹配优先用 IMO/呼号，船名不一致也能绑到正确的船")
	void approveAutoMatchUsesImoBeforeName() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("申请人写的船名", "IMO9800999"));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(vessel("v-9", "档案里的船名", "IMO9800999", null));

		String vesselId = service.approveApply(TENANT, "admin-1", "apply-1", new VesselBindAuditDTO());

		assertEquals("v-9", vesselId);
		verify(vesselInfoMapper, never()).insert(any(VesselInfo.class));
	}

	@Test
	@DisplayName("新建同名船舶必须显式确认，否则拦住静默产出重复档案")
	void approveNewRejectsDuplicateNameWithoutConfirmation() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航6号", null));
		when(vesselInfoMapper.selectCount(any())).thenReturn(1L);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName("悦航6号");

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", audit));
		assertTrue(ex.getMsg().contains("已存在同名在营船舶"));
		verify(vesselInfoMapper, never()).insert(any(VesselInfo.class));
	}

	@Test
	@DisplayName("确认后允许新建同名船舶——确实存在同名不同船的情形")
	void approveNewAllowsDuplicateNameWhenConfirmed() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航6号", null));
		when(vesselInfoMapper.selectCount(any())).thenReturn(1L);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName("悦航6号");
		audit.setConfirmDuplicateName(true);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		verify(vesselInfoMapper).insert(any(VesselInfo.class));
	}

	@Test
	@DisplayName("新建时 IMO 撞库直接引导绑定已有，不给强制新建的口子")
	void approveNewRejectsExistingImo() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("新船名", null));
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L, 1L);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName("新船名");
		audit.setNewVesselImo("9800999");

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.approveApply(TENANT, "admin-1", "apply-1", audit));
		assertTrue(ex.getMsg().contains("已存在"));
		verify(vesselInfoMapper, never()).insert(any(VesselInfo.class));
	}

	@Test
	@DisplayName("候选检索只返回在营船舶且带认船字段")
	void searchVesselOptionsReturnsActiveOnlyWithIdentityFields() {
		when(vesselInfoMapper.selectList(any()))
			.thenReturn(List.of(vessel("v-1", "悦航1号", "9800001", "B7XYZ")));

		List<VesselBindVesselOptionVO> options = service.searchVesselOptions(TENANT, "悦航");

		assertEquals(1, options.size());
		assertEquals("悦航1号", options.get(0).getVesselName());
		assertEquals("9800001", options.get(0).getImoCode());
		assertEquals("B7XYZ", options.get(0).getCallSign());
	}

	@Test
	@DisplayName("预检查 IMO 时只比 imo_code，不会拿 IMO 去撞别人的呼号列")
	void precheckQueriesImoAgainstImoColumnOnly() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), VesselInfo.class);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("某船", "9800999"));
		ArgumentCaptor<Wrapper<VesselInfo>> captor = ArgumentCaptor.forClass(Wrapper.class);
		when(vesselInfoMapper.selectList(captor.capture())).thenReturn(List.of());

		service.precheckApply(TENANT, "apply-1");

		String sql = captor.getAllValues().stream()
			.map(Wrapper::getSqlSegment)
			.filter(segment -> segment.contains("imo_code"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("未捕获到 IMO 查询，实际：" + captor.getAllValues()));
		// 分列采集后 IMO 只比 imo_code：此前一个输入框同时比两列，
		// 会出现「你填的是 IMO，却命中了某条船的呼号」这种错误绑定
		assertTrue(sql.contains("imo_code"), "应查 imo_code，实际：" + sql);
		assertFalse(sql.contains("call_sign"), "IMO 查询不应牵连 call_sign 列，实际：" + sql);
		// 租户与在营过滤不能被条件吞掉
		assertTrue(sql.contains("tenant_id"), "缺少租户条件，实际：" + sql);
		assertTrue(sql.contains("status"), "缺少在营状态条件，实际：" + sql);
	}

	@Test
	@DisplayName("预检查呼号时只比 call_sign，不会拿呼号去撞 IMO 列")
	void precheckQueriesCallSignAgainstCallSignColumnOnly() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), VesselInfo.class);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("某船", null, "3EABC"));
		ArgumentCaptor<Wrapper<VesselInfo>> captor = ArgumentCaptor.forClass(Wrapper.class);
		when(vesselInfoMapper.selectList(captor.capture())).thenReturn(List.of());

		service.precheckApply(TENANT, "apply-1");

		String sql = captor.getAllValues().stream()
			.map(Wrapper::getSqlSegment)
			.filter(segment -> segment.contains("call_sign"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("未捕获到呼号查询，实际：" + captor.getAllValues()));
		assertTrue(sql.contains("call_sign"), "应查 call_sign，实际：" + sql);
		assertFalse(sql.contains("imo_code"), "呼号查询不应牵连 imo_code 列，实际：" + sql);
		assertTrue(sql.contains("tenant_id"), "缺少租户条件，实际：" + sql);
	}

	@Test
	@DisplayName("申请单同时填了 IMO 与呼号时，两列各查一次、候选合并去重")
	void precheckQueriesBothIdentityColumnsWhenBothPresent() {
		when(vesselBindApplyMapper.selectOne(any()))
			.thenReturn(pendingApply("某船", "9800999", "3EABC"));
		when(vesselInfoMapper.selectList(any()))
			.thenReturn(List.of(vessel("v-1", "按IMO命中", "9800999", null)),
				List.of(vessel("v-2", "按呼号命中", null, "3EABC")));

		VesselBindPrecheckVO precheck = service.precheckApply(TENANT, "apply-1");

		assertEquals(2, precheck.getCandidates().size());
		assertEquals("IMO一致", precheck.getCandidates().get(0).getMatchReason());
		assertEquals("呼号一致", precheck.getCandidates().get(1).getMatchReason());
	}

	@Test
	@DisplayName("自动匹配按 IMO → 呼号 → 船名的优先级逐级回退")
	void approveAutoMatchFallsBackFromImoToCallSign() {
		// IMO 填了但系统内没有；呼号能命中
		when(vesselBindApplyMapper.selectOne(any()))
			.thenReturn(pendingApply("申请人写的船名", "9800111", "3EABC"));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any()))
			.thenReturn(null)
			.thenReturn(vessel("v-call", "呼号命中的船", "9800999", "3EABC"));

		String vesselId = service.approveApply(TENANT, "admin-1", "apply-1", new VesselBindAuditDTO());

		assertEquals("v-call", vesselId);
		verify(vesselInfoMapper, never()).insert(any(VesselInfo.class));
	}

	@Test
	@DisplayName("审核建船落库前 trim IMO 与呼号：查重用 trim 值、存原值会产出匹配不上的脏档案")
	void approveCreatesVesselTrimsIdentityFields() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航新船", null));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(null);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName(" 悦航新船 ");
		audit.setNewVesselImo(" 9800123 ");
		audit.setNewVesselCallSign(" 3EABC ");
		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselInfo> captor = ArgumentCaptor.forClass(VesselInfo.class);
		verify(vesselInfoMapper).insert(captor.capture());
		assertEquals("悦航新船", captor.getValue().getVesselName());
		assertEquals("9800123", captor.getValue().getImoCode());
		assertEquals("3EABC", captor.getValue().getCallSign());
	}

	@Test
	@DisplayName("审核建船未指定船型时落缺省值，指定非法码值直接拒绝")
	void approveCreatesVesselValidatesVesselType() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航新船", null));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(null);

		VesselBindAuditDTO defaulted = new VesselBindAuditDTO();
		defaulted.setNewVesselName("悦航新船");
		service.approveApply(TENANT, "admin-1", "apply-1", defaulted);

		ArgumentCaptor<VesselInfo> captor = ArgumentCaptor.forClass(VesselInfo.class);
		verify(vesselInfoMapper).insert(captor.capture());
		assertEquals(VesselType.DEFAULT, captor.getValue().getVesselType());

		// DTO 注释里那套旧口径（1货轮…）会让人传出错码值：必须拒绝而不是静默落库
		VesselBindAuditDTO invalid = new VesselBindAuditDTO();
		invalid.setNewVesselName("另一条船");
		invalid.setNewVesselType("9");
		assertThrows(ArynBusinessException.class,
			() -> service.approveApply(TENANT, "admin-1", "apply-1", invalid));
	}

	@Test
	@DisplayName("审核建船能写入审核员选的船型（此前无控件、恒落「其他」）")
	void approveCreatesVesselWithSelectedType() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("集装箱船", null));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectCount(any())).thenReturn(0L);
		when(vesselInfoMapper.selectOne(any())).thenReturn(null);

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setNewVesselName("集装箱船");
		audit.setNewVesselType(VesselType.CONTAINER);
		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselInfo> captor = ArgumentCaptor.forClass(VesselInfo.class);
		verify(vesselInfoMapper).insert(captor.capture());
		assertEquals(VesselType.CONTAINER, captor.getValue().getVesselType());
	}

	@Test
	@DisplayName("申请单的 IMO 与呼号分别落库：审核建船时两列不互相覆盖")
	void submitApplyPersistsImoAndCallSignSeparately() {
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		when(remoteMessageStaffService.queryRecipientsByPermission(any(), any())).thenReturn(List.of());

		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyRole(VesselBindApply.ROLE_CREW);
		dto.setApplyVesselName("悦航1号");
		dto.setApplyVesselImo(" 9800001 ");
		dto.setApplyVesselCallSign(" 3EABC ");
		service.submitApply(TENANT, CREW, dto);

		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).insert(captor.capture());
		assertEquals("9800001", captor.getValue().getApplyVesselImo());
		assertEquals("3EABC", captor.getValue().getApplyVesselCallSign());
	}

	// ---------------------------------------------------------------------
	// 成员复活（唯一键不含 del_flag）
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("被移出过的用户重新加入走复活：唯一键不含 del_flag，直接 insert 必撞键 1062")
	void onboardingRevivesRemovedMemberInsteadOfInserting() {
		// 有效计数为 0（已逻辑删除），但历史行占着唯一键位
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselMemberMapper.reviveRemovedMember(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(1);

		VesselMemberRegistrar.Outcome outcome = registrar()
			.ensureOnboard(TENANT, VESSEL_ID, CREW, VesselMember.ROLE_CREW, "1", "0", "重新加入",
					VesselMemberBindSource.MANUAL)
			.outcome();

		assertEquals(VesselMemberRegistrar.Outcome.REVIVED, outcome);
		// 关键：不能再 insert，否则 MySQL 1062
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("没有历史行时才新建")
	void onboardingCreatesWhenNoHistory() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselMemberMapper.reviveRemovedMember(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(0);

		VesselMemberRegistrar.Outcome outcome = registrar()
			.ensureOnboard(TENANT, VESSEL_ID, CREW, VesselMember.ROLE_CREW, "1", "0", null,
					VesselMemberBindSource.MANUAL)
			.outcome();

		assertEquals(VesselMemberRegistrar.Outcome.CREATED, outcome);
		verify(vesselMemberMapper).insert(any(VesselMember.class));
	}

	@Test
	@DisplayName("已在船上的人不动数据")
	void onboardingKeepsExistingMemberUntouched() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(CREW, VesselMember.ROLE_CREW));

		VesselMemberRegistrar.Outcome outcome = registrar()
			.ensureOnboard(TENANT, VESSEL_ID, CREW, VesselMember.ROLE_CREW, "1", "0", null,
					VesselMemberBindSource.MANUAL)
			.outcome();

		assertEquals(VesselMemberRegistrar.Outcome.ALREADY_ONBOARD, outcome);
		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
		verify(vesselMemberMapper, never()).reviveRemovedMember(any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any());
	}

	@Test
	@DisplayName("退出船舶后可再次通过邀请码加入——复活而非撞键")
	void redeemAfterLeaveRevives() {
		when(vesselInviteCodeMapper.selectOne(any())).thenReturn(
				invite("ABC234", 0, 0, LocalDateTime.now().plusHours(1), VesselInviteCode.STATUS_ACTIVE));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselMemberMapper.reviveRemovedMember(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(1);
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(CREW, VesselMember.ROLE_CREW));
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		service.redeemInviteCode(TENANT, CREW, "ABC234");

		verify(vesselMemberMapper, never()).insert(any(VesselMember.class));
	}

	// ---------------------------------------------------------------------
	// 运营解绑
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("运营可解绑普通成员，并作废其本人有效邀请码")
	void adminCanUnbindCrew() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		VesselMember crew = member(CREW, VesselMember.ROLE_CREW);
		when(vesselMemberMapper.selectOne(any())).thenReturn(crew);

		service.adminUnbindMember(TENANT, "admin-1", VESSEL_ID, CREW, "绑错船了");

		verify(vesselMemberMapper).deleteById(crew.getId());
		verify(vesselInviteCodeMapper).delete(any());
	}

	@Test
	@DisplayName("不能解绑发起人——否则该船没有管理角色")
	void adminCannotUnbindOwner() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectOne(any())).thenReturn(member("owner-user", VesselMember.ROLE_OWNER));

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.adminUnbindMember(TENANT, "admin-1", VESSEL_ID, "owner-user", null));
		assertTrue(ex.getMsg().contains("发起人"));
		verify(vesselMemberMapper, never()).deleteById(any(String.class));
	}

	@Test
	@DisplayName("解绑不是本船成员时报错，不静默成功")
	void adminUnbindRejectsNonMember() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselMemberMapper.selectOne(any())).thenReturn(null);

		assertThrows(ArynBusinessException.class,
				() -> service.adminUnbindMember(TENANT, "admin-1", VESSEL_ID, "stranger", null));
		verify(vesselMemberMapper, never()).deleteById(any(String.class));
	}

	// ---------------------------------------------------------------------
	// 审核结果通知
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("审核通过后给申请人发站内信（含船名与角色）")
	void approveNotifiesApplicant() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航1号", null));
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselMemberMapper.reviveRemovedMember(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(0);
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);
		audit.setMemberRole(VesselMember.ROLE_CONFIRMER);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate).convertAndSend(any(String.class), captor.capture());
		MessageSendCommand command = (MessageSendCommand) captor.getValue();
		assertEquals("MALL_USER", command.getRecipientType());
		assertEquals(CREW, command.getRecipientId());
		assertTrue(command.getTitle().contains("已通过"));
		assertTrue(command.getContent().contains("悦航1号"));
		assertTrue(command.getContent().contains("采购确认人"));
		// 跳转必须落 C 端绑定页，且是 JSON 对象（裸串会被消费端校验拒绝）
		assertTrue(command.getJumpPayload().contains("/sub-pages/vessel/bind/index"));
	}

	@Test
	@DisplayName("审核驳回也要通知申请人，并带上驳回原因")
	void rejectNotifiesApplicantWithReason() {
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(pendingApply("悦航9号", null));

		service.rejectApply(TENANT, "admin-1", "apply-1", "船名无法核实");

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate).convertAndSend(any(String.class), captor.capture());
		MessageSendCommand command = (MessageSendCommand) captor.getValue();
		assertEquals(CREW, command.getRecipientId());
		assertTrue(command.getTitle().contains("未通过"));
		assertTrue(command.getContent().contains("船名无法核实"));
	}

	@Test
	@DisplayName("跨船通过时通知必须说清「申请的是什么、实际绑的是哪条、原因」")
	void approveMismatchNotificationNamesBothVessels() {
		VesselBindApply apply = pendingApply("悦航8号", null);
		when(vesselBindApplyMapper.selectOne(any())).thenReturn(apply);
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		// requireVesselActive 与通知里的 selectById 都返回「悦航1号」
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		when(vesselInfoMapper.selectById(any())).thenReturn(activeVessel());

		VesselBindAuditDTO audit = new VesselBindAuditDTO();
		audit.setMatchedVesselId(VESSEL_ID);
		audit.setConfirmVesselMismatch(Boolean.TRUE);
		audit.setAuditRemark("船已改名");

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate).convertAndSend(any(String.class), captor.capture());
		MessageSendCommand command = (MessageSendCommand) captor.getValue();
		// 契约 3.10：不能把实际绑定船说成「您申请的」
		assertTrue(command.getSummary().contains("您申请的「悦航8号」"));
		assertTrue(command.getSummary().contains("实际绑定「悦航1号」"));
		assertTrue(command.getContent().contains("原因：船已改名"));
	}

	// ---------------------------------------------------------------------
	// 管理端解绑审计与成员来源回填
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("运营解绑写 UNBIND 审计：留下操作人、原角色与原因")
	void adminUnbindWritesAudit() {
		when(vesselInfoMapper.selectOne(any())).thenReturn(activeVessel());
		VesselMember crew = member(CREW, VesselMemberRole.CREW);
		crew.setBindSource(VesselMemberBindSource.APPLY_AUDIT);
		when(vesselMemberMapper.selectOne(any())).thenReturn(crew);

		service.adminUnbindMember(TENANT, "admin-1", VESSEL_ID, CREW, "绑错船了");

		ArgumentCaptor<VesselMemberAuditLog> captor = ArgumentCaptor.forClass(VesselMemberAuditLog.class);
		verify(vesselMemberAuditLogMapper).insert(captor.capture());
		VesselMemberAuditLog audit = captor.getValue();
		assertEquals(VesselMemberAuditLog.ACTION_UNBIND, audit.getAction());
		assertEquals(CREW, audit.getUserId());
		assertEquals(VESSEL_ID, audit.getVesselId());
		assertEquals(VesselMemberRole.CREW, audit.getOldMemberRole());
		// 被解绑的这条关系当初是审核通过来的：按来源统计误绑时靠它
		assertEquals(VesselMemberBindSource.APPLY_AUDIT, audit.getBindSource());
		assertEquals("admin-1", audit.getOperatorId());
		assertTrue(audit.getReason().contains("绑错船了"));
	}

	@Test
	@DisplayName("成员列表回填绑定来源与中文标签，未知来源回退「手工/历史」")
	void memberListFillsBindSource() {
		VesselMember shareMember = member(CREW, VesselMemberRole.CREW);
		shareMember.setBindSource(VesselMemberBindSource.SHARE_CART);
		VesselMember legacy = member("legacy-user", VesselMemberRole.CREW);
		// 历史行为空（DB 迁移前写入）：标签必须回退，不能让前端显示空白
		legacy.setBindSource(null);
		when(vesselMemberMapper.selectOne(any())).thenReturn(member(SALES, VesselMemberRole.SALES));
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		when(vesselMemberMapper.selectList(any())).thenReturn(List.of(shareMember, legacy));
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of());

		List<VesselMemberVO> members = service.listMembers(TENANT, SALES, VESSEL_ID);

		assertEquals(VesselMemberBindSource.SHARE_CART, members.get(0).getBindSource());
		assertEquals("分享购物车自动加入", members.get(0).getBindSourceLabel());
		assertNull(members.get(1).getBindSource());
		assertEquals("手工/历史", members.get(1).getBindSourceLabel());
	}

	@Test
	@DisplayName("「我的申请」标记申请船与实际绑定船不一致——误绑在用户侧也能看见")
	void applyListFlagsVesselMismatch() {
		VesselBindApply mismatch = pendingApply("悦航8号", null);
		mismatch.setStatus(VesselBindApply.STATUS_APPROVED);
		mismatch.setMatchedVesselId(VESSEL_ID);
		VesselBindApply sameName = pendingApply("悦航 1 号", null);
		sameName.setId("apply-2");
		sameName.setStatus(VesselBindApply.STATUS_APPROVED);
		sameName.setMatchedVesselId(VESSEL_ID);
		when(vesselBindApplyMapper.selectList(any())).thenReturn(List.of(mismatch, sameName));
		when(vesselInfoMapper.selectList(any())).thenReturn(List.of(activeVessel()));
		when(vesselMemberMapper.selectList(any())).thenReturn(List.of());
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of());

		List<VesselBindApplyVO> applies = service.myApplies(TENANT, CREW);

		assertEquals(Boolean.TRUE, applies.get(0).getVesselMismatch());
		// 归一化相等（多一个空格）不算不一致：口径必须与审核判定一致
		assertEquals(Boolean.FALSE, applies.get(1).getVesselMismatch());
	}
}
