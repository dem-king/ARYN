package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.logistics.util.Kuaidi100Utils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.mapper.PromotionSnapshotMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.order.validator.DeliveryContextValidator;
import com.aryn.cloud.order.validator.PurchaseSceneValidator;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端订单商品分类名回填与导出列表组装的单元测试。
 *
 * <p>order_item 不落分类快照，分类名经 {@code RemoteGoodsSpuService#getSpuByIds}
 * 实时回填；商品域不可用时降级为无分类（null），不能阻断详情与导出主流程。
 */
class OrderInfoCategoryBackfillTest {

	private OrderInfoServiceImpl service;

	private OrderInfoMapper orderInfoMapper;

	private OrderItemMapper orderItemMapper;

	private RemoteGoodsSpuService remoteGoodsSpuService;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItemEntity.class);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		orderInfoMapper = mock(OrderInfoMapper.class);
		orderItemMapper = mock(OrderItemMapper.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);

		service = new OrderInfoServiceImpl(mock(IOrderConfigService.class), mock(Kuaidi100Utils.class),
				orderItemMapper, mock(RemoteGoodsSkuService.class), remoteGoodsSpuService,
				mock(com.aryn.cloud.user.api.remote.RemoteMallUserService.class), mock(IOrderItemService.class),
				mock(IShoppingCartService.class), mock(com.aryn.cloud.pay.api.remote.RemotePayService.class),
				mock(ApplicationEventPublisher.class), mock(OrderPriceComputeService.class),
				mock(OrderWxDeliveryService.class), mock(OrderStatisticsQueryService.class),
				mock(OrderAppraiseService.class), mock(com.aryn.cloud.user.api.remote.RemoteUserAddressService.class),
				mock(OrderDeliveryMapper.class), mock(OrderRefundMapper.class),
				mock(RemoteCouponUserService.class), mock(RemoteSeckillService.class),
				mock(org.apache.rocketmq.spring.core.RocketMQTemplate.class),
				mock(CallbackPrefixProperties.class), mock(com.aryn.cloud.order.service.IDeliveryTaskService.class),
				mock(com.aryn.cloud.order.service.IDeliveryAreaService.class), mock(PromotionSnapshotMapper.class),
				mock(PurchaseSceneValidator.class), mock(DeliveryContextValidator.class),
				mock(OrderPaySuccessNotifier.class), mock(RemotePromotionEngine.class),
				mock(com.aryn.cloud.upms.api.remote.RemoteMaterialService.class),
				mock(ObjectProvider.class));
		ReflectionTestUtils.setField(service, "baseMapper", orderInfoMapper);
	}

	private GoodsSpu spu(String id, String categoryName) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(id);
		spu.setCategoryName(categoryName);
		return spu;
	}

	private OrderItemEntity item(String orderId, String spuId, String spuName, int quantity, String price) {
		OrderItemEntity item = new OrderItemEntity();
		item.setOrderId(orderId);
		item.setSpuId(spuId);
		item.setSpuName(spuName);
		item.setBuyQuantity(quantity);
		item.setTotalPrice(new BigDecimal(price));
		return item;
	}

	@Test
	void listForExportBackfillsCategoryAndItems() {
		OrderInfo orderA = new OrderInfo();
		orderA.setId("order-a");
		OrderInfo orderB = new OrderInfo();
		orderB.setId("order-b");
		when(orderInfoMapper.countExportList(any())).thenReturn(2);
		when(orderInfoMapper.selectExportList(any())).thenReturn(List.of(orderA, orderB));
		when(orderItemMapper.selectByOrderIds(any()))
			.thenReturn(List.of(item("order-a", "spu-1", "矿泉水", 3, "9.00"),
					item("order-a", "spu-2", "薯片", 1, "12.00"),
					item("order-b", "spu-1", "矿泉水", 2, "6.00")));
		when(remoteGoodsSpuService.getSpuByIds(any()))
			.thenReturn(List.of(spu("spu-1", "饮品/水"), spu("spu-2", "零食/膨化")));

		List<OrderInfo> orders = service.listForExport(new OrderInfo());

		assertThat(orders).hasSize(2);
		assertThat(orders.get(0).getOrderItemList()).extracting(OrderItemEntity::getCategoryName)
			.containsExactly("饮品/水", "零食/膨化");
		assertThat(orders.get(1).getOrderItemList()).extracting(OrderItemEntity::getCategoryName)
			.containsExactly("饮品/水");
	}

	@Test
	void listForExportThrowsWhenExceedingLimit() {
		when(orderInfoMapper.countExportList(any())).thenReturn(MallOrderConstants.EXPORT_MAX_ORDERS + 1);

		// ArynBusinessException 只写 msg 字段，不进 Throwable message
		assertThatThrownBy(() -> service.listForExport(new OrderInfo()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("5000"));
	}

	@Test
	void listForExportDegradesWhenCategoryServiceFails() {
		OrderInfo order = new OrderInfo();
		order.setId("order-a");
		when(orderInfoMapper.countExportList(any())).thenReturn(1);
		when(orderInfoMapper.selectExportList(any())).thenReturn(List.of(order));
		when(orderItemMapper.selectByOrderIds(any())).thenReturn(List.of(item("order-a", "spu-1", "矿泉水", 1, "3.00")));
		when(remoteGoodsSpuService.getSpuByIds(any())).thenThrow(new RuntimeException("dubbo timeout"));

		List<OrderInfo> orders = service.listForExport(new OrderInfo());

		assertThat(orders.get(0).getOrderItemList()).hasSize(1);
		assertThat(orders.get(0).getOrderItemList().get(0).getCategoryName()).isNull();
	}

	@Test
	void getOrderByIdBackfillsCategory() {
		OrderInfo order = new OrderInfo();
		order.setId("order-a");
		order.setOrderItemList(List.of(item("order-a", "spu-1", "矿泉水", 1, "3.00")));
		when(orderInfoMapper.selectOrderById("order-a")).thenReturn(order);
		when(remoteGoodsSpuService.getSpuByIds(any())).thenReturn(List.of(spu("spu-1", "饮品/水")));

		OrderInfo result = service.getOrderById("order-a");

		assertThat(result.getOrderItemList().get(0).getCategoryName()).isEqualTo("饮品/水");
	}

}
