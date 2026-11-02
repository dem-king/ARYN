package com.aryn.cloud.upms.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import com.aryn.cloud.upms.api.dto.SysUserProfileDTO;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.UploadFileValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人信息自助修改契约：
 * <ol>
 *   <li>资料修改只接受昵称/邮箱，用户 id 取自会话；</li>
 *   <li>修改登录账号必须校验当前密码，且用户名全租户唯一；</li>
 *   <li>改密必须校验旧密码与密码强度，落库只存 BCrypt hash；</li>
 *   <li>修改成功后同步刷新会话中的用户快照。</li>
 * </ol>
 */
class SysUserProfileControllerTest {

	private static final String OLD_PASSWORD = "OldPass123";

	private static final String STRONG_NEW_PASSWORD = "NewPass2026";

	private final ISysUserService userService = mock(ISysUserService.class);

	private final ISysStorageConfigService storageConfigService = mock(ISysStorageConfigService.class);

	private final StorageFactory storageFactory = mock(StorageFactory.class);

	private final UploadFileValidator uploadFileValidator = mock(UploadFileValidator.class);

	private final SysUserProfileController controller = new SysUserProfileController(userService, storageConfigService,
			storageFactory, uploadFileValidator);

	private ArynUser loginUser;

	private MockedStatic<SecurityUtils> securityUtils;

	@BeforeEach
	void setUp() {
		loginUser = new ArynUser();
		loginUser.setUserId("user-1");
		loginUser.setTenantId("tenant-1");
		loginUser.setUsername("system");
		loginUser.setNickname("旧昵称");
		loginUser.setEmail("old@example.com");
		securityUtils = mockStatic(SecurityUtils.class);
		securityUtils.when(SecurityUtils::requireUser).thenReturn(loginUser);
	}

	@AfterEach
	void tearDown() {
		securityUtils.close();
	}

	private SysUser currentUser() {
		SysUser current = new SysUser();
		current.setId("user-1");
		current.setUsername("system");
		current.setNickname("旧昵称");
		current.setPassword(BCrypt.hashpw(OLD_PASSWORD));
		return current;
	}

	private SysUserProfileDTO profile(String nickname, String email) {
		SysUserProfileDTO dto = new SysUserProfileDTO();
		dto.setNickname(nickname);
		dto.setEmail(email);
		return dto;
	}

	private SysUserProfileDTO usernameChange(String username, String password) {
		SysUserProfileDTO dto = new SysUserProfileDTO();
		dto.setUsername(username);
		dto.setPassword(password);
		return dto;
	}

	private SysUserProfileDTO passwordChange(String oldPassword, String newPassword) {
		SysUserProfileDTO dto = new SysUserProfileDTO();
		dto.setPassword(oldPassword);
		dto.setNewPassword(newPassword);
		dto.setCheckPassword(newPassword);
		return dto;
	}

	@Test
	void updateProfileSavesTrimmedFieldsAndRefreshesSessionSnapshot() {
		when(userService.updateById(any())).thenReturn(true);

		Result result = controller.updateProfile(profile("  新昵称  ", "  ops@example.com "));

		assertThat(result.getCode()).isZero();
		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(captor.capture());
		assertThat(captor.getValue().getId()).isEqualTo("user-1");
		assertThat(captor.getValue().getNickname()).isEqualTo("新昵称");
		assertThat(captor.getValue().getEmail()).isEqualTo("ops@example.com");
		assertThat(loginUser.getNickname()).isEqualTo("新昵称");
		assertThat(loginUser.getEmail()).isEqualTo("ops@example.com");
	}

	@Test
	void updateProfileRejectsBlankNicknameAndInvalidEmail() {
		Result blankNickname = controller.updateProfile(profile("   ", "ops@example.com"));
		Result invalidEmail = controller.updateProfile(profile("新昵称", "not-an-email"));

		assertThat(blankNickname.getCode()).isNotZero();
		assertThat(blankNickname.getMsg()).contains("昵称");
		assertThat(invalidEmail.getCode()).isNotZero();
		assertThat(invalidEmail.getMsg()).contains("邮箱");
		verify(userService, never()).updateById(any());
	}

	@Test
	void updateUsernameRequiresCurrentPassword() {
		when(userService.getById("user-1")).thenReturn(currentUser());

		Result result = controller.updateUsername(usernameChange("system2", "wrong-password"));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("当前密码错误");
		verify(userService, never()).updateById(any());
	}

	@Test
	void updateUsernameRejectsDuplicateAccount() {
		when(userService.getById("user-1")).thenReturn(currentUser());
		when(userService.findUserByName("system2")).thenReturn(new SysUser());

		Result result = controller.updateUsername(usernameChange("system2", OLD_PASSWORD));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).contains("已被使用");
		verify(userService, never()).updateById(any());
	}

	@Test
	void updateUsernameSavesAndRefreshesSessionSnapshot() {
		when(userService.getById("user-1")).thenReturn(currentUser());
		when(userService.findUserByName("system2")).thenReturn(null);
		when(userService.updateById(any())).thenReturn(true);

		Result result = controller.updateUsername(usernameChange("system2", OLD_PASSWORD));

		assertThat(result.getCode()).isZero();
		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(captor.capture());
		assertThat(captor.getValue().getUsername()).isEqualTo("system2");
		assertThat(loginUser.getUsername()).isEqualTo("system2");
	}

	@Test
	void updatePasswordRejectsWeakNewPassword() {
		when(userService.getById("user-1")).thenReturn(currentUser());

		Result tooShort = controller.updatePassword(passwordChange(OLD_PASSWORD, "a1b2c3"));
		Result noDigit = controller.updatePassword(passwordChange(OLD_PASSWORD, "abcdefgh"));
		Result weak = controller.updatePassword(passwordChange(OLD_PASSWORD, "admin123"));

		assertThat(tooShort.getMsg()).contains("8到64");
		assertThat(noDigit.getMsg()).contains("数字");
		assertThat(weak.getMsg()).contains("过于简单");
		verify(userService, never()).updateById(any());
	}

	@Test
	void updatePasswordRejectsWrongCurrentPassword() {
		when(userService.getById("user-1")).thenReturn(currentUser());

		Result result = controller.updatePassword(passwordChange("wrong-password", STRONG_NEW_PASSWORD));

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("当前密码错误");
		verify(userService, never()).updateById(any());
	}

	@Test
	void updatePasswordStoresOnlyPasswordHash() {
		when(userService.getById("user-1")).thenReturn(currentUser());
		when(userService.updateById(any())).thenReturn(true);

		Result result = controller.updatePassword(passwordChange(OLD_PASSWORD, STRONG_NEW_PASSWORD));

		assertThat(result.getCode()).isZero();
		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(captor.capture());
		assertThat(captor.getValue().getPassword()).isNotEqualTo(STRONG_NEW_PASSWORD);
		assertThat(BCrypt.checkpw(STRONG_NEW_PASSWORD, captor.getValue().getPassword())).isTrue();
		assertThat(BCrypt.checkpw(OLD_PASSWORD, captor.getValue().getPassword())).isFalse();
	}

}
