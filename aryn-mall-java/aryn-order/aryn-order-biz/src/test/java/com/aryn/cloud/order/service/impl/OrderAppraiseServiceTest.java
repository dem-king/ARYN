package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderAppraiseServiceTest {

	private OrderInfoMapper orderInfoMapper;
	private OrderItemMapper orderItemMapper;
	private RemoteMallUserService remoteMallUserService;
	private RemoteGoodsAppraiseService remoteGoodsAppraiseService;
	private OrderAppraiseService service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		orderItemMapper = mock(OrderItemMapper.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		remoteGoodsAppraiseService = mock(RemoteGoodsAppraiseService.class);
		service = new OrderAppraiseService(orderInfoMapper, orderItemMapper, remoteMallUserService,
			remoteGoodsAppraiseService);
	}

	@Test
	void rejectsItemsThatDoNotBelongToOrder() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));

		assertThatThrownBy(() -> service.appraiseOrder("order-1", "user-1",
			List.of(appraise("foreign-item"))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg())
				.isEqualTo("评价商品与订单不匹配"));
		verify(remoteGoodsAppraiseService, never()).addGoodsAppraise(any());
	}

	@Test
	void replacesClientOwnedIdentityWithOrderSnapshot() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));
		UserInfoVO user = new UserInfoVO();
		user.setNickname("用户");
		user.setAvatarUrl("avatar.png");
		when(remoteMallUserService.getUserById("user-1")).thenReturn(user);
		when(remoteGoodsAppraiseService.addGoodsAppraise(any())).thenReturn(true);
		when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);
		OrderAppraiseDTO request = appraise("item-1");
		request.setOrderId("foreign-order");
		request.setSpuId("foreign-spu");
		request.setUserId("foreign-user");

		assertThat(service.appraiseOrder("order-1", "user-1", List.of(request))).isTrue();

		ArgumentCaptor<List<GoodsAppraise>> captor = ArgumentCaptor.forClass(List.class);
		verify(remoteGoodsAppraiseService).addGoodsAppraise(captor.capture());
		GoodsAppraise saved = captor.getValue().get(0);
		assertThat(saved.getOrderId()).isEqualTo("order-1");
		assertThat(saved.getOrderItemId()).isEqualTo("item-1");
		assertThat(saved.getSpuId()).isEqualTo("spu-1");
		assertThat(saved.getUserId()).isEqualTo("user-1");
	}

	private OrderInfo completedOrder() {
		return new OrderInfo().setId("order-1").setUserId("user-1")
			.setStatus(OrderStatusEnum.COMPLETED.getCode()).setAppraiseStatus(CommonConstants.NO);
	}

	private OrderItemEntity orderItem(String id, String spuId) {
		return new OrderItemEntity().setId(id).setOrderId("order-1").setSpuId(spuId);
	}

	private OrderAppraiseDTO appraise(String orderItemId) {
		OrderAppraiseDTO request = new OrderAppraiseDTO();
		request.setOrderItemId(orderItemId);
		request.setGoodsScore(5);
		request.setContent("很好");
		return request;
	}
}
