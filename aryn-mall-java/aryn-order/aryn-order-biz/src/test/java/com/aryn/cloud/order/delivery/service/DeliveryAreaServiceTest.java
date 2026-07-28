package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryArea;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryAreaMapper;
import com.aryn.cloud.order.service.impl.OrderPriceComputeService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAreaServiceTest {

	@Mock
	private OrderDeliveryAreaMapper areaMapper;

	@Mock
	private RemoteUserAddressService remoteUserAddressService;

	@Mock
	private OrderPriceComputeService orderPriceComputeService;

	private DeliveryAreaService areaService;

	private DeliveryCheckoutService checkoutService;

	@BeforeEach
	void setUp() {
		areaService = new DeliveryAreaService(areaMapper);
		checkoutService = new DeliveryCheckoutService(remoteUserAddressService, areaService, orderPriceComputeService);
	}

	@Test
	void districtRuleWinsWithoutConsultingBroaderRules() {
		UserAddress address = address();
		OrderDeliveryArea district = new OrderDeliveryArea().setScopeLevel("DISTRICT").setAreaCode("district-1");
		when(areaMapper.selectEnabledByScope("DISTRICT", "district-1")).thenReturn(district);

		assertThat(areaService.findBestMatch(address)).containsSame(district);

		verify(areaMapper, never()).selectEnabledByScope("CITY", "city-1");
		verify(areaMapper, never()).selectEnabledByScope("PROVINCE", "province-1");
	}

	@Test
	void cityRuleCoversAddressWhenNoDistrictRuleExists() {
		UserAddress address = address();
		OrderDeliveryArea city = new OrderDeliveryArea().setScopeLevel("CITY").setAreaCode("city-1");
		when(areaMapper.selectEnabledByScope("DISTRICT", "district-1")).thenReturn(null);
		when(areaMapper.selectEnabledByScope("CITY", "city-1")).thenReturn(city);

		assertThat(areaService.findBestMatch(address)).containsSame(city);
	}

	@Test
	void tenantWithoutEnabledRulesCannotUseMallDelivery() {
		UserAddress address = address();

		assertThat(areaService.isAvailable(address)).isFalse();
		assertThatThrownBy(() -> areaService.requireAvailable(address))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(error -> assertThat(((ArynBusinessException) error).getMsg())
						.contains("不在商城配送范围"));
	}

	@Test
	void mallDeliveryLoadsOwnedAddressCopiesSnapshotAndUsesExistingFreightCalculation() {
		UserAddress address = address()
				.setRecipientName("张三")
				.setTelephone("13800000000")
				.setProvinceName("测试省")
				.setCityName("测试市")
				.setAreaName("测试区")
				.setDetailAddress("测试路1号");
		when(remoteUserAddressService.getById("address-1", "user-1")).thenReturn(address);
		when(areaMapper.selectEnabledByScope("DISTRICT", "district-1"))
				.thenReturn(new OrderDeliveryArea().setScopeLevel("DISTRICT"));
		OrderInfo order = new OrderInfo().setDeliveryWay("3");
		List<OrderItemEntity> items = List.of(new OrderItemEntity());
		List<GoodsSku> skus = List.of(new GoodsSku());

		checkoutService.applyDeliveryAddressAndFreight("3", "address-1", "user-1", order, items, skus, false);

		assertThat(order.getRecipientName()).isEqualTo("张三");
		assertThat(order.getRecipientProvinceCode()).isEqualTo("province-1");
		assertThat(order.getRecipientCityCode()).isEqualTo("city-1");
		assertThat(order.getRecipientAreaCode()).isEqualTo("district-1");
		verify(orderPriceComputeService).orderFreightHandler(order, items, skus, false);
	}

	private UserAddress address() {
		return new UserAddress()
				.setId("address-1")
				.setUserId("user-1")
				.setProvinceCode("province-1")
				.setCityCode("city-1")
				.setAreaCode("district-1");
	}

}
