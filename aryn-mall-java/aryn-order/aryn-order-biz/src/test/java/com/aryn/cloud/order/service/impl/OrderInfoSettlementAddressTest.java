package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.logistics.util.Kuaidi100Utils;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SettlementOrderDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
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
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算预览对「无收货地址」的容忍性。
 *
 * <p>回归（真实缺陷）：用户没有收货地址时进结算页，settlementOrder 对配送方式 1/3
 * 直接抛「用户收货地址不存在」，整次结算失败——确认页商品列表零行、金额 ¥0.00；
 * 用户补完地址回来也没有任何恢复入口（前端只在选址时重新结算，而页面已空）。
 * 修复口径：结算为预览，无地址（未传 id 或所选地址已删除）时跳过收件人回填与
 * 运费计算（运费保持 0，确认页提示「选择地址后计算」），提交订单 createOrder
 * 仍强校验地址。
 */
class OrderInfoSettlementAddressTest {

	private OrderInfoServiceImpl service;

	private OrderInfoMapper orderInfoMapper;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private RemoteMallUserService remoteMallUserService;

	private RemoteUserAddressService remoteUserAddressService;

	private OrderPriceComputeService orderPriceComputeService;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItemEntity.class);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		orderInfoMapper = mock(OrderInfoMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		remoteUserAddressService = mock(RemoteUserAddressService.class);
		orderPriceComputeService = mock(OrderPriceComputeService.class);

		com.aryn.cloud.order.service.OrderStaffNotifyService staffNotifyService = mock(com.aryn.cloud.order.service.OrderStaffNotifyService.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<com.aryn.cloud.order.service.ISharedCartService> sharedCartProvider = mock(ObjectProvider.class);
		service = new OrderInfoServiceImpl(mock(IOrderConfigService.class), mock(Kuaidi100Utils.class),
				mock(OrderItemMapper.class), remoteGoodsSkuService,
				mock(com.aryn.cloud.product.api.remote.RemoteGoodsSpuService.class),
				remoteMallUserService, mock(com.aryn.cloud.user.api.remote.RemotePointsService.class),
				mock(IOrderItemService.class), mock(IShoppingCartService.class),
				mock(com.aryn.cloud.pay.api.remote.RemotePayService.class), mock(ApplicationEventPublisher.class),
				orderPriceComputeService, mock(OrderWxDeliveryService.class),
				mock(OrderStatisticsQueryService.class), mock(OrderAppraiseService.class),
				remoteUserAddressService, mock(com.aryn.cloud.order.mapper.OrderDeliveryMapper.class),
				mock(com.aryn.cloud.order.mapper.OrderRefundMapper.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteCouponUserService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteSeckillService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteGroupBuyService.class), mock(RocketMQTemplate.class),
				mock(CallbackPrefixProperties.class), mock(IDeliveryTaskService.class),
				mock(com.aryn.cloud.order.service.IOrderDeliveryStateService.class),
				mock(com.aryn.cloud.order.service.IDeliveryAreaService.class), mock(PromotionSnapshotMapper.class),
				mock(PurchaseSceneValidator.class), mock(DeliveryContextValidator.class),
				mock(com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier.class),
				mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class),
				mock(com.aryn.cloud.upms.api.remote.RemoteMaterialService.class), sharedCartProvider, staffNotifyService,
				mock(com.aryn.cloud.promotion.api.remote.RemoteSalesAttributionService.class));

		ReflectionTestUtils.setField(service, "baseMapper", orderInfoMapper);
	}

	private void stubHappyPath() {
		when(remoteMallUserService.getUserById("user-1")).thenReturn(new UserInfoVO());
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(sku()));
		when(orderPriceComputeService.generateOrderItems(anyList(), anyList())).thenReturn(List.of(item()));
		when(orderInfoMapper.insert(any(OrderInfo.class))).thenReturn(1);
	}

	private SettlementOrderDTO dto(String deliveryWay, String userAddressId) {
		SettlementOrderDTO dto = new SettlementOrderDTO();
		dto.setUserId("user-1");
		dto.setDeliveryWay(deliveryWay);
		dto.setCreateWay("1");
		dto.setUserAddressId(userAddressId);
		CreateOrderSkuReqDTO skuReq = new CreateOrderSkuReqDTO();
		skuReq.setSpuId("spu-1");
		skuReq.setSkuId("sku-1");
		skuReq.setQuantity(2);
		dto.setSkuReqList(List.of(skuReq));
		return dto;
	}

	private GoodsSku sku() {
		GoodsSku sku = new GoodsSku();
		sku.setId("sku-1");
		sku.setSalesPrice(new BigDecimal("10.00"));
		sku.setStock(99);
		// 在售口径镜像真实 SQL：selectListByIds 只回 status='0'
		sku.setStatus("0");
		return sku;
	}

	private OrderItemEntity item() {
		OrderItemEntity item = new OrderItemEntity();
		item.setSkuId("sku-1");
		item.setSpuId("spu-1");
		item.setBuyQuantity(2);
		item.setSalesPrice(new BigDecimal("10.00"));
		item.setFreightPrice(BigDecimal.ZERO);
		return item;
	}

	private UserAddress address() {
		UserAddress address = new UserAddress();
		address.setId("addr-1");
		address.setUserId("user-1");
		address.setRecipientName("张三");
		address.setTelephone("13222222222");
		address.setProvinceName("北京市");
		address.setCityName("市辖区");
		address.setAreaName("东城区");
		address.setProvinceCode("110000");
		address.setCityCode("110100");
		address.setAreaCode("110101");
		address.setDetailAddress("测试地址 1 号");
		return address;
	}

	@Test
	@DisplayName("无地址结算（way=3）：不抛错，运费保持 0，不回填收件人，不算运费")
	void settlementWithoutAddressSkipsFreight() {
		stubHappyPath();

		OrderInfo result = service.settlementOrder(dto("3", null));

		assertThat(result.getFreightPrice()).isEqualByComparingTo("0");
		assertThat(result.getRecipientName()).isNull();
		assertThat(result.getRecipientPhone()).isNull();
		assertThat(result.getOrderItemList()).hasSize(1);
		verify(remoteUserAddressService, never()).getById(any(), any());
		verify(orderPriceComputeService, never()).orderFreightHandler(any(), anyList(), anyList(), anyBoolean());
	}

	@Test
	@DisplayName("所选地址已删除（查不到）：同样容忍，结算不失败")
	void settlementWithDeletedAddressStillSucceeds() {
		stubHappyPath();
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(null);

		OrderInfo result = service.settlementOrder(dto("3", "addr-1"));

		assertThat(result.getFreightPrice()).isEqualByComparingTo("0");
		assertThat(result.getRecipientName()).isNull();
	}

	@Test
	@DisplayName("有地址结算（way=1）：回填收件人并计算运费，与下单口径一致")
	void settlementWithAddressFillsRecipientAndFreight() {
		stubHappyPath();
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(address());

		OrderInfo result = service.settlementOrder(dto("1", "addr-1"));

		assertThat(result.getRecipientName()).isEqualTo("张三");
		assertThat(result.getRecipientPhone()).isEqualTo("13222222222");
		assertThat(result.getRecipientAddress()).isEqualTo("测试地址 1 号");
		verify(orderPriceComputeService).orderFreightHandler(any(), anyList(), anyList(), anyBoolean());
	}

	@Test
	@DisplayName("结算回显备注：确认页展示与提交使用同一份备注，空字符串也原样回显")
	void settlementEchoesRemark() {
		stubHappyPath();
		SettlementOrderDTO dto = dto("3", null);
		dto.setRemark("本次补给含甲板部需求");
		assertThat(service.settlementOrder(dto).getRemark()).isEqualTo("本次补给含甲板部需求");

		SettlementOrderDTO cleared = dto("3", null);
		cleared.setRemark("");
		assertThat(service.settlementOrder(cleared).getRemark()).isEmpty();
	}
}
