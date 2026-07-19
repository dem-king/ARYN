package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.order.api.entity.OrderDelivery;
import com.aryn.cloud.order.mapper.OrderDeliveryLogisticsMapper;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.kuaidi100.sdk.response.SubscribePushParamResp;
import com.kuaidi100.sdk.response.SubscribePushResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderDeliveryServiceImplTest {

	private OrderDeliveryLogisticsMapper logisticsMapper;
	private OrderDeliveryServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderDelivery.class);
		OrderDeliveryMapper deliveryMapper = mock(OrderDeliveryMapper.class);
		logisticsMapper = mock(OrderDeliveryLogisticsMapper.class);
		when(deliveryMapper.updateById(any(OrderDelivery.class))).thenReturn(1);
		when(logisticsMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		service = new TestOrderDeliveryService(logisticsMapper, deliveryMapper);
	}

	@Test
	void logisticsCallbackNeverOverwritesOrderIdWithDeliveryState() {
		OrderDelivery delivery = new OrderDelivery();
		delivery.setId("delivery-1");
		delivery.setOrderId("order-1");
		SubscribePushResult result = new SubscribePushResult();
		result.setState("3");
		result.setIscheck("1");
		result.setData(List.of());
		SubscribePushParamResp response = new SubscribePushParamResp();
		response.setStatus("polling");
		response.setLastResult(result);

		service.notifyLogistics(delivery, response);

		assertThat(delivery.getOrderId()).isEqualTo("order-1");
		assertThat(delivery.getIsCheck()).isEqualTo("1");
		assertThat(delivery.getDeliveryStatus()).isEqualTo("3");
	}

	private static final class TestOrderDeliveryService extends OrderDeliveryServiceImpl {

		private TestOrderDeliveryService(OrderDeliveryLogisticsMapper logisticsMapper,
				OrderDeliveryMapper deliveryMapper) {
			super(logisticsMapper);
			this.baseMapper = deliveryMapper;
		}
	}
}
