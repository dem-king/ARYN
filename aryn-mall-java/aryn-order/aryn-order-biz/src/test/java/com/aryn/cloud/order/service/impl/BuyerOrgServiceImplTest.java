package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.BuyerOrgConstants;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgSaveDTO;
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.BuyerOrgApply;
import com.aryn.cloud.order.api.entity.BuyerOrgMember;
import com.aryn.cloud.order.mapper.BuyerOrgApplyMapper;
import com.aryn.cloud.order.mapper.BuyerOrgInviteCodeMapper;
import com.aryn.cloud.order.mapper.BuyerOrgMapper;
import com.aryn.cloud.order.mapper.BuyerOrgMemberMapper;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购主体 P0 守门测试。
 *
 * <p>覆盖三类最容易在双模式/补字段时回归的边界：
 * 待审核主体允许建车下单、非成员不能借主体建车、创建申请遇到重复信用代码必须拦下，
 * 以及运营审核通过创建申请后申请人自动成为厂长成员。
 *
 * @author aryn
 * @since 2026/11/12
 */
class BuyerOrgServiceImplTest {

	private BuyerOrgMapper buyerOrgMapper;

	private BuyerOrgMemberMapper buyerOrgMemberMapper;

	private BuyerOrgApplyMapper buyerOrgApplyMapper;

	private BuyerOrgInviteCodeMapper buyerOrgInviteCodeMapper;

