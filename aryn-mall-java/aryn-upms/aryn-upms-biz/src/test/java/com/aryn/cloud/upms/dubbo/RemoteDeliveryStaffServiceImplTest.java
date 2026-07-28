package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.DeliveryStaffQuery;
import com.aryn.cloud.upms.api.vo.DeliveryStaffVO;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteDeliveryStaffServiceImplTest {

	@Mock
	private SysUserMapper sysUserMapper;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void returnsCurrentTenantEligibleStaffWithStableCursor() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		DeliveryStaffQuery query = new DeliveryStaffQuery();
		query.setTenantId("tenant-1");
		query.setKeyword("张");
		query.setLimit(1);
		when(sysUserMapper.selectDeliveryStaff(query, 2)).thenReturn(List.of(staff("10"), staff("11")));

		var page = new RemoteDeliveryStaffServiceImpl(sysUserMapper).queryCandidates(query);

		assertThat(page.getRecords()).extracting(DeliveryStaffVO::getId).containsExactly("10");
		assertThat(page.getNextCursor()).isEqualTo("10");
		assertThat(page.isHasMore()).isTrue();
	}

	@Test
	void rejectsCrossTenantCandidateQuery() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		DeliveryStaffQuery query = new DeliveryStaffQuery();
		query.setTenantId("tenant-2");

		assertThatThrownBy(() -> new RemoteDeliveryStaffServiceImpl(sysUserMapper).queryCandidates(query))
			.isInstanceOf(ArynBusinessException.class);
		verify(sysUserMapper, never()).selectDeliveryStaff(query, 501);
	}

	@Test
	void eligibilityRejectsEmptyDisabledAndCrossTenantStaff() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		RemoteDeliveryStaffServiceImpl service = new RemoteDeliveryStaffServiceImpl(sysUserMapper);
		when(sysUserMapper.countEligibleDeliveryStaff("tenant-1", "disabled-user")).thenReturn(0);

		assertThat(service.isEligible("tenant-1", null)).isFalse();
		assertThat(service.isEligible("tenant-1", " ")).isFalse();
		assertThat(service.isEligible("tenant-1", "disabled-user")).isFalse();
		assertThatThrownBy(() -> service.isEligible("tenant-2", "staff-1"))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void candidateViewContainsOnlyAssignmentSnapshotFields() {
		assertThat(DeliveryStaffVO.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
			.containsExactlyInAnyOrder("serialVersionUID", "id", "nickname", "phone", "avatar", "deptId")
			.doesNotContain("password", "permissions", "roles", "email");
	}

	private DeliveryStaffVO staff(String id) {
		DeliveryStaffVO staff = new DeliveryStaffVO();
		staff.setId(id);
		return staff;
	}

}
