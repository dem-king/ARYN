
package com.aryn.cloud.auth.service;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.DeliveryEligibilityDTO;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 配送资格与身份换取状态矩阵测试（含同租户/跨租户/租户缺失场景）
 */
class DeliveryAuthServiceTest {

	private RemoteDeliveryAccountService remoteDeliveryAccountService;

	private RemoteSysUserService remoteSysUserService;

	private DeliveryAuthService service;

	private MockedStatic<SecurityUtils> securityUtils;

	private ArynUser tocUser;

	@BeforeEach
	void setUp() {
		remoteDeliveryAccountService = mock(RemoteDeliveryAccountService.class);
		remoteSysUserService = mock(RemoteSysUserService.class);
		service = new DeliveryAuthService(remoteDeliveryAccountService, remoteSysUserService);
		securityUtils = mockStatic(SecurityUtils.class);
		tocUser = new ArynUser();
		tocUser.setUserId("mall-user-1");
		tocUser.setTenantId("tenant-1");
		securityUtils.when(() -> SecurityUtils.requireUser(eq(DeviceTypeEnum.TOC))).thenReturn(tocUser);
	}

	@AfterEach
	void tearDown() {
		securityUtils.close();
	}

	private void givenBinding(String bindingStatus) {
		givenBinding(bindingStatus, "tenant-1");
	}

	private void givenBinding(String bindingStatus, String bindingTenantId) {
		DeliveryEligibilityDTO dto = new DeliveryEligibilityDTO();
		dto.setBindingStatus(bindingStatus);
		if (DeliveryEligibilityDTO.BINDING_BOUND.equals(bindingStatus)
				|| DeliveryEligibilityDTO.BINDING_STAFF_INVALID.equals(bindingStatus)) {
			dto.setTenantId(bindingTenantId);
			dto.setSysUserId("sys-user-1");
			dto.setDeliveryStaffId("staff-1");
		}
		if (DeliveryEligibilityDTO.BINDING_BOUND.equals(bindingStatus)) {
			dto.setStaffName("张三");
			dto.setPendingTaskCount(3);
		}
		when(remoteDeliveryAccountService.getEligibilityByMallUser("mall-user-1")).thenReturn(dto);
	}

	private SysUser givenSysUser(String status, Set<String> permissions) {
		return givenSysUser(status, permissions, "tenant-1");
	}

	private SysUser givenSysUser(String status, Set<String> permissions, String tenantId) {
		SysUser sysUser = new SysUser();
		sysUser.setId("sys-user-1");
		sysUser.setStatus(status);
		sysUser.setNickname("张三");
		sysUser.setTenantId(tenantId);
		sysUser.setPermissions(permissions);
		when(remoteSysUserService.getUserById("sys-user-1")).thenReturn(sysUser);
		return sysUser;
	}

