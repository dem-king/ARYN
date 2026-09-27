package com.aryn.cloud.user.controller.app;

import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.vo.AppBalanceRecordVO;
import com.aryn.cloud.user.api.vo.AppPointsRecordVO;
import com.aryn.cloud.user.api.vo.AppSignInRecordVO;
import com.aryn.cloud.user.api.vo.PointsRecordVO;
import com.aryn.cloud.user.api.vo.SignInRecordVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.vo.BalanceRecordVO;
import com.aryn.cloud.user.service.IBalanceRecordService;
import com.aryn.cloud.user.service.IPointsRecordService;
import com.aryn.cloud.user.service.ISignInRecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端记录接口的越权防护测试。
 *
 * <p>背景（真实缺陷）：签到 / 积分 / 余额记录原先由 C 端页面直连管理端接口
 * （{@code /pointsrecord/user/page} 等）。这些接口把 {@code userId} 当查询参数接收，
 * 既能查任意用户，又要求 C 端 token 不可能有的管理端权限 —— 于是一律 403。
 *
 * <p>改走 C 端接口后必须保证：<b>用户身份只能来自登录态</b>。
 * 这组测试直接调用 controller，断言传给 service 的 userId 是登录用户，
 * 且响应 VO 不回内部 ID —— 否则「修好 403」会顺带开出一个越权口子。
 */
class AppRecordEndpointAuthorizationTest {

	private ISignInRecordService signInRecordService;

	private IPointsRecordService pointsRecordService;

	private IBalanceRecordService balanceRecordService;

	private MockedStatic<SecurityUtils> securityUtils;

	@BeforeEach
	void setUp() {
		signInRecordService = mock(ISignInRecordService.class);
		pointsRecordService = mock(IPointsRecordService.class);
		balanceRecordService = mock(IBalanceRecordService.class);
		securityUtils = mockStatic(SecurityUtils.class);

		ArynUser loginUser = new ArynUser();
		loginUser.setUserId("mall-user-1");
		securityUtils.when(SecurityUtils::getUser).thenReturn(loginUser);
	}

	@AfterEach
	void tearDown() {
		securityUtils.close();
	}

	@Test
	@DisplayName("签到记录只查当前登录用户，且不回内部 ID")
	void signInRecordsUseLoginUser() {
		AppSignInController controller = new AppSignInController(signInRecordService, null);

		SignInRecordVO row = new SignInRecordVO();
		row.setId("record-1");
		row.setUserId("mall-user-1");
		row.setNickname("张三");
		row.setSignDate(LocalDate.of(2026, 9, 24));
		row.setConsecutiveDay(3);
		row.setRewardPoint(15);
		IPage<SignInRecordVO> source = new Page<SignInRecordVO>(1, 31);
		source.setRecords(List.of(row));
		when(signInRecordService.getUserPage(any(), eq("mall-user-1"))).thenReturn(source);

		IPage<AppSignInRecordVO> result = controller.records(new Page<>()).getData();

		// 关键断言：userId 取自登录态，而不是任何请求参数
		verify(signInRecordService).getUserPage(any(), eq("mall-user-1"));
		assertThat(result.getRecords()).hasSize(1);
		AppSignInRecordVO vo = result.getRecords().get(0);
		assertThat(vo.getSignDate()).isEqualTo(LocalDate.of(2026, 9, 24));
		assertThat(vo.getRewardPoint()).isEqualTo(15);
		// C 端 VO 不得携带 userId / nickname
		assertThat(AppSignInRecordVO.class.getDeclaredFields())
				.extracting(java.lang.reflect.Field::getName)
				.doesNotContain("userId", "nickname");
	}

	@Test
	@DisplayName("积分记录只查当前登录用户")
	void pointsRecordsUseLoginUser() {
		AppPointsController controller = new AppPointsController(null, null, pointsRecordService);

		PointsRecordVO row = new PointsRecordVO();
		row.setId("pr-1");
		row.setUserId("mall-user-1");
		row.setNickname("张三");
		row.setChangeType("1");
		row.setChangePoint(10);
		IPage<PointsRecordVO> source = new Page<PointsRecordVO>(1, 10);
		source.setRecords(List.of(row));
		when(pointsRecordService.getUserPage(any(), eq("mall-user-1"))).thenReturn(source);

		IPage<AppPointsRecordVO> result = controller.records(new Page<>()).getData();

		verify(pointsRecordService).getUserPage(any(), eq("mall-user-1"));
		assertThat(result.getRecords()).hasSize(1);
		assertThat(result.getRecords().get(0).getChangePoint()).isEqualTo(10);
		assertThat(AppPointsRecordVO.class.getDeclaredFields())
				.extracting(java.lang.reflect.Field::getName)
				.doesNotContain("userId", "nickname");
	}

	@Test
	@DisplayName("余额记录只查当前登录用户")
	void balanceRecordsUseLoginUser() {
		AppBalanceController controller = new AppBalanceController(balanceRecordService);

		BalanceRecordVO row = new BalanceRecordVO();
		row.setId("br-1");
		row.setUserId("mall-user-1");
		row.setNickname("张三");
		row.setChangeType("1");
		IPage<BalanceRecordVO> source = new Page<BalanceRecordVO>(1, 10);
		source.setRecords(List.of(row));
		when(balanceRecordService.getUserPage(any(), eq("mall-user-1"))).thenReturn(source);

		IPage<AppBalanceRecordVO> result = controller.records(new Page<>()).getData();

		verify(balanceRecordService).getUserPage(any(), eq("mall-user-1"));
		assertThat(result.getRecords()).hasSize(1);
		assertThat(AppBalanceRecordVO.class.getDeclaredFields())
				.extracting(java.lang.reflect.Field::getName)
				.doesNotContain("userId", "nickname");
	}

	@Test
	@DisplayName("越权防护：其他用户 ID 无法作为参数传入")
	void controllersExposeNoUserIdParameter() {
		// 方法签名里不得出现 userId 参数 —— 身份只能来自 SecurityUtils
		for (var method : AppSignInController.class.getDeclaredMethods()) {
			if (method.getName().equals("records")) {
				assertThat(method.getParameters())
						.extracting(java.lang.reflect.Parameter::getName)
						.doesNotContain("userId");
			}
		}
		for (var method : AppPointsController.class.getDeclaredMethods()) {
			if (method.getName().equals("records")) {
				assertThat(method.getParameters())
						.extracting(java.lang.reflect.Parameter::getName)
						.doesNotContain("userId");
			}
		}
		for (var method : AppBalanceController.class.getDeclaredMethods()) {
			if (method.getName().equals("records")) {
				assertThat(method.getParameters())
						.extracting(java.lang.reflect.Parameter::getName)
						.doesNotContain("userId");
			}
		}

		// 反向确认：管理端接口确实是靠 userId 参数的，这正是不能给 C 端用的原因
		verify(signInRecordService, never()).getUserPage(any(), eq("other-user"));
	}

}
