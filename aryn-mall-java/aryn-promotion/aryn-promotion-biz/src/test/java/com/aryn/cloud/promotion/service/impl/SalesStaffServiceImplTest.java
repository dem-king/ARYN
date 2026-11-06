package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesStaffResignDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffSaveDTO;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.vo.SalesStaffVO;
import com.aryn.cloud.promotion.mapper.SalesCustomerBindMapper;
import com.aryn.cloud.promotion.mapper.SalesStaffMapper;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售离职交接单测：客户批量转交、认领作废、离职状态
 */
class SalesStaffServiceImplTest {

	private SalesCustomerBindMapper bindMapper;

	private SalesBindWriter bindWriter;

	private SalesStaffMapper staffMapper;

	private RemoteSysUserService remoteSysUserService;

	private RemoteMallUserService remoteMallUserService;

	private SalesUserEnricher salesUserEnricher;

	private SalesStaffServiceImpl service;

	@BeforeEach
	void setUp() {
		bindMapper = mock(SalesCustomerBindMapper.class);
		bindWriter = mock(SalesBindWriter.class);
		staffMapper = mock(SalesStaffMapper.class);
		remoteSysUserService = mock(RemoteSysUserService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		salesUserEnricher = mock(SalesUserEnricher.class);
		service = spy(new SalesStaffServiceImpl(remoteSysUserService,
				remoteMallUserService, bindMapper, bindWriter, salesUserEnricher));
		ReflectionTestUtils.setField(service, "baseMapper", staffMapper);
	}

	@Test
	void resignTransfersCustomersAndInvalidatesClaims() {
		SalesStaff staff = staff("staff-1", "0");
		SalesStaff receive = staff("staff-2", "0");
		doReturn(staff).when(service).getById("staff-1");
		doReturn(receive).when(service).getById("staff-2");
		doReturn(true).when(service).updateById(any(SalesStaff.class));
		SalesCustomerBind bind = new SalesCustomerBind().setCustomerUserId("buyer-1").setSalesStaffId("staff-1");
		when(bindMapper.selectList(any())).thenReturn(List.of(bind));
		when(bindWriter.invalidatePendingClaimsByStaff("staff-1")).thenReturn(2);
		SalesStaffResignDTO dto = new SalesStaffResignDTO();
		dto.setStaffId("staff-1");
		dto.setReceiveStaffId("staff-2");
		dto.setReason("转岗");

		assertThat(service.resign(dto)).isTrue();

		verify(bindWriter).transferBind(bind, staff, receive, "转岗");
		verify(bindWriter).invalidatePendingClaimsByStaff("staff-1");
		assertThat(staff.getStatus()).isEqualTo("1");
		assertThat(staff.getLeaveTime()).isNotNull();
	}

	@Test
	void resignRequiresActiveReceiver() {
		doReturn(staff("staff-1", "0")).when(service).getById("staff-1");
		doReturn(staff("staff-2", "1")).when(service).getById("staff-2");
		SalesStaffResignDTO dto = new SalesStaffResignDTO();
		dto.setStaffId("staff-1");
		dto.setReceiveStaffId("staff-2");

		assertThatThrownBy(() -> service.resign(dto)).isInstanceOf(ArynBusinessException.class);
		verify(bindWriter, never()).invalidatePendingClaimsByStaff(any());
	}

	@Test
	void resignRejectsSelfAsReceiver() {
		doReturn(staff("staff-1", "0")).when(service).getById("staff-1");
		SalesStaffResignDTO dto = new SalesStaffResignDTO();
		dto.setStaffId("staff-1");
		dto.setReceiveStaffId("staff-1");

		assertThatThrownBy(() -> service.resign(dto)).isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void changeStatusRejectsDirectResignation() {
		doReturn(staff("staff-1", "0")).when(service).getById("staff-1");
		var dto = new com.aryn.cloud.promotion.api.dto.SalesStaffStatusDTO();
		dto.setId("staff-1");
		dto.setStatus("1");

		assertThatThrownBy(() -> service.changeStatus(dto)).isInstanceOf(ArynBusinessException.class)
			.satisfies(e -> assertThat(((ArynBusinessException) e).getMsg()).contains("离职交接"));
	}

	@Test
	void addStaffRejectsCommissionRateAboveOne() {
		var dto = new com.aryn.cloud.promotion.api.dto.SalesStaffSaveDTO();
		dto.setSysUserId("sys-1");
		dto.setMemberUserId("member-1");
		dto.setCommissionRate(new java.math.BigDecimal("1.20"));

		assertThatThrownBy(() -> service.addStaff(dto)).isInstanceOf(ArynBusinessException.class)
			.satisfies(e -> assertThat(((ArynBusinessException) e).getMsg()).contains("提成比例"));
		verify(staffMapper, never()).insert(any(SalesStaff.class));
	}

	@Test
	void addStaffSnapshotsEmployeeDept() {
		// 远程员工查询必须带回部门名称（deptName 是 sys_dept 关联字段），
		// 否则登记出来的销售员「所属部门」恒为空。
		SysUser sysUser = new SysUser();
		sysUser.setId("sys-1");
		sysUser.setNickname("司机");
		sysUser.setPhone("13200009999");
		sysUser.setDeptId("dept-sales");
		sysUser.setDeptName("销售部");
		when(remoteSysUserService.getUserById("sys-1")).thenReturn(sysUser);
		UserInfoVO member = new UserInfoVO();
		member.setId("member-1");
		when(remoteMallUserService.getUserById("member-1")).thenReturn(member);
		when(staffMapper.selectCount(any())).thenReturn(0L);
		doReturn(true).when(service).save(any(SalesStaff.class));
		SalesStaffSaveDTO dto = new SalesStaffSaveDTO();
		dto.setSysUserId("sys-1");
		dto.setMemberUserId("member-1");

		assertThat(service.addStaff(dto)).isTrue();

		ArgumentCaptor<SalesStaff> captor = ArgumentCaptor.forClass(SalesStaff.class);
		verify(service).save(captor.capture());
		assertThat(captor.getValue().getDeptId()).isEqualTo("dept-sales");
		assertThat(captor.getValue().getDeptName()).isEqualTo("销售部");
	}

	@Test
	void selectAdminPageRepairsBlankDeptSnapshot() {
		// 历史数据 dept_name 为空：列表读取时按员工账号当前部门补齐并回写，避免「所属部门」长期空白
		SalesStaff legacy = staff("staff-1", "0").setSysUserId("sys-1").setDeptId("dept-old");
		Page<SalesStaff> staffPage = new Page<>(1, 10);
		staffPage.setRecords(List.of(legacy));
		staffPage.setTotal(1);
		doReturn(staffPage).when(service).page(any(), any());
		SysUser sysUser = new SysUser();
		sysUser.setId("sys-1");
		sysUser.setDeptId("dept-sales");
		sysUser.setDeptName("销售部");
		when(remoteSysUserService.getUserInfoByIds(List.of("sys-1"))).thenReturn(List.of(sysUser));
		doReturn(true).when(service).updateById(any(SalesStaff.class));

		IPage<SalesStaffVO> result = service.selectAdminPage(new Page<>(1, 10), new SalesStaff());

		assertThat(result.getRecords()).hasSize(1);
		assertThat(result.getRecords().get(0).getDeptName()).isEqualTo("销售部");
		assertThat(legacy.getDeptId()).isEqualTo("dept-sales");
		verify(service).updateById(legacy);
	}

	@Test
	void selectAdminPageSkipsRepairWhenDeptSnapshotPresent() {
		SalesStaff current = staff("staff-1", "0").setSysUserId("sys-1").setDeptId("dept-sales")
			.setDeptName("销售部");
		Page<SalesStaff> staffPage = new Page<>(1, 10);
		staffPage.setRecords(List.of(current));
		staffPage.setTotal(1);
		doReturn(staffPage).when(service).page(any(), any());

		service.selectAdminPage(new Page<>(1, 10), new SalesStaff());

		verify(remoteSysUserService, never()).getUserInfoByIds(any());
		verify(service, never()).updateById(any(SalesStaff.class));
	}

	@Test
	void selectAdminPageFillsMemberNicknameAndAvatar() {
		// 列表不直接展示 19 位会员ID：回填会员昵称/头像，前端按「头像 + 昵称 + ID 小字」展示
		SalesStaff current = staff("staff-1", "0").setSysUserId("sys-1").setDeptId("dept-sales")
			.setDeptName("销售部").setMemberUserId("member-9");
		Page<SalesStaff> staffPage = new Page<>(1, 10);
		staffPage.setRecords(List.of(current));
		staffPage.setTotal(1);
		doReturn(staffPage).when(service).page(any(), any());
		UserInfoVO member = new UserInfoVO();
		member.setId("member-9");
		member.setNickname("小程序司机");
		member.setAvatarUrl("https://cdn.example.com/avatar.png");
		when(salesUserEnricher.getUserMap(List.of("member-9"))).thenReturn(Map.of("member-9", member));

		IPage<SalesStaffVO> result = service.selectAdminPage(new Page<>(1, 10), new SalesStaff());

		assertThat(result.getRecords().get(0).getMemberNickname()).isEqualTo("小程序司机");
		assertThat(result.getRecords().get(0).getMemberAvatar()).isEqualTo("https://cdn.example.com/avatar.png");
	}

	private SalesStaff staff(String id, String status) {
		return new SalesStaff().setId(id).setStaffName(id).setMemberUserId("member-" + id).setStatus(status);
	}

}
