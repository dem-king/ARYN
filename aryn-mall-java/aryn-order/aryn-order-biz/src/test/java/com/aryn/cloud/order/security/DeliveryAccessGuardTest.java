
package com.aryn.cloud.order.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.constant.DeliveryAuthConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import cn.dev33.satoken.session.SaSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配送访问守卫测试：主体/租户/绑定一致性与资格缓存跨实例失效
 */
class DeliveryAccessGuardTest {

	private IDeliveryStaffService deliveryStaffService;

	private IDeliveryAccountBindingService deliveryAccountBindingService;

	private RemoteSysUserService remoteSysUserService;

	@SuppressWarnings("unchecked")
	private final ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);

	private StringRedisTemplate stringRedisTemplate;

	private ValueOperations<String, String> valueOperations;

	private DeliveryAccessGuard guard;

	private MockedStatic<SecurityUtils> securityUtils;

	private MockedStatic<StpUtil> stpUtil;

	private SaSession tokenSession;

	private ArynUser tobUser;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		deliveryStaffService = mock(IDeliveryStaffService.class);
		deliveryAccountBindingService = mock(IDeliveryAccountBindingService.class);
		remoteSysUserService = mock(RemoteSysUserService.class);
		stringRedisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisProvider.getIfAvailable()).thenReturn(stringRedisTemplate);
		guard = new DeliveryAccessGuard(deliveryStaffService, deliveryAccountBindingService, remoteSysUserService,
				redisProvider);
		securityUtils = mockStatic(SecurityUtils.class);
		stpUtil = mockStatic(StpUtil.class);
		// 默认：独立密码登录会话（无商城绑定来源标记）
		tokenSession = mock(SaSession.class);
		stpUtil.when(StpUtil::getTokenSession).thenReturn(tokenSession);
		when(tokenSession.get(DeliveryAuthConstants.MALL_BINDING_SOURCE)).thenReturn(null);
		tobUser = new ArynUser();
		tobUser.setUserId("sys-user-1");
		tobUser.setTenantId("tenant-1");
		securityUtils.when(() -> SecurityUtils.requireUser(eq(DeviceTypeEnum.TOB))).thenReturn(tobUser);

		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("sys-user-1");
		staff.setTenantId("tenant-1");
		staff.setStaffName("张三");
		when(deliveryStaffService.getByUserId("sys-user-1")).thenReturn(staff);
		givenQualifiedSysUser("sys-user-1", "tenant-1");
	}

	@AfterEach
	void tearDown() {
		securityUtils.close();
		stpUtil.close();
	}

	private void givenQualifiedSysUser(String sysUserId, String tenantId) {
		SysUser sysUser = new SysUser();
		sysUser.setId(sysUserId);
		sysUser.setStatus("0");
		sysUser.setTenantId(tenantId);
		sysUser.setPermissions(Set.of("delivery:execute"));
		when(remoteSysUserService.getUserById(sysUserId)).thenReturn(sysUser);
	}

	@Test
	void tocTokenCannotAccessDeliveryEndpoints() {
		securityUtils.when(() -> SecurityUtils.requireUser(eq(DeviceTypeEnum.TOB)))
			.thenThrow(new ArynBusinessException("当前登录端无权访问该资源"));
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "当前登录端无权访问该资源");
	}

	@Test
	void missingSessionTenantIsRejected() {
		tobUser.setTenantId(null);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
	}

	@Test
	void missingStaffIsUnauthorized() {
		when(deliveryStaffService.getByUserId("sys-user-1")).thenReturn(null);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 401);
	}

	@Test
	void staffOwnedByAnotherSubjectIsRejected() {
		tobUser.setUserId("sys-user-2");
		// staff 查询按登录主体执行，这里模拟查询侧异常返回了他人资料
		DeliveryStaff other = new DeliveryStaff();
		other.setId("staff-1");
		other.setUserId("sys-user-1");
		other.setTenantId("tenant-1");
		when(deliveryStaffService.getByUserId("sys-user-2")).thenReturn(other);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
	}

	@Test
	void crossTenantStaffIsRejected() {
		tobUser.setTenantId("tenant-2");
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
	}

	@Test
	void crossTenantSysUserQualificationIsRejected() {
		givenQualifiedSysUser("sys-user-1", "tenant-2");
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
	}

	@Test
	void revokedPermissionIsRejected() {
		SysUser sysUser = new SysUser();
		sysUser.setId("sys-user-1");
		sysUser.setStatus("0");
		sysUser.setTenantId("tenant-1");
		sysUser.setPermissions(Set.of("other:perm"));
		when(remoteSysUserService.getUserById("sys-user-1")).thenReturn(sysUser);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
	}

	@Test
	void remoteQualificationFailureFailsClosed() {
		when(remoteSysUserService.getUserById("sys-user-1")).thenThrow(new RuntimeException("rpc down"));
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
	}

	@Test
	void mallExchangeSessionWithoutBindingIsRejected() {
		when(tokenSession.get(DeliveryAuthConstants.MALL_BINDING_SOURCE))
			.thenReturn(DeliveryAuthConstants.MALL_BINDING_SOURCE_VALUE);
		when(deliveryAccountBindingService.getActiveBySysUser("sys-user-1")).thenReturn(null);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 401);
	}

	@Test
	void mallExchangeSessionWithInconsistentBindingIsRejected() {
		when(tokenSession.get(DeliveryAuthConstants.MALL_BINDING_SOURCE))
			.thenReturn(DeliveryAuthConstants.MALL_BINDING_SOURCE_VALUE);
		DeliveryAccountBinding binding = new DeliveryAccountBinding();
		binding.setDeliveryStaffId("staff-other");
		binding.setSysUserId("sys-user-1");
		binding.setTenantId("tenant-1");
		when(deliveryAccountBindingService.getActiveBySysUser("sys-user-1")).thenReturn(binding);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 401);
	}

	@Test
	void mallExchangeSessionWithConsistentBindingPasses() {
		when(tokenSession.get(DeliveryAuthConstants.MALL_BINDING_SOURCE))
			.thenReturn(DeliveryAuthConstants.MALL_BINDING_SOURCE_VALUE);
		DeliveryAccountBinding binding = new DeliveryAccountBinding();
		binding.setDeliveryStaffId("staff-1");
		binding.setSysUserId("sys-user-1");
		binding.setTenantId("tenant-1");
		when(deliveryAccountBindingService.getActiveBySysUser("sys-user-1")).thenReturn(binding);
		DeliveryStaff staff = guard.requireCurrentStaff();
		assertThat(staff.getId()).isEqualTo("staff-1");
	}

	@Test
	void passwordLoginSessionPassesWithoutBinding() {
		// 独立密码登录路径：无商城绑定也能进入工作台，资格校验照常执行
		DeliveryStaff staff = guard.requireCurrentStaff();
		assertThat(staff.getId()).isEqualTo("staff-1");
		verify(deliveryAccountBindingService, times(0)).getActiveBySysUser(anyString());
	}

	@Test
	void revocationVersionOnOtherInstanceInvalidatesPositiveCache() {
		// 第一次校验通过并缓存（版本 0）
		assertThat(guard.requireCurrentStaff().getId()).isEqualTo("staff-1");
		// 另一实例撤销资格：版本号推进 + 权限被回收
		when(valueOperations.get("delivery:qual:ver:sys-user-1")).thenReturn("1000");
		SysUser sysUser = new SysUser();
		sysUser.setId("sys-user-1");
		sysUser.setStatus("0");
		sysUser.setTenantId("tenant-1");
		sysUser.setPermissions(Set.of("other:perm"));
		when(remoteSysUserService.getUserById("sys-user-1")).thenReturn(sysUser);
		assertThatThrownBy(() -> guard.requireCurrentStaff())
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("code", 403);
		// 缓存被跨实例失效，资格校验确实重新执行了两次
		verify(remoteSysUserService, times(2)).getUserById("sys-user-1");
	}

	@Test
	void redisOutageDisablesPositiveCache() {
		// 第一次校验通过并缓存
		assertThat(guard.requireCurrentStaff().getId()).isEqualTo("staff-1");
		// Redis 故障：正向缓存不可信，必须重新远程校验（fail-closed）
		when(valueOperations.get("delivery:qual:ver:sys-user-1")).thenThrow(new RuntimeException("redis down"));
		assertThat(guard.requireCurrentStaff().getId()).isEqualTo("staff-1");
		verify(remoteSysUserService, times(2)).getUserById("sys-user-1");
	}

	@Test
	void invalidateWritesRevocationVersion() {
		guard.invalidateQualificationCache("sys-user-1");
		verify(valueOperations).set(eq("delivery:qual:ver:sys-user-1"), anyString(), any(java.time.Duration.class));
	}

}
