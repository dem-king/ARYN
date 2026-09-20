package com.aryn.cloud.vessel.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.dto.VesselBindApplyDTO;
import com.aryn.cloud.vessel.api.dto.VesselBindAuditDTO;
import com.aryn.cloud.vessel.api.dto.VesselMemberAddDTO;
import com.aryn.cloud.vessel.api.entity.VesselBindApply;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselInviteCode;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.aryn.cloud.vessel.mapper.VesselBindApplyMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselInviteCodeMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.impl.VesselBindServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

	private RemoteMallUserService remoteMallUserService;

	private VesselBindServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		vesselMemberMapper = mock(VesselMemberMapper.class);
		vesselBindApplyMapper = mock(VesselBindApplyMapper.class);
		vesselInviteCodeMapper = mock(VesselInviteCodeMapper.class);
		vesselInfoMapper = mock(VesselInfoMapper.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		service = new VesselBindServiceImpl(vesselMemberMapper, vesselBindApplyMapper, vesselInviteCodeMapper,
				vesselInfoMapper);
		// @DubboReference 字段在单测中无容器注入，反射塞入 mock
		Field field = VesselBindServiceImpl.class.getDeclaredField("remoteMallUserService");
		field.setAccessible(true);
		field.set(service, remoteMallUserService);
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
	// 申请
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("已是成员时不允许重复提交申请")
	void submitApplyRejectedWhenAlreadyMember() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(1L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航1号");

		assertThrows(ArynBusinessException.class, () -> service.submitApply(TENANT, CREW, dto));
		verify(vesselBindApplyMapper, never()).insert(any(VesselBindApply.class));
	}

	@Test
	@DisplayName("已有待审核申请时不允许重复提交")
	void submitApplyRejectedWhenPendingExists() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(1L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航1号");

		assertThrows(ArynBusinessException.class, () -> service.submitApply(TENANT, CREW, dto));
	}

	@Test
	@DisplayName("申请角色只允许普通船员(2)与业务员(4)")
	void submitApplyRejectsUnknownRole() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航1号");
		dto.setApplyRole("9");

		assertThrows(ArynBusinessException.class, () -> service.submitApply(TENANT, CREW, dto));
	}

	@Test
	@DisplayName("业务员认领船舶：applyRole=4 的申请被原样落库")
	void submitApplySupportsSalesClaim() {
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
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
		audit.setMemberRole(VesselMember.ROLE_SALES);

		service.approveApply(TENANT, "admin-1", "apply-1", audit);

		ArgumentCaptor<VesselInfo> vesselCaptor = ArgumentCaptor.forClass(VesselInfo.class);
		verify(vesselInfoMapper).insert(vesselCaptor.capture());
		assertEquals("悦航新船", vesselCaptor.getValue().getVesselName());
		assertEquals("1", vesselCaptor.getValue().getStatus());

		ArgumentCaptor<VesselMember> memberCaptor = ArgumentCaptor.forClass(VesselMember.class);
		verify(vesselMemberMapper).insert(memberCaptor.capture());
		assertEquals(VesselMember.ROLE_SALES, memberCaptor.getValue().getMemberRole());
		assertEquals("1", memberCaptor.getValue().getCanEdit());
		assertEquals("0", memberCaptor.getValue().getCanConfirm());
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
	@DisplayName("审核通过指定已有船舶时直接绑定，不再建船")
	void approveUsesExistingVesselWhenSpecified() {
		VesselBindApply apply = new VesselBindApply();
		apply.setId("apply-1");
		apply.setTenantId(TENANT);
		apply.setUserId(CREW);
		apply.setApplyRole(VesselBindApply.ROLE_CREW);
		apply.setApplyVesselName("随便写的船名");
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
		when(vesselMemberMapper.selectCount(any())).thenReturn(0L);
		when(vesselBindApplyMapper.selectCount(any())).thenReturn(0L);
		VesselBindApplyDTO dto = new VesselBindApplyDTO();
		dto.setApplyVesselName("悦航2号");

		service.submitApply(TENANT, SALES, dto);

		ArgumentCaptor<VesselBindApply> captor = ArgumentCaptor.forClass(VesselBindApply.class);
		verify(vesselBindApplyMapper).insert(captor.capture());
		assertEquals(TENANT, captor.getValue().getTenantId());
	}

}
