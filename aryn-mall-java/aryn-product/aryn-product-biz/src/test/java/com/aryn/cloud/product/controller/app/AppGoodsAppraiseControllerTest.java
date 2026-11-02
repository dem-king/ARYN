package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppGoodsAppraiseControllerTest {

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void pageOnlyQueriesUserAppraiseAndMasksAnonymous() {
		IGoodsAppraiseService goodsAppraiseService = mock(IGoodsAppraiseService.class);
		GoodsAppraise anonymous = new GoodsAppraise().setId("appraise-1").setUserId("user-1")
			.setNickname("张三").setAvatarUrl("avatar.png").setIsAnonymous(1).setAppraiseType(0);
		GoodsAppraise normal = new GoodsAppraise().setId("appraise-2").setUserId("user-2").setNickname("李四")
			.setAvatarUrl("avatar2.png").setIsAnonymous(0).setAppraiseType(0);
		Page<GoodsAppraise> result = new Page<>(1, 10);
		result.setRecords(new ArrayList<>(List.of(anonymous, normal)));
		when(goodsAppraiseService.page(any(Page.class), any(Wrapper.class))).thenReturn(result);
		AppGoodsAppraiseController controller = new AppGoodsAppraiseController(goodsAppraiseService);
		// 客户端伪造 appraiseType=1 试图把系统默认评价带出来
		GoodsAppraise query = new GoodsAppraise();
		query.setSpuId("spu-1");
		query.setAppraiseType(1);

		controller.getPage(new Page<>(), query);

		// 系统默认评价不进商详列表：查询条件被强制 appraise_type=0，其余条件原样保留
		assertThat(query.getAppraiseType()).isZero();
		ArgumentCaptor<Wrapper<GoodsAppraise>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(goodsAppraiseService).page(any(Page.class), wrapperCaptor.capture());
		AbstractWrapper<GoodsAppraise, ?, ?> wrapper = (AbstractWrapper<GoodsAppraise, ?, ?>) wrapperCaptor
			.getValue();
		assertThat(wrapper.getEntity()).isSameAs(query);
		assertThat(wrapper.getEntity().getSpuId()).isEqualTo("spu-1");
		// 匿名评价脱敏：不暴露昵称/头像/userId；正常评价保持原样
		assertThat(anonymous.getNickname()).isEqualTo("匿名用户");
		assertThat(anonymous.getAvatarUrl()).isNull();
		assertThat(anonymous.getUserId()).isNull();
		assertThat(normal.getNickname()).isEqualTo("李四");
		assertThat(normal.getUserId()).isEqualTo("user-2");
		assertThat(normal.getAvatarUrl()).isEqualTo("avatar2.png");
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void userPageQueriesOwnAppraisesByOrderIdWithoutMasking() {
		IGoodsAppraiseService goodsAppraiseService = mock(IGoodsAppraiseService.class);
		Page<GoodsAppraise> result = new Page<>(1, 10);
		when(goodsAppraiseService.getPage(any(Page.class), any(GoodsAppraise.class))).thenReturn(result);
		AppGoodsAppraiseController controller = new AppGoodsAppraiseController(goodsAppraiseService);
		ArynUser user = new ArynUser();
		user.setUserId("user-1");
		GoodsAppraise query = new GoodsAppraise();
		query.setOrderId("order-1");

		try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
			mocked.when(SecurityUtils::getUser).thenReturn(user);
			controller.getPageByUserId(new Page<>(), query);
		}

		// 本人评价走 selectAppraisePage（带 spuName/spuUrls 与新字段），按当前用户 + orderId 查询且不脱敏
		ArgumentCaptor<GoodsAppraise> queryCaptor = ArgumentCaptor.forClass(GoodsAppraise.class);
		verify(goodsAppraiseService).getPage(any(Page.class), queryCaptor.capture());
		assertThat(queryCaptor.getValue().getUserId()).isEqualTo("user-1");
		assertThat(queryCaptor.getValue().getOrderId()).isEqualTo("order-1");
	}

}
