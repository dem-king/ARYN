package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.OrderAdvanceAppraiseDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseEditDTO;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

	// ============================ 主评价：部分评价 / 幂等 / 兜底 ============================

	@Test
	void rejectsItemsThatDoNotBelongToOrder() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));

		assertThatThrownBy(() -> service.appraiseOrder("order-1", "user-1", List.of(appraise("foreign-item"))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("评价商品与订单不匹配"));
		verify(remoteGoodsAppraiseService, never()).addGoodsAppraise(any());
	}

	@Test
	void rejectsDuplicateItems() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1"))
			.thenReturn(List.of(orderItem("item-1", "spu-1"), orderItem("item-2", "spu-2")));

		assertThatThrownBy(() -> service.appraiseOrder("order-1", "user-1",
				List.of(appraise("item-1"), appraise("item-1"))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("评价商品不能重复提交"));
		verify(remoteGoodsAppraiseService, never()).addGoodsAppraise(any());
	}

	@Test
	void rejectsOrderNotCompleted() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(new OrderInfo().setId("order-1").setUserId("user-1")
			.setStatus(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())
			.setAppraiseStatus(CommonConstants.NO));

		assertThatThrownBy(() -> service.appraiseOrder("order-1", "user-1", List.of(appraise("item-1"))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("订单尚未完成，不能评价"));
	}

	@Test
	void ignoresOrderOfOtherUser() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());

		assertThat(service.appraiseOrder("order-1", "other-user", List.of(appraise("item-1")))).isFalse();
		verify(remoteGoodsAppraiseService, never()).addGoodsAppraise(any());
	}

	@Test
	void fillsDefaultContentAndServerSideIdentity() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));
		when(remoteMallUserService.getUserById("user-1")).thenReturn(userInfo());
		when(remoteGoodsAppraiseService.addGoodsAppraise(any())).thenReturn(true);
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of());
		OrderAppraiseDTO request = appraise("item-1");
		request.setContent("   ");
		request.setOrderId("foreign-order");
		request.setSpuId("foreign-spu");
		request.setUserId("foreign-user");
		request.setNickname("伪造昵称");
		request.setAvatarUrl("foreign-avatar.png");
		request.setLogisticsScore(5);
		request.setServiceScore(4);
		request.setIsAnonymous(true);
		request.setTags(List.of(" 新鲜 ", "", "超长超长超长超长超长超长", "新鲜", "包装好"));

		assertThat(service.appraiseOrder("order-1", "user-1", List.of(request))).isTrue();

		GoodsAppraise saved = captureAdded().get(0);
		assertThat(saved.getOrderId()).isEqualTo("order-1");
		assertThat(saved.getOrderItemId()).isEqualTo("item-1");
		assertThat(saved.getSpuId()).isEqualTo("spu-1");
		assertThat(saved.getUserId()).isEqualTo("user-1");
		assertThat(saved.getNickname()).isEqualTo("用户");
		assertThat(saved.getAvatarUrl()).isEqualTo("avatar.png");
		assertThat(saved.getContent()).isEqualTo("此用户未填写评价内容");
		assertThat(saved.getAppraiseType()).isZero();
		assertThat(saved.getIsAnonymous()).isEqualTo(1);
		assertThat(saved.getGoodsScore()).isEqualTo(5);
		assertThat(saved.getLogisticsScore()).isEqualTo(5);
		assertThat(saved.getServiceScore()).isEqualTo(4);
		assertThat(saved.getTags()).containsExactly("新鲜", "包装好");
	}

	@Test
	void partialAppraiseKeepsOrderPending() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1"))
			.thenReturn(List.of(orderItem("item-1", "spu-1"), orderItem("item-2", "spu-2")));
		when(remoteMallUserService.getUserById("user-1")).thenReturn(userInfo());
		when(remoteGoodsAppraiseService.addGoodsAppraise(any())).thenReturn(true);
		// 插入前无已评；插入后只有 item-1，仍缺 item-2 → 订单保持待评价
		when(remoteGoodsAppraiseService.listByOrderId("order-1"))
			.thenReturn(List.of(), List.of(existingAppraise("item-1")));

		assertThat(service.appraiseOrder("order-1", "user-1", List.of(appraise("item-1")))).isTrue();

		verify(orderInfoMapper, never()).update(any(), any(Wrapper.class));
	}

	@Test
	void marksOrderEvaluatedOnlyWhenAllItemsAppraised() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1"))
			.thenReturn(List.of(orderItem("item-1", "spu-1"), orderItem("item-2", "spu-2")));
		when(remoteMallUserService.getUserById("user-1")).thenReturn(userInfo());
		when(remoteGoodsAppraiseService.addGoodsAppraise(any())).thenReturn(true);
		when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);
		when(remoteGoodsAppraiseService.listByOrderId("order-1"))
			.thenReturn(List.of(), List.of(existingAppraise("item-1"), existingAppraise("item-2")));

		assertThat(service.appraiseOrder("order-1", "user-1",
				List.of(appraise("item-1"), appraise("item-2")))).isTrue();

		AbstractWrapper<OrderInfo, ?, ?> updateWrapper = captureOrderUpdateWrapper();
		assertThat(updateWrapper.getSqlSet()).contains("appraise_status");
		assertThat(updateWrapper.getParamNameValuePairs()).containsValue(CommonConstants.YES);
	}

	@Test
	void skipsAlreadyAppraisedItemsIdempotently() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));
		when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);
		when(remoteGoodsAppraiseService.listByOrderId("order-1"))
			.thenReturn(List.of(existingAppraise("item-1")));

		assertThat(service.appraiseOrder("order-1", "user-1", List.of(appraise("item-1")))).isTrue();

		verify(remoteGoodsAppraiseService, never()).addGoodsAppraise(any());
	}

	// ============================ 系统自动评价：默认评价不打分 ============================

	@Test
	void autoAppraiseUsesOrderItemSpuIdAndSystemDefaultFlag() {
		OrderInfo orderInfo = completedOrder();
		when(remoteMallUserService.getUserById("user-1")).thenReturn(userInfo());
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of());
		when(remoteGoodsAppraiseService.addGoodsAppraise(any())).thenReturn(true);
		when(orderInfoMapper.updateById(orderInfo)).thenReturn(1);

		assertThat(service.autoAppraiseOrder(orderInfo)).isTrue();
		assertThat(orderInfo.getAppraiseStatus()).isEqualTo(CommonConstants.YES);

		GoodsAppraise saved = captureAdded().get(0);
		assertThat(saved.getSpuId()).isEqualTo("spu-1");
		assertThat(saved.getOrderId()).isEqualTo("order-1");
		assertThat(saved.getOrderItemId()).isEqualTo("item-1");
		assertThat(saved.getContent()).isEqualTo("系统默认好评");
		assertThat(saved.getAppraiseType()).isEqualTo(1);
		assertThat(saved.getIsAnonymous()).isEqualTo(1);
		// 默认评价不打分：商品/物流/服务评分均为空
		assertThat(saved.getGoodsScore()).isNull();
		assertThat(saved.getLogisticsScore()).isNull();
		assertThat(saved.getServiceScore()).isNull();
	}

	@Test
	void autoAppraiseSkipsItemsAlreadyAppraised() {
		OrderInfo orderInfo = completedOrder();
		when(remoteMallUserService.getUserById("user-1")).thenReturn(userInfo());
		when(orderItemMapper.selectByOrderId("order-1"))
			.thenReturn(List.of(orderItem("item-1", "spu-1"), orderItem("item-2", "spu-2")));
		when(remoteGoodsAppraiseService.listByOrderId("order-1"))
			.thenReturn(List.of(existingAppraise("item-1")));
		when(remoteGoodsAppraiseService.addGoodsAppraise(any())).thenReturn(true);
		when(orderInfoMapper.updateById(orderInfo)).thenReturn(1);

		assertThat(service.autoAppraiseOrder(orderInfo)).isTrue();

		List<GoodsAppraise> saved = captureAdded();
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getOrderItemId()).isEqualTo("item-2");
	}

	@Test
	void autoAppraiseSkipsOrderAlreadyEvaluated() {
		OrderInfo orderInfo = completedOrder().setAppraiseStatus(CommonConstants.YES);

		assertThat(service.autoAppraiseOrder(orderInfo)).isTrue();
		verify(remoteGoodsAppraiseService, never()).addGoodsAppraise(any());
	}

	// ============================ 追评 / 修改 / 删除 ============================

	@Test
	void advanceAppraiseFillsDefaultContentWithinWindow() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(1));
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));
		when(remoteGoodsAppraiseService.updateById(any())).thenReturn(true);
		OrderAdvanceAppraiseDTO request = new OrderAdvanceAppraiseDTO();
		request.setOrderItemId("item-1");
		request.setContent("   ");
		request.setPicUrls(List.of("http://localhost/a.jpg", " "));

		assertThat(service.advanceAppraiseOrder("order-1", "item-1", "user-1", request)).isTrue();

		ArgumentCaptor<GoodsAppraise> captor = ArgumentCaptor.forClass(GoodsAppraise.class);
		verify(remoteGoodsAppraiseService).updateById(captor.capture());
		GoodsAppraise updated = captor.getValue();
		assertThat(updated.getAdvanceContent()).isEqualTo("此用户未填写追评内容");
		assertThat(updated.getAdvancePicUrls()).containsExactly("http://localhost/a.jpg");
		assertThat(updated.getAdvanceTime()).isNotNull();
	}

	@Test
	void advanceAppraiseRejectsSecondAdvance() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(1));
		existing.setAdvanceTime(LocalDateTime.now().minusDays(1));
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));

		assertThatThrownBy(() -> service.advanceAppraiseOrder("order-1", "item-1", "user-1", advanceRequest()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("该商品已追评，不能重复追评"));
		verify(remoteGoodsAppraiseService, never()).updateById(any());
	}

	@Test
	void advanceAppraiseRejectsWhenWindowExpired() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(31));
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));

		assertThatThrownBy(() -> service.advanceAppraiseOrder("order-1", "item-1", "user-1", advanceRequest()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("评价发布已超过30天，不能追评"));
		verify(remoteGoodsAppraiseService, never()).updateById(any());
	}

	@Test
	void advanceAppraiseRejectsOrderOfOtherUser() {
		OrderInfo orderInfo = completedOrder().setUserId("other-user");
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);

		assertThatThrownBy(() -> service.advanceAppraiseOrder("order-1", "item-1", "user-1", advanceRequest()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("订单不存在"));
	}

	@Test
	void editAppraiseWritesContentScoreAndModifyTime() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(1));
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));
		when(remoteGoodsAppraiseService.updateById(any())).thenReturn(true);
		OrderAppraiseEditDTO request = new OrderAppraiseEditDTO();
		request.setContent("补充一下，包装很好");
		request.setPicUrls(List.of("http://localhost/b.jpg"));
		request.setGoodsScore(4);

		assertThat(service.editAppraiseOrder("order-1", "item-1", "user-1", request)).isTrue();

		ArgumentCaptor<GoodsAppraise> captor = ArgumentCaptor.forClass(GoodsAppraise.class);
		verify(remoteGoodsAppraiseService).updateById(captor.capture());
		GoodsAppraise updated = captor.getValue();
		assertThat(updated.getContent()).isEqualTo("补充一下，包装很好");
		assertThat(updated.getPicUrls()).containsExactly("http://localhost/b.jpg");
		assertThat(updated.getGoodsScore()).isEqualTo(4);
		assertThat(updated.getModifyTime()).isNotNull();
	}

	@Test
	void editAppraiseWithBlankContentFallsBackToDefaultTextAndCanClearPictures() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(1));
		existing.setPicUrls(new String[] { "http://localhost/old.jpg" });
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));
		when(remoteGoodsAppraiseService.updateById(any())).thenReturn(true);
		// 前端清空文字时提交空串（非 null）：服务端统一落默认文案，保证 content NOT NULL
		OrderAppraiseEditDTO request = new OrderAppraiseEditDTO();
		request.setContent("   ");
		request.setPicUrls(List.of());

		assertThat(service.editAppraiseOrder("order-1", "item-1", "user-1", request)).isTrue();

		ArgumentCaptor<GoodsAppraise> captor = ArgumentCaptor.forClass(GoodsAppraise.class);
		verify(remoteGoodsAppraiseService).updateById(captor.capture());
		assertThat(captor.getValue().getContent()).isEqualTo("此用户未填写评价内容");
		assertThat(captor.getValue().getPicUrls()).isEmpty();
	}

	@Test
	void editAppraiseRejectsSecondModify() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(1));
		existing.setModifyTime(LocalDateTime.now().minusDays(1));
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));

		assertThatThrownBy(() -> service.editAppraiseOrder("order-1", "item-1", "user-1", new OrderAppraiseEditDTO()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("评价只能修改一次，不能再次修改"));
	}

	@Test
	void deleteAppraiseLogicallyRemovesAndRollsBackOrderStatus() {
		OrderInfo orderInfo = completedOrder().setAppraiseStatus(CommonConstants.YES);
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);
		when(orderItemMapper.selectByOrderId("order-1")).thenReturn(List.of(orderItem("item-1", "spu-1")));
		GoodsAppraise existing = existingAppraise("item-1");
		existing.setCreateTime(LocalDateTime.now().minusDays(1));
		// 删除前有评价；删除后（逻辑删除）查询为空 → 订单回落待评价
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing), List.of());
		when(remoteGoodsAppraiseService.removeById("appraise-1")).thenReturn(true);
		when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);

		assertThat(service.deleteAppraiseOrder("order-1", "item-1", "user-1")).isTrue();

		verify(remoteGoodsAppraiseService).removeById("appraise-1");
		AbstractWrapper<OrderInfo, ?, ?> updateWrapper = captureOrderUpdateWrapper();
		assertThat(updateWrapper.getSqlSet()).contains("appraise_status");
		assertThat(updateWrapper.getParamNameValuePairs()).containsValue(CommonConstants.NO);
	}

	@Test
	void deleteAppraiseIsIdempotentWhenAlreadyDeleted() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of());

		assertThat(service.deleteAppraiseOrder("order-1", "item-1", "user-1")).isTrue();
		verify(remoteGoodsAppraiseService, never()).removeById(any());
	}

	@Test
	void editAppraiseRejectsSystemDefaultAppraise() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder());
		GoodsAppraise existing = existingAppraise("item-1").setAppraiseType(1);
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));

		assertThatThrownBy(() -> service.editAppraiseOrder("order-1", "item-1", "user-1", new OrderAppraiseEditDTO()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("系统默认评价不支持修改"));
		verify(remoteGoodsAppraiseService, never()).updateById(any());
	}

	@Test
	void deleteAppraiseRejectsSystemDefaultAppraise() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(completedOrder().setAppraiseStatus(CommonConstants.YES));
		GoodsAppraise existing = existingAppraise("item-1").setAppraiseType(1);
		when(remoteGoodsAppraiseService.listByOrderId("order-1")).thenReturn(List.of(existing));

		assertThatThrownBy(() -> service.deleteAppraiseOrder("order-1", "item-1", "user-1"))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg()).isEqualTo("系统默认评价不支持删除"));
		verify(remoteGoodsAppraiseService, never()).removeById(any());
	}

	// ============================ helpers ============================

	@SuppressWarnings("unchecked")
	private List<GoodsAppraise> captureAdded() {
		ArgumentCaptor<List<GoodsAppraise>> captor = ArgumentCaptor.forClass(List.class);
		verify(remoteGoodsAppraiseService).addGoodsAppraise(captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private AbstractWrapper<OrderInfo, ?, ?> captureOrderUpdateWrapper() {
		ArgumentCaptor<Wrapper<OrderInfo>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(orderInfoMapper).update(isNull(), captor.capture());
		return (AbstractWrapper) captor.getValue();
	}

	private OrderInfo completedOrder() {
		return new OrderInfo().setId("order-1").setUserId("user-1")
			.setStatus(OrderStatusEnum.COMPLETED.getCode()).setAppraiseStatus(CommonConstants.NO);
	}

	private OrderItemEntity orderItem(String id, String spuId) {
		return new OrderItemEntity().setId(id).setOrderId("order-1").setSpuId(spuId);
	}

	private UserInfoVO userInfo() {
		UserInfoVO user = new UserInfoVO();
		user.setNickname("用户");
		user.setAvatarUrl("avatar.png");
		return user;
	}

	private GoodsAppraise existingAppraise(String orderItemId) {
		return new GoodsAppraise().setId("appraise-1").setOrderId("order-1").setOrderItemId(orderItemId)
			.setSpuId("spu-1").setUserId("user-1").setContent("很好").setGoodsScore(5).setAppraiseType(0)
			.setIsAnonymous(0).setCreateTime(LocalDateTime.now().minusDays(1));
	}

	private OrderAppraiseDTO appraise(String orderItemId) {
		OrderAppraiseDTO request = new OrderAppraiseDTO();
		request.setOrderItemId(orderItemId);
		request.setGoodsScore(5);
		request.setContent("很好");
		return request;
	}

	private OrderAdvanceAppraiseDTO advanceRequest() {
		OrderAdvanceAppraiseDTO request = new OrderAdvanceAppraiseDTO();
		request.setOrderItemId("item-1");
		request.setContent("追评");
		return request;
	}

}
