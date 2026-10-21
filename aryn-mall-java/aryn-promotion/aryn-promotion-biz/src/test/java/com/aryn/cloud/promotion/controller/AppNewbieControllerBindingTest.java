package com.aryn.cloud.promotion.controller;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import com.aryn.cloud.promotion.api.vo.AppNewbieFloorVO;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;
import com.aryn.cloud.promotion.controller.app.AppNewbieController;
import com.aryn.cloud.promotion.service.INewbieActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端新人价接口的**请求绑定**契约。
 *
 * <p>背景：前端 `getNewbieSkuPrices` 把 SKU 列表拼成逗号分隔的**单个**查询参数
 * （`?skuIds=a,b`），而控制器签名是 `@RequestParam List<String> skuIds`。
 * 两者能对上依赖 Spring 的字符串→集合转换器（按逗号切分）。
 * 一旦有人把签名改成 `@RequestParam String skuIds`（或前端改成重复参数），
 * 编译与类型检查都不会报错，只有真机取不到新人价——所以在这里钉死。
 *
 * <p>同时校验本接口**不要求登录**：白名单放行后未携带 token 的请求也必须 200，
 * 否则免登楼层/未登录商详会拿到 401，被前端当成登录态失效。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("C端新人价接口请求绑定契约")
class AppNewbieControllerBindingTest {

	@Mock
	private INewbieActivityService newbieActivityService;

	private MockMvc mockMvc;

	/**
	 * 控制器里调用了 {@code SecurityUtils.getUser()}（内部是 Sa-Token 的
	 * {@code StpUtil.isLogin()}），standalone MockMvc 没有真实请求的 SaTokenContext，
	 * 未初始化时直接抛 {@code SaTokenContextException}。这里装 Sa-Token 官方的
	 * Mock 上下文（1.42.0+ 提供），让「未登录」分支能真实走到 —— 这正是本测试
	 * 要证明的免登可访问语义。
	 */
	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new AppNewbieController(newbieActivityService)).build();
		SaTokenContextMockUtil.setMockContext();
	}

	@AfterEach
	void tearDown() {
		SaTokenContextMockUtil.clearContext();
	}

	@Test
	@DisplayName("逗号分隔的 skuIds 被解析成多个元素（与前端拼接口径一致）")
	void parsesCommaSeparatedSkuIds() throws Exception {
		AppNewbiePriceVO price = new AppNewbiePriceVO();
		price.setActivityId("act-1");
		when(newbieActivityService.getSkuPrices(anyList(), any())).thenReturn(price);

		mockMvc.perform(get("/app/newbie/prices").param("skuIds", "sku-1,sku-2,sku-3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.activityId").value("act-1"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(newbieActivityService).getSkuPrices(captor.capture(), any());
		assertThat(captor.getValue()).containsExactly("sku-1", "sku-2", "sku-3");
	}

	@Test
	@DisplayName("未携带登录态时仍返回 200（接口在免登白名单内，userId 为 null）")
	void allowsAnonymousRequest() throws Exception {
		when(newbieActivityService.getSkuPrices(anyList(), any())).thenReturn(null);

		mockMvc.perform(get("/app/newbie/prices").param("skuIds", "sku-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0));

		verify(newbieActivityService).getSkuPrices(List.of("sku-1"), null);
	}

	@Test
	@DisplayName("缺少 skuIds 参数时请求绑定失败（400），不会打到 service")
	void rejectsMissingSkuIds() throws Exception {
		mockMvc.perform(get("/app/newbie/prices"))
			.andExpect(status().isBadRequest());

		verify(newbieActivityService, never()).getSkuPrices(anyList(), any());
	}

	@Test
	@DisplayName("当前活动接口免登可访问并原样透传 service 结果")
	void currentDelegatesToService() throws Exception {
		AppNewbieFloorVO floor = new AppNewbieFloorVO();
		floor.setActivityId("act-1");
		when(newbieActivityService.getCurrentFloor()).thenReturn(floor);

		mockMvc.perform(get("/app/newbie/current"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.activityId").value("act-1"));
	}

}