	private BuyerOrgServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), BuyerOrg.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), BuyerOrgMember.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), BuyerOrgApply.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""),
			com.aryn.cloud.order.api.entity.BuyerOrgInviteCode.class);
		buyerOrgMapper = mock(BuyerOrgMapper.class);
		buyerOrgMemberMapper = mock(BuyerOrgMemberMapper.class);
		buyerOrgApplyMapper = mock(BuyerOrgApplyMapper.class);
		buyerOrgInviteCodeMapper = mock(BuyerOrgInviteCodeMapper.class);
		service = new BuyerOrgServiceImpl(buyerOrgMapper, buyerOrgMemberMapper, buyerOrgApplyMapper,
			buyerOrgInviteCodeMapper, mock(RemoteMallUserService.class));
	}

	@Test
	@DisplayName("待审核主体 + 有效成员 + 可编辑：允许创建岸上车（先下单，后审首单）")
	void pendingOrgMemberCanCreateShoreCart() {
		BuyerOrg org = org(BuyerOrgConstants.AUDIT_PENDING, BuyerOrgConstants.STATUS_NORMAL);
		BuyerOrgMember member = member(BuyerOrgConstants.ROLE_BUYER, "1");
		when(buyerOrgMapper.selectOne(any())).thenReturn(org);
		when(buyerOrgMemberMapper.selectOne(any())).thenReturn(member);

		BuyerOrg result = service.requireUsableForCart("tenant-1", "user-1", "org-1");

		assertThat(result.getId()).isEqualTo("org-1");
		assertThat(result.getOrgName()).isEqualTo("宁波恒远机械");
	}

	@Test
	@DisplayName("非成员不能借别家主体建车")
	void nonMemberCannotCreateShoreCart() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_APPROVED,
			BuyerOrgConstants.STATUS_NORMAL));
		when(buyerOrgMemberMapper.selectOne(any())).thenReturn(null);

		ArynBusinessException error = catchThrowableOfType(
			() -> service.requireUsableForCart("tenant-1", "user-2", "org-1"), ArynBusinessException.class);
		assertThat(error).isNotNull();
		assertThat(error.getMsg()).contains("不是该采购主体成员");
	}

	@Test
	@DisplayName("已驳回主体不能建车，提示重新申请")
	void rejectedOrgCannotCreateShoreCart() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_REJECTED,
			BuyerOrgConstants.STATUS_NORMAL));
		when(buyerOrgMemberMapper.selectOne(any())).thenReturn(member(BuyerOrgConstants.ROLE_OWNER, "1"));

		ArynBusinessException error = catchThrowableOfType(
			() -> service.requireUsableForCart("tenant-1", "user-1", "org-1"), ArynBusinessException.class);
		assertThat(error).isNotNull();
		assertThat(error.getMsg()).contains("未通过审核");
	}

	@Test
	@DisplayName("主体不存在：建车直接拒绝，不能把订单挂到幽灵工厂")
	void missingOrgCannotCreateShoreCart() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(null);

		ArynBusinessException error = catchThrowableOfType(
			() -> service.requireUsableForCart("tenant-1", "user-1", "org-missing"), ArynBusinessException.class);
		assertThat(error).isNotNull();
		assertThat(error.getMsg()).contains("采购主体不存在");
	}

	@Test
	@DisplayName("只读采购员不能建车，但可作为订单确认人")
	void readOnlyMemberCannotCreateShoreCart() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_APPROVED,
			BuyerOrgConstants.STATUS_NORMAL));
		when(buyerOrgMemberMapper.selectOne(any())).thenReturn(member(BuyerOrgConstants.ROLE_BUYER, "0"));

		ArynBusinessException error = catchThrowableOfType(
			() -> service.requireUsableForCart("tenant-1", "user-1", "org-1"), ArynBusinessException.class);
		assertThat(error).isNotNull();
		assertThat(error.getMsg()).contains("无建车权限");
		assertThat(service.requireForOrder("tenant-1", "user-1", "org-1")).isNotNull();
	}

	@Test
	@DisplayName("订单落快照不要求可编辑：确认人可以是只读采购员，但仍须是有效成员")
	void orderSnapshotAllowsConfirmOnlyMember() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_PENDING,
			BuyerOrgConstants.STATUS_NORMAL));
		when(buyerOrgMemberMapper.selectOne(any())).thenReturn(member(BuyerOrgConstants.ROLE_BUYER, "0"));

		assertThat(service.requireForOrder("tenant-1", "user-1", "org-1")).isNotNull();
	}

	@Test
	@DisplayName("创建申请遇到同信用代码已存在：拒绝新建，引导搜索加入")
	void createApplyRejectsDuplicateCreditCode() {
		BuyerOrg existing = org(BuyerOrgConstants.AUDIT_APPROVED, BuyerOrgConstants.STATUS_NORMAL);
		existing.setId("org-existing");
		when(buyerOrgMapper.selectOne(any())).thenReturn(existing);

		BuyerOrgApplyDTO dto = new BuyerOrgApplyDTO();
		dto.setApplyType(BuyerOrgConstants.APPLY_TYPE_CREATE);
		dto.setOrgName("宁波恒远机械");
		dto.setCreditCode("91330200MA2H12345X");

		ArynBusinessException error = catchThrowableOfType(
			() -> service.apply("tenant-1", "user-1", dto), ArynBusinessException.class);
		assertThat(error).isNotNull();
		assertThat(error.getMsg()).contains("请搜索后申请加入");
		verify(buyerOrgMapper, never()).insert(any(BuyerOrg.class));
	}

	@Test
	@DisplayName("运营审核通过创建申请：主体转为已通过，申请人成为厂长成员")
	void auditCreateApplyPromotesOrgAndOwnerMember() {
		BuyerOrgApply apply = new BuyerOrgApply()
			.setId("apply-1")
			.setUserId("user-1")
			.setApplyType(BuyerOrgConstants.APPLY_TYPE_CREATE)
			.setStatus(BuyerOrgConstants.APPLY_STATUS_PENDING)
			.setMatchedOrgId("org-1")
			.setTenantId("tenant-1");
		BuyerOrg org = org(BuyerOrgConstants.AUDIT_PENDING, BuyerOrgConstants.STATUS_NORMAL);
		when(buyerOrgApplyMapper.selectOne(any())).thenReturn(apply);
		when(buyerOrgMapper.selectOne(any())).thenReturn(org);
		when(buyerOrgMemberMapper.selectOne(any())).thenReturn(null);
		when(buyerOrgMemberMapper.insert(any(BuyerOrgMember.class))).thenReturn(1);
		when(buyerOrgMapper.updateById(any(BuyerOrg.class))).thenReturn(1);
		when(buyerOrgApplyMapper.updateById(any(BuyerOrgApply.class))).thenReturn(1);

		BuyerOrgApplyAuditDTO audit = new BuyerOrgApplyAuditDTO();
		audit.setApproved(Boolean.TRUE);
		audit.setAuditRemark("资料齐全");
		service.adminAuditApply("tenant-1", "admin", "apply-1", audit);

		assertThat(org.getAuditStatus()).isEqualTo(BuyerOrgConstants.AUDIT_APPROVED);
		assertThat(apply.getStatus()).isEqualTo(BuyerOrgConstants.APPLY_STATUS_APPROVED);
		ArgumentCaptor<BuyerOrgMember> captor = ArgumentCaptor.forClass(BuyerOrgMember.class);
		verify(buyerOrgMemberMapper).insert(captor.capture());
		BuyerOrgMember created = captor.getValue();
		assertThat(created.getUserId()).isEqualTo("user-1");
		assertThat(created.getMemberRole()).isEqualTo(BuyerOrgConstants.ROLE_OWNER);
		assertThat(created.getStatus()).isEqualTo(BuyerOrgConstants.MEMBER_STATUS_NORMAL);
		assertThat(created.getBindSource()).isEqualTo(BuyerOrgConstants.BIND_SOURCE_APPLY_AUDIT);
	}

	@Test
	@DisplayName("运营代建主体：直接落已通过/正常，不产生待审核申请")
	void adminCreateMarksOrgApproved() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(null);
		when(buyerOrgMapper.insert(any(BuyerOrg.class))).thenReturn(1);

		BuyerOrgSaveDTO dto = new BuyerOrgSaveDTO();
		dto.setOrgName("宁波恒远机械");
		dto.setOrgType(BuyerOrgConstants.ORG_TYPE_FACTORY);
		dto.setCreditCode("91330200MA2H12345X");

		BuyerOrg created = service.adminCreate("tenant-1", "admin", dto);

		assertThat(created.getAuditStatus()).isEqualTo(BuyerOrgConstants.AUDIT_APPROVED);
		assertThat(created.getStatus()).isEqualTo(BuyerOrgConstants.STATUS_NORMAL);
		assertThat(created.getOrgCode()).startsWith("BO");
		ArgumentCaptor<BuyerOrg> captor = ArgumentCaptor.forClass(BuyerOrg.class);
		verify(buyerOrgMapper).insert(captor.capture());
		assertThat(captor.getValue().getOrgName()).isEqualTo("宁波恒远机械");
	}

	@Test
	@DisplayName("运营修改主体：信用代码撞到别家主体时拒绝")
	void adminUpdateRejectsDuplicateCreditCode() {
		BuyerOrg target = org(BuyerOrgConstants.AUDIT_APPROVED, BuyerOrgConstants.STATUS_NORMAL);
		BuyerOrg other = org(BuyerOrgConstants.AUDIT_APPROVED, BuyerOrgConstants.STATUS_NORMAL);
		other.setId("org-other");
		when(buyerOrgMapper.selectOne(any())).thenReturn(target, other);

		BuyerOrgSaveDTO dto = new BuyerOrgSaveDTO();
		dto.setOrgName("宁波恒远机械");
		dto.setCreditCode("91330200MA2H12345X");

		ArynBusinessException error = catchThrowableOfType(
			() -> service.adminUpdate("tenant-1", "admin", "org-1", dto), ArynBusinessException.class);
		assertThat(error).isNotNull();
		assertThat(error.getMsg()).contains("已被其他主体使用");
		verify(buyerOrgMapper, never()).updateById(any(BuyerOrg.class));
	}

	private BuyerOrg org(String auditStatus, String status) {
		BuyerOrg org = new BuyerOrg();
		org.setId("org-1");
		org.setTenantId("tenant-1");
		org.setOrgName("宁波恒远机械");
		org.setOrgType(BuyerOrgConstants.ORG_TYPE_FACTORY);
		org.setCreditCode("91330200MA2H12345X");
		org.setAuditStatus(auditStatus);
		org.setStatus(status);
		org.setOwnerUserId("user-1");
		return org;
	}

	private BuyerOrgMember member(String role, String canEdit) {
		BuyerOrgMember member = new BuyerOrgMember();
		member.setId("member-1");
		member.setTenantId("tenant-1");
		member.setBuyerOrgId("org-1");
		member.setUserId("user-1");
		member.setMemberRole(role);
		member.setCanEdit(canEdit);
		member.setCanConfirm(canEdit);
		member.setStatus(BuyerOrgConstants.MEMBER_STATUS_NORMAL);
		return member;
	}

}
