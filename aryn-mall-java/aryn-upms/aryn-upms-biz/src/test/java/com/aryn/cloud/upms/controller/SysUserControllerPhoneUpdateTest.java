package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.dto.SysUserPhoneUpdateDTO;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.PutMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员修改员工手机号契约：
 * <ol>
 *   <li>独立权限 {@code upms:sysuser:phone:edit} + 操作日志，日志不记录完整号码；</li>
 *   <li>格式、同号、跨租户唯一性依次校验，只更新 id + phone（脱敏值永不回写）；</li>
 *   <li>唯一键冲突兜底为友好文案；</li>
 *   <li>更新成功后 best-effort 同步配送员资料联系电话，同步失败不影响返回。</li>
 * </ol>
 */
class SysUserControllerPhoneUpdateTest {

	private static final String OLD_PHONE = "17615123399";

	private static final String NEW_PHONE = "13800138000";

	private ISysUserService userService;

	private RemoteDeliveryAccountService remoteDeliveryAccountService;

	private SysUserController controller;

	@BeforeEach
	void setUp() {
		userService = mock(ISysUserService.class);
		remoteDeliveryAccountService = mock(RemoteDeliveryAccountService.class);
		controller = new SysUserController(userService, mock(ISysRoleService.class),
				mock(ISysUserRoleService.class), mock(SysUserSessionRevoker.class), remoteDeliveryAccountService);
	}

	private SysUser existingUser() {
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setUsername("staff");
		user.setNickname("员工");
		user.setPhone(OLD_PHONE);
		return user;
	}

	private SysUserPhoneUpdateDTO phoneUpdate(String phone) {
		SysUserPhoneUpdateDTO dto = new SysUserPhoneUpdateDTO();
		dto.setPhone(phone);
		return dto;
	}

