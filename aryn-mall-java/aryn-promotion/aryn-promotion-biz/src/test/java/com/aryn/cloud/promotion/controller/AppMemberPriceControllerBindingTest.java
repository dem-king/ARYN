package com.aryn.cloud.promotion.controller;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import com.aryn.cloud.promotion.api.vo.AppMemberPriceVO;
import com.aryn.cloud.promotion.controller.app.AppMemberPriceController;
import com.aryn.cloud.promotion.service.IMemberPriceActivityService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端会员价接口的**请求绑定**契约（与 {@link AppNewbieControllerBindingTest} 同族）。
 *
 * <p>守住四件事：
 * <ol>
 *   <li>前端 {@code ?skuIds=a,b} / {@code ?spuIds=a,b} 的逗号拼接口径能被
 *       {@code @RequestParam List<String>} 正确切分；</li>
 *   <li>未携带登录态时仍返回 200（接口在免登白名单内，userId 为 null，不报 401）；</li>
 *   <li>入参优先级：skuIds 非空走原 SKU 逻辑、忽略 spuIds；无 skuIds 时才按 spuIds 查询；</li>
 *   <li>两者都空时返回 200 + data=null（不再 400，列表页可能按需才带参数），且不打到 service。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("C端会员价接口请求绑定契约")
class AppMemberPriceControllerBindingTest {

	@Mock
	private IMemberPriceActivityService memberPriceActivityService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new AppMemberPriceController(memberPriceActivityService)).build();
		// 控制器调用 SecurityUtils.getUser()（内部 Sa-Token StpUtil.isLogin()），
		// standalone MockMvc 没有真实请求的 SaTokenContext，需装官方 Mock 上下文
		SaTokenContextMockUtil.setMockContext();
	}

	@AfterEach
	void tearDown() {
		SaTokenContextMockUtil.clearContext();
	}

	@Test
	@DisplayName("逗号分隔的 skuIds 被解析成多个元素（与前端拼接口径一致）")
	void parsesCommaSeparatedSkuIds() throws Exception {
		AppMemberPriceVO price = new AppMemberPriceVO();
		price.setUserLevelId("lv-user");
		// 会员折扣率权益随响应下发，供展示侧计算取优价（min(memberPrice, originalPrice×rate)）
		price.setUserDiscountRate(new BigDecimal("0.9"));
		AppMemberPriceVO.MemberSkuPriceVO item = new AppMemberPriceVO.MemberSkuPriceVO();
		item.setSkuId("sku-1");
		item.setMemberPrice(new BigDecimal("9.9"));
		price.setItems(List.of(item));
		when(memberPriceActivityService.getSkuPrices(anyList(), any())).thenReturn(price);

		mockMvc.perform(get("/app/member/prices").param("skuIds", "sku-1,sku-2,sku-3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.userLevelId").value("lv-user"))
			.andExpect(jsonPath("$.data.userDiscountRate").value(0.9))
			.andExpect(jsonPath("$.data.items[0].skuId").value("sku-1"))
			.andExpect(jsonPath("$.data.items[0].memberPrice").value(9.9));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(memberPriceActivityService).getSkuPrices(captor.capture(), any());
		assertThat(captor.getValue()).containsExactly("sku-1", "sku-2", "sku-3");
	}

	@Test
	@DisplayName("未携带登录态时仍返回 200（接口在免登白名单内，userId 为 null）")
	void allowsAnonymousRequest() throws Exception {
		when(memberPriceActivityService.getSkuPrices(anyList(), any())).thenReturn(null);

		mockMvc.perform(get("/app/member/prices").param("skuIds", "sku-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0));

		verify(memberPriceActivityService).getSkuPrices(List.of("sku-1"), null);
	}

	@Test
	@DisplayName("只传 spuIds 时走按 SPU 查询，逗号分隔被正确切分并透传 userId=null")
	void parsesSpuIdsWhenSkuIdsAbsent() throws Exception {
		AppMemberPriceVO price = new AppMemberPriceVO();
		AppMemberPriceVO.MemberSkuPriceVO item = new AppMemberPriceVO.MemberSkuPriceVO();
		item.setSkuId("sku-1");
		item.setSpuId("spu-1");
		item.setMemberPrice(new BigDecimal("9.9"));
		price.setItems(List.of(item));
		when(memberPriceActivityService.getSpuPrices(anyList(), any())).thenReturn(price);

		mockMvc.perform(get("/app/member/prices").param("spuIds", "spu-1,spu-2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.items[0].spuId").value("spu-1"))
			.andExpect(jsonPath("$.data.items[0].memberPrice").value(9.9));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(memberPriceActivityService).getSpuPrices(captor.capture(), any());
		assertThat(captor.getValue()).containsExactly("spu-1", "spu-2");
		verify(memberPriceActivityService, never()).getSkuPrices(anyList(), any());
	}

	@Test
	@DisplayName("skuIds 与 spuIds 同时传时 skuIds 优先，spuIds 被忽略（原 SKU 逻辑行为不变）")
	void skuIdsTakePriorityOverSpuIds() throws Exception {
		AppMemberPriceVO price = new AppMemberPriceVO();
		price.setUserLevelId("lv-user");
		when(memberPriceActivityService.getSkuPrices(anyList(), any())).thenReturn(price);

		mockMvc.perform(get("/app/member/prices").param("skuIds", "sku-1").param("spuIds", "spu-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userLevelId").value("lv-user"));

		verify(memberPriceActivityService).getSkuPrices(List.of("sku-1"), null);
		verify(memberPriceActivityService, never()).getSpuPrices(anyList(), any());
	}

	@Test
	@DisplayName("skuIds 与 spuIds 都不传时返回 200 + data=null，不会打到 service")
	void missingBothParamsReturnsNullResult() throws Exception {
		mockMvc.perform(get("/app/member/prices"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data").value(nullValue()));

		verify(memberPriceActivityService, never()).getSkuPrices(anyList(), any());
		verify(memberPriceActivityService, never()).getSpuPrices(anyList(), any());
	}

}