	@Test
	void unboundHidesEntrance() {
		givenBinding(DeliveryEligibilityDTO.BINDING_UNBOUND);
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_UNBOUND);
		assertThat(vo.isEligible()).isFalse();
	}

	@Test
	void deletedOrInconsistentStaffIsStaffInvalid() {
		givenBinding(DeliveryEligibilityDTO.BINDING_STAFF_INVALID);
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_STAFF_INVALID);
		assertThat(vo.isEligible()).isFalse();
	}

	@Test
	void disabledAccountReturnsAccountDisabled() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("1", Set.of("delivery:execute"));
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_ACCOUNT_DISABLED);
		assertThat(vo.isEligible()).isFalse();
	}

	@Test
	void missingPermissionReturnsPermissionMissing() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("0", Set.of("other:perm"));
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_PERMISSION_MISSING);
		assertThat(vo.isEligible()).isFalse();
	}

	@Test
	void activeWhenBindingStaffAccountAndPermissionAllValid() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("0", Set.of("delivery:execute"));
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_ACTIVE);
		assertThat(vo.isEligible()).isTrue();
		assertThat(vo.getPendingTaskCount()).isEqualTo(3);
		assertThat(vo.getStaffName()).isEqualTo("张三");
	}

	@Test
	void wildcardPermissionGrantsAccess() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("0", Set.of("*"));
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_ACTIVE);
	}

	@Test
	void eligibilityVoExposesNoInternalIds() {
		// 资格响应禁止携带 sysUserId/staffId 等内部 ID（R-02）
		Set<String> fieldNames = new HashSet<>();
		for (Field field : DeliveryAuthService.DeliveryEligibilityVO.class.getDeclaredFields()) {
			fieldNames.add(field.getName());
		}
		assertThat(fieldNames).doesNotContain("sysUserId", "staffId", "userId", "id");
		assertThat(fieldNames).contains("eligible", "status", "staffName", "pendingTaskCount");
	}

	@Test
	void tenantInvisibleAccountCannotBeReadOrExchanged() {
		// 跨租户员工账号被租户隔离过滤为不可见 → ACCOUNT_DISABLED，且换取拒绝
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		when(remoteSysUserService.getUserById("sys-user-1")).thenReturn(null);
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_ACCOUNT_DISABLED);
		assertThatThrownBy(() -> service.exchange()).isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void crossTenantSysUserIsAccountDisabledAndExchangeRejected() {
		// 显式校验：员工账号租户与商城用户租户不一致时按账号停用处理，不泄露跨租户细节（R-03）
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("0", Set.of("delivery:execute"), "tenant-2");
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_ACCOUNT_DISABLED);
		assertThat(vo.isEligible()).isFalse();
		assertThatThrownBy(() -> service.exchange())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "配送账号已停用");
	}

	@Test
	void crossTenantBindingIsTreatedAsUnbound() {
		// 订单域绑定租户与商城用户租户不一致时按未绑定处理（R-03）
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND, "tenant-2");
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_UNBOUND);
		assertThat(vo.isEligible()).isFalse();
		assertThatThrownBy(() -> service.exchange())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "当前账号未开通配送权限");
	}

	@Test
	void missingBindingTenantIsRejected() {
		// 订单域无法确认绑定租户时拒绝换取（R-03：任一侧无法确认租户都拒绝）
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND, "");
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_UNBOUND);
		assertThatThrownBy(() -> service.exchange()).isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void missingMallTenantHidesEntrance() {
		// 商城会话租户缺失时按未绑定处理，不展示入口（R-03）
		tocUser.setTenantId("");
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		DeliveryAuthService.DeliveryEligibilityVO vo = service.eligibility();
		assertThat(vo.getStatus()).isEqualTo(DeliveryAuthService.STATUS_UNBOUND);
		assertThat(vo.isEligible()).isFalse();
		assertThatThrownBy(() -> service.exchange()).isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void exchangeIssuesTokenOnlyForActiveStatus() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("0", Set.of("delivery:execute"));
		securityUtils.when(() -> SecurityUtils.loginByDevice(any(ArynUser.class), eq(DeviceTypeEnum.TOB)))
			.thenAnswer(invocation -> null);
		try (MockedStatic<cn.dev33.satoken.stp.StpUtil> stp = mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
			cn.dev33.satoken.session.SaSession tokenSession = mock(cn.dev33.satoken.session.SaSession.class);
			stp.when(cn.dev33.satoken.stp.StpUtil::getTokenSession).thenReturn(tokenSession);
			cn.dev33.satoken.stp.SaTokenInfo tokenInfo = new cn.dev33.satoken.stp.SaTokenInfo();
			tokenInfo.setTokenValue("delivery-token");
			tokenInfo.setTokenTimeout(7200L);
			stp.when(cn.dev33.satoken.stp.StpUtil::getTokenInfo).thenReturn(tokenInfo);
			DeliveryAuthService.DeliveryExchangeVO result = service.exchange();
			assertThat(result.getTokenValue()).isEqualTo("delivery-token");
			assertThat(result.getStaff().getStaffName()).isEqualTo("张三");
		}
	}

	@Test
	void exchangeRejectsWhenNotBound() {
		givenBinding(DeliveryEligibilityDTO.BINDING_UNBOUND);
		assertThatThrownBy(() -> service.exchange())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "当前账号未开通配送权限");
	}

	@Test
	void exchangeRejectsWhenPermissionMissing() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("0", Set.of("other:perm"));
		assertThatThrownBy(() -> service.exchange())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "配送权限已暂停，请联系管理员");
	}

	@Test
	void exchangeRejectsWhenAccountDisabled() {
		givenBinding(DeliveryEligibilityDTO.BINDING_BOUND);
		givenSysUser("1", Set.of("delivery:execute"));
		assertThatThrownBy(() -> service.exchange())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "配送账号已停用");
	}

}
