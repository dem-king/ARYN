package com.aryn.cloud.promotion.controller;

import com.aryn.cloud.promotion.api.vo.MemberActivityGoodsVO;
import com.aryn.cloud.promotion.controller.app.AppMemberActivityGoodsController;
import com.aryn.cloud.promotion.service.IMemberActivityGoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端会员专区自动数据源接口的**请求绑定**契约。
 *
 * <p>守住：
 * <ol>
 *   <li>无 {@code activityId} 时透传 null（运行时取当前进行中的会员价活动）；</li>
 *   <li>带 {@code activityId} 时原样透传（装修器固定活动）；</li>
 *   <li>接口免登可访问（控制器不依赖登录态，未携带 token 也 200）；</li>
 *   <li>无可用活动时返回 200 + data=null（不由后端报错）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("C端会员专区自动数据源接口请求绑定契约")
class AppMemberActivityGoodsControllerBindingTest {

	@Mock
	private IMemberActivityGoodsService memberActivityGoodsService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(new AppMemberActivityGoodsController(memberActivityGoodsService))
			.build();
	}

	@Test
	@DisplayName("不传 activityId：透传 null 并返回活动元信息与 SPU 列表")
	void delegatesWithoutActivityId() throws Exception {
		MemberActivityGoodsVO vo = new MemberActivityGoodsVO();
		vo.setActivityId("act-1");
		vo.setActivityName("会员专区活动");
		vo.setEndTime(LocalDateTime.of(2026, 11, 12, 19, 51, 14));
		vo.setSpuIds(List.of("spu-1", "spu-2"));
		when(memberActivityGoodsService.getActivityGoods(isNull())).thenReturn(vo);

		mockMvc.perform(get("/app/member/activity-goods"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.activityId").value("act-1"))
			.andExpect(jsonPath("$.data.activityName").value("会员专区活动"))
			.andExpect(jsonPath("$.data.spuIds[0]").value("spu-1"))
			.andExpect(jsonPath("$.data.spuIds[1]").value("spu-2"));

		verify(memberActivityGoodsService).getActivityGoods(null);
	}

	@Test
	@DisplayName("带 activityId：原样透传给 service（装修器固定活动）")
	void delegatesWithActivityId() throws Exception {
		MemberActivityGoodsVO vo = new MemberActivityGoodsVO();
		vo.setActivityId("act-fixed");
		vo.setSpuIds(List.of("spu-9"));
		when(memberActivityGoodsService.getActivityGoods("act-fixed")).thenReturn(vo);

		mockMvc.perform(get("/app/member/activity-goods").param("activityId", "act-fixed"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.activityId").value("act-fixed"))
			.andExpect(jsonPath("$.data.spuIds[0]").value("spu-9"));

		verify(memberActivityGoodsService).getActivityGoods("act-fixed");
	}

	@Test
	@DisplayName("无可用活动（service 返回 null）时仍 200 + data=null")
	void returnsNullResultWhenNoActivity() throws Exception {
		when(memberActivityGoodsService.getActivityGoods(any())).thenReturn(null);

		mockMvc.perform(get("/app/member/activity-goods"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

}
