package com.aryn.cloud.user.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.vo.SignInRecordVO;
import com.aryn.cloud.user.service.ISignInRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignInRecordControllerTest {

	@Mock
	private ISignInRecordService signInRecordService;

	private SignInRecordController controller;

	@BeforeEach
	void setUp() {
		controller = new SignInRecordController(signInRecordService);
	}

	@Test
	@DisplayName("会员详情传 userId 时只查该会员签到记录")
	void pageWithUserIdQueriesUserRecords() {
		when(signInRecordService.getUserPage(any(), eq("user-1"), isNull(), isNull()))
			.thenReturn(new Page<SignInRecordVO>());

		controller.page(new Page<>(), null, null, null, "user-1");

		verify(signInRecordService).getUserPage(any(), eq("user-1"), isNull(), isNull());
		verify(signInRecordService, never()).getPage(any(), any(), any(), any());
	}

	@Test
	@DisplayName("后台列表不传 userId 时保持全量查询")
	void pageWithoutUserIdQueriesAllRecords() {
		when(signInRecordService.getPage(any(), isNull(), isNull(), isNull()))
			.thenReturn(new Page<SignInRecordVO>());

		controller.page(new Page<>(), null, null, null, null);

		verify(signInRecordService).getPage(any(), isNull(), isNull(), isNull());
		verify(signInRecordService, never()).getUserPage(any(), any(), any(), any());
	}

}
