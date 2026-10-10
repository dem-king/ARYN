package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.logistics.util.Kuaidi100Utils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.vo.ReorderPreviewVO;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.PromotionSnapshotMapper;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.order.validator.DeliveryContextValidator;
import com.aryn.cloud.order.validator.PurchaseSceneValidator;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 再来一单预览的可购判定。
 *
 * <p>回归：getBySkuIds 底层 selectListByIds 只返回在售 SKU（goods_sku.status='0' 且 goods_spu.status='1'，
 * 两张表 status 语义相反），在售 SKU 的 status 恒为 '0'。曾按「'1' 才是上架」判定，
 * 导致所有订单的再来一单恒报「商品已下架」；mock 数据必须镜像真实 SQL 口径（在售回 status='0'）。
 */
class OrderInfoReorderPreviewTest {

	private OrderInfoServiceImpl service;

	private OrderInfoMapper orderInfoMapper;

	private OrderItemMapper orderItemMapper;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItemEntity.class);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		orderItemMapper = mock(OrderItemMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);

		service = new OrderInfoServiceImpl(mock(IOrderConfigService.class), mock(Kuaidi100Utils.class),
				orderItemMapper, remoteGoodsSkuService,
				mock(com.aryn.cloud.product.api.remote.RemoteGoodsSpuService.class),
				mock(RemoteMallUserService.class), mock(IOrderItemService.class), mock(IShoppingCartService.class),
				mock(com.aryn.cloud.pay.api.remote.RemotePayService.class), mock(ApplicationEventPublisher.class),
				mock(OrderPriceComputeService.class), mock(OrderWxDeliveryService.class),
				mock(OrderStatisticsQueryService.class), mock(OrderAppraiseService.class),
				mock(RemoteUserAddressService.class), mock(com.aryn.cloud.order.mapper.OrderDeliveryMapper.class),
				mock(com.aryn.cloud.order.mapper.OrderRefundMapper.class),
				mock(RemoteCouponUserService.class), mock(RemoteSeckillService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteGroupBuyService.class), mock(RocketMQTemplate.class),
				mock(CallbackPrefixProperties.class), mock(IDeliveryTaskService.class),
				mock(com.aryn.cloud.order.service.IOrderDeliveryStateService.class),
				mock(com.aryn.cloud.order.service.IDeliveryAreaService.class), mock(PromotionSnapshotMapper.class),
				mock(PurchaseSceneValidator.class), mock(DeliveryContextValidator.class),
				mock(com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier.class),
				mock(RemotePromotionEngine.class),
				mock(com.aryn.cloud.upms.api.remote.RemoteMaterialService.class), mock(ObjectProvider.class));

		orderInfoMapper = mock(OrderInfoMapper.class);
		ReflectionTestUtils.setField(service, "baseMapper", orderInfoMapper);
	}

	private void stubOrderFound() {
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("NO-1");
		order.setPurchaseScene("1");
		// ServiceImpl.getOne 落到 BaseMapper.selectOne 的单参或双参重载因 MP 版本而异，两个都桩上
		when(orderInfoMapper.selectOne(any(Wrapper.class))).thenReturn(order);
		when(orderInfoMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(order);
	}

	private OrderItemEntity item(String skuId, String spuId, int quantity, String price) {
		OrderItemEntity item = new OrderItemEntity();
		item.setSkuId(skuId);
		item.setSpuId(spuId);
		item.setSpuName("汾酒青花20");
		item.setBuyQuantity(quantity);
		item.setSalesPrice(new BigDecimal(price));
		return item;
	}

	/**
	 * 在售 SKU 必须镜像真实 SQL 口径：selectListByIds 只回 status='0' 的行。
	 */
	private GoodsSku sku(String id, String price, int stock, String status) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSalesPrice(new BigDecimal(price));
		sku.setStock(stock);
		sku.setStatus(status);
		return sku;
	}

	@Test
	void onShelfSkuIsPurchasable() {
		stubOrderFound();
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item("sku-1", "spu-1", 3, "1194.00")));
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1")))
				.thenReturn(List.of(sku("sku-1", "1194.00", 996, "0")));

		ReorderPreviewVO preview = service.reorderPreview("tenant-1", "user-1", "order-1");

		ReorderPreviewVO.ReorderItem reorderItem = preview.getItems().get(0);
		assertThat(reorderItem.getPurchasable()).isTrue();
		assertThat(reorderItem.getReason()).isNull();
		assertThat(reorderItem.getPriceChanged()).isFalse();
		assertThat(reorderItem.getCurrentPrice()).isEqualByComparingTo("1194.00");
		assertThat(reorderItem.getCurrentStock()).isEqualTo(996);
		assertThat(reorderItem.getOriginalQuantity()).isEqualTo(3);
	}

	@Test
	void skuMissingFromLookupIsReportedOffShelfOrDeleted() {
		// selectListByIds 已过滤下架/删除：查不到的 SKU 不在返回里，而不是带着别的 status 回来
		stubOrderFound();
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item("sku-1", "spu-1", 3, "1194.00")));
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of());

		ReorderPreviewVO preview = service.reorderPreview("tenant-1", "user-1", "order-1");

		ReorderPreviewVO.ReorderItem reorderItem = preview.getItems().get(0);
		assertThat(reorderItem.getPurchasable()).isFalse();
		assertThat(reorderItem.getReason()).isEqualTo("商品已下架或已删除");
	}

	@Test
	void insufficientStockIsReported() {
		stubOrderFound();
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item("sku-1", "spu-1", 3, "1194.00")));
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1")))
				.thenReturn(List.of(sku("sku-1", "1194.00", 1, "0")));

		ReorderPreviewVO preview = service.reorderPreview("tenant-1", "user-1", "order-1");

		ReorderPreviewVO.ReorderItem reorderItem = preview.getItems().get(0);
		assertThat(reorderItem.getPurchasable()).isFalse();
		assertThat(reorderItem.getReason()).isEqualTo("库存不足");
	}

	@Test
	void priceChangeIsFlaggedButStillPurchasable() {
		stubOrderFound();
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item("sku-1", "spu-1", 3, "1194.00")));
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1")))
				.thenReturn(List.of(sku("sku-1", "1200.00", 996, "0")));

		ReorderPreviewVO preview = service.reorderPreview("tenant-1", "user-1", "order-1");

		ReorderPreviewVO.ReorderItem reorderItem = preview.getItems().get(0);
		assertThat(reorderItem.getPurchasable()).isTrue();
		assertThat(reorderItem.getPriceChanged()).isTrue();
	}

	@Test
	void missingOrderThrows() {
		when(orderInfoMapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(orderInfoMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteGoodsSkuService.getBySkuIds(anyList())).thenReturn(List.of());

		assertThatThrownBy(() -> service.reorderPreview("tenant-1", "user-1", "order-1"))
				.isInstanceOf(ArynBusinessException.class);
	}

}