	@Test
	void updatePhoneSavesOnlyIdAndPhoneAndSyncsDeliveryStaff() {
		when(userService.getVisibleUserById("user-1")).thenReturn(existingUser());
		when(userService.getCount(any())).thenReturn(0);
		when(userService.updateById(any())).thenReturn(true);

		Result<Boolean> result = controller.updatePhone("user-1", phoneUpdate(" " + NEW_PHONE + " "));

		assertThat(result.getCode()).isZero();
		assertThat(result.getData()).isTrue();
		ArgumentCaptor<SysUser> updateCaptor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(updateCaptor.capture());
		assertThat(updateCaptor.getValue().getId()).isEqualTo("user-1");
		assertThat(updateCaptor.getValue().getPhone()).isEqualTo(NEW_PHONE);
		// 只更新 id + phone：用户名/密码/昵称等字段不进入 UPDATE 语句，脱敏值不可能回写
		assertThat(updateCaptor.getValue().getUsername()).isNull();
		assertThat(updateCaptor.getValue().getPassword()).isNull();
		assertThat(updateCaptor.getValue().getNickname()).isNull();
		assertThat(updateCaptor.getValue().getEmail()).isNull();
		assertThat(updateCaptor.getValue().getStatus()).isNull();
		// 查重只带 phone，与库上跨租户唯一约束同口径
		ArgumentCaptor<SysUser> queryCaptor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).getCount(queryCaptor.capture());
		assertThat(queryCaptor.getValue().getPhone()).isEqualTo(NEW_PHONE);
		assertThat(queryCaptor.getValue().getId()).isNull();
		verify(remoteDeliveryAccountService).syncStaffPhone("user-1", NEW_PHONE);
	}

	@Test
	void updatePhoneRejectsBlankPhone() {
		Result result = controller.updatePhone("user-1", phoneUpdate("   "));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("请输入手机号");
		verify(userService, never()).getVisibleUserById(anyString());
		verify(userService, never()).updateById(any());
	}

	@Test
	void updatePhoneRejectsNullBodyWithoutNpe() {
		Result result = controller.updatePhone("user-1", null);

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("请输入手机号");
		verify(userService, never()).getVisibleUserById(anyString());
	}

	@Test
	void updatePhoneRejectsInvalidFormat() {
		Result result = controller.updatePhone("user-1", phoneUpdate("12345678901"));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("手机号格式不正确");
		verify(userService, never()).getVisibleUserById(anyString());
	}

	@Test
	void updatePhoneRejectsMissingUser() {
		when(userService.getVisibleUserById("ghost")).thenReturn(null);

		Result result = controller.updatePhone("ghost", phoneUpdate(NEW_PHONE));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("用户不存在或已删除");
		verify(userService, never()).updateById(any());
	}

	@Test
	void updatePhoneRejectsHiddenPlatformAdminAccount() {
		// 平台管理员账号对租户管理员不可见；手机号可直接用于短信登录，不能经员工接口改绑
		when(userService.getVisibleUserById("system-user")).thenReturn(null);

		Result result = controller.updatePhone("system-user", phoneUpdate(NEW_PHONE));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("用户不存在或已删除");
		verify(userService, never()).getCount(any());
		verify(userService, never()).updateById(any());
	}

	@Test
	void updatePhoneRejectsSamePhone() {
		when(userService.getVisibleUserById("user-1")).thenReturn(existingUser());

		Result result = controller.updatePhone("user-1", phoneUpdate(OLD_PHONE));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("新手机号与当前手机号相同");
		verify(userService, never()).getCount(any());
		verify(userService, never()).updateById(any());
		verify(remoteDeliveryAccountService, never()).syncStaffPhone(anyString(), anyString());
	}

	@Test
	void updatePhoneRejectsDuplicatePhoneAcrossTenants() {
		when(userService.getVisibleUserById("user-1")).thenReturn(existingUser());
		when(userService.getCount(any())).thenReturn(1);

		Result result = controller.updatePhone("user-1", phoneUpdate(NEW_PHONE));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("该手机号已被使用");
		verify(userService, never()).updateById(any());
		verify(remoteDeliveryAccountService, never()).syncStaffPhone(anyString(), anyString());
	}

	@Test
	void updatePhoneMapsUniqueKeyConflictToFriendlyMessage() {
		when(userService.getVisibleUserById("user-1")).thenReturn(existingUser());
		when(userService.getCount(any())).thenReturn(0);
		when(userService.updateById(any())).thenThrow(new DuplicateKeyException("uk_sys_user_phone"));

		Result result = controller.updatePhone("user-1", phoneUpdate(NEW_PHONE));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("该手机号已被使用");
		verify(remoteDeliveryAccountService, never()).syncStaffPhone(anyString(), anyString());
	}

	@Test
	void updatePhoneSucceedsEvenWhenDeliverySyncFails() {
		when(userService.getVisibleUserById("user-1")).thenReturn(existingUser());
		when(userService.getCount(any())).thenReturn(0);
		when(userService.updateById(any())).thenReturn(true);
		when(remoteDeliveryAccountService.syncStaffPhone("user-1", NEW_PHONE))
			.thenThrow(new IllegalStateException("配送服务不可用"));

		Result<Boolean> result = controller.updatePhone("user-1", phoneUpdate(NEW_PHONE));

		assertThat(result.getCode()).isZero();
		assertThat(result.getData()).isTrue();
	}

	@Test
	void updatePhoneFailsWhenLocalUpdateReportsFalse() {
		when(userService.getVisibleUserById("user-1")).thenReturn(existingUser());
		when(userService.getCount(any())).thenReturn(0);
		when(userService.updateById(any())).thenReturn(false);

		Result result = controller.updatePhone("user-1", phoneUpdate(NEW_PHONE));

		assertThat(result.getCode()).isNotZero();
		verify(remoteDeliveryAccountService, never()).syncStaffPhone(anyString(), anyString());
	}

	@Test
	void updatePhoneEndpointContractIsFrozen() throws Exception {
		var method = SysUserController.class.getMethod("updatePhone", String.class, SysUserPhoneUpdateDTO.class);

		assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly("/{id}/phone");
		assertThat(method.getAnnotation(SaCheckPermission.class).value())
			.containsExactly("upms:sysuser:phone:edit");
		assertThat(method.getAnnotation(SysLog.class).value()).isEqualTo("修改员工手机号");
	}

}
