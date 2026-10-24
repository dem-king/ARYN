package com.aryn.cloud.promotion.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aryn.cloud.promotion.api.vo.AppDiscountVO;
import com.aryn.cloud.promotion.controller.app.AppDiscountController;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端批量折扣价接口的**请求绑定**与免登契约。
 *
 * <p>背景：前端（商详 / 购物车）把 SKU 列表拼成逗号分隔的**单个**查询参数
 * （`?skuIds=a,b`），而控制器签名是 `@RequestParam List<String> skuIds`。
 * 两者能对上依赖 Spring 的字符串→集合转换器（按逗号切分）；一旦有人把签名改成
 * `@RequestParam String skuIds`（或前端改成重复参数），编译与类型检查都不会报错，
 * 只有真机取不到折后价——所以在这里钉死。
 *
 * <p>同时钉死免登语义：批量查价接口**不加** {@code @SaCheckLogin}（与折扣会场列表
 * 同一条白名单语义，前端 skipToken 调用）；带登录校验的单 SKU 接口语义保持不变。
 */
class AppDiscountControllerBindingTest {

	private IDiscountActivityService discountActivityService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		discountActivityService = mock(IDiscountActivityService.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new AppDiscountController(discountActivityService))
			.build();
	}

	@Test
	@DisplayName("逗号分隔的 skuIds 被解析成多个元素（与前端拼接口径一致）")
	void parsesCommaSeparatedSkuIds() throws Exception {
		AppDiscountVO vo = new AppDiscountVO();
		vo.setSkuId("sku-1");
		vo.setActivityId("act-1");
		when(discountActivityService.getGoodsDiscountPrices(anyList())).thenReturn(List.of(vo));

		mockMvc.perform(get("/app/discount/prices").param("skuIds", "sku-1,sku-2,sku-3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].skuId").value("sku-1"))
			.andExpect(jsonPath("$.data[0].activityId").value("act-1"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(discountActivityService).getGoodsDiscountPrices(captor.capture());
		assertThat(captor.getValue()).containsExactly("sku-1", "sku-2", "sku-3");
	}

	@Test
	@DisplayName("批量查价免登（无 @SaCheckLogin），单 SKU 接口仍保持登录校验")
	void batchPriceEndpointStaysPublicButSingleSkuEndpointRequiresLogin() throws Exception {
		Method batch = AppDiscountController.class.getMethod("getGoodsDiscountPrices", List.class);
		assertThat(batch.getAnnotation(SaCheckLogin.class))
			.as("批量折扣价接口必须免登（否则未登录商详/购物车会 401）")
			.isNull();

		Method single = AppDiscountController.class.getMethod("getGoodsDiscountInfo", String.class);
		assertThat(single.getAnnotation(SaCheckLogin.class))
			.as("单 SKU 折扣接口是既有登录语义，不能被顺带放开")
			.isNotNull();
	}

	@Test
	@DisplayName("缺少 skuIds 参数时请求绑定失败（400），不会打到 service")
	void rejectsMissingSkuIds() throws Exception {
		mockMvc.perform(get("/app/discount/prices"))
			.andExpect(status().isBadRequest());

		verify(discountActivityService, never()).getGoodsDiscountPrices(anyList());
	}

}
