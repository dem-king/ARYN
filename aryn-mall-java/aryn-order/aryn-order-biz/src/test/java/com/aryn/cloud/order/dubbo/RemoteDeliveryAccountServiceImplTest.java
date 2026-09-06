
package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.dto.DeliveryEligibilityDTO;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单域资格原始信息一致性测试
 */
class RemoteDeliveryAccountServiceImplTest {

	private IDeliveryAccountBindingService bindingService;

	private IDeliveryStaffService deliveryStaffService;

	private DeliveryTaskMapper deliveryTaskMapper;

	private RemoteDeliveryAccountServiceImpl service;

	@BeforeEach
	void setUp() {
		bindingService = mock(IDeliveryAccountBindingService.class);
		deliveryStaffService = mock(IDeliveryStaffService.class);
		deliveryTaskMapper = mock(DeliveryTaskMapper.class);
		service = new RemoteDeliveryAccountServiceImpl(bindingService, deliveryStaffService, deliveryTaskMapper);
	}

	private DeliveryAccountBinding binding(String sysUserId, String staffId) {
		DeliveryAccountBinding binding = new DeliveryAccountBinding();
		binding.setId("binding-1");
		binding.setMallUserId("mall-user-1");
		binding.setSysUserId(sysUserId);
		binding.setDeliveryStaffId(staffId);
		binding.setStatus(DeliveryAccountBinding.STATUS_BOUND);
		return binding;
	}

	@Test
	void blankMallUserIsUnbound() {
		DeliveryEligibilityDTO dto = service.getEligibilityByMallUser("");
		assertThat(dto.getBindingStatus()).isEqualTo(DeliveryEligibilityDTO.BINDING_UNBOUND);
	}

	@Test
	void noActiveBindingIsUnbound() {
		when(bindingService.getActiveByMallUser("mall-user-1")).thenReturn(null);
		DeliveryEligibilityDTO dto = service.getEligibilityByMallUser("mall-user-1");
		assertThat(dto.getBindingStatus()).isEqualTo(DeliveryEligibilityDTO.BINDING_UNBOUND);
	}

	@Test
	void missingStaffIsStaffInvalid() {
		when(bindingService.getActiveByMallUser("mall-user-1")).thenReturn(binding("sys-user-1", "staff-1"));
		when(deliveryStaffService.getById("staff-1")).thenReturn(null);
		DeliveryEligibilityDTO dto = service.getEligibilityByMallUser("mall-user-1");
		assertThat(dto.getBindingStatus()).isEqualTo(DeliveryEligibilityDTO.BINDING_STAFF_INVALID);
	}

	@Test
	void inconsistentStaffOwnershipIsStaffInvalid() {
		// staff.userId 与绑定归属不一致（如资料被改绑员工账号）时必须失效
		when(bindingService.getActiveByMallUser("mall-user-1")).thenReturn(binding("sys-user-1", "staff-1"));
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("sys-user-other");
		staff.setStaffName("李四");
		when(deliveryStaffService.getById("staff-1")).thenReturn(staff);
		DeliveryEligibilityDTO dto = service.getEligibilityByMallUser("mall-user-1");
		assertThat(dto.getBindingStatus()).isEqualTo(DeliveryEligibilityDTO.BINDING_STAFF_INVALID);
	}

	@Test
	void consistentBindingReturnsStaffInfoAndPendingCount() {
		when(bindingService.getActiveByMallUser("mall-user-1")).thenReturn(binding("sys-user-1", "staff-1"));
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("sys-user-1");
		staff.setStaffName("张三");
		staff.setStatus("1");
		when(deliveryStaffService.getById("staff-1")).thenReturn(staff);
		when(deliveryTaskMapper.selectMaps(any())).thenReturn(List.of(Map.of("cnt", 3L)));
		DeliveryEligibilityDTO dto = service.getEligibilityByMallUser("mall-user-1");
		assertThat(dto.getBindingStatus()).isEqualTo(DeliveryEligibilityDTO.BINDING_BOUND);
		assertThat(dto.getStaffName()).isEqualTo("张三");
		assertThat(dto.getStaffStatus()).isEqualTo("1");
		assertThat(dto.getPendingTaskCount()).isEqualTo(3);
		assertThat(dto.getSysUserId()).isEqualTo("sys-user-1");
	}

	@Test
	void blankSysUserHasNoActiveDeliveryStaff() {
		assertThat(service.hasActiveDeliveryStaff("")).isFalse();
		assertThat(service.hasActiveDeliveryStaff(null)).isFalse();
	}

	@Test
	void activeDeliveryStaffIsReportedForDeleteProtection() {
		// 员工账号仍关联配送员资料时，删除员工账号的保护检查必须返回 true
		when(deliveryStaffService.getByUserId("sys-user-1")).thenReturn(new DeliveryStaff());
		assertThat(service.hasActiveDeliveryStaff("sys-user-1")).isTrue();
	}

	@Test
	void cleanedUpDeliveryStaffAllowsDelete() {
		when(deliveryStaffService.getByUserId("sys-user-1")).thenReturn(null);
		assertThat(service.hasActiveDeliveryStaff("sys-user-1")).isFalse();
	}

}
