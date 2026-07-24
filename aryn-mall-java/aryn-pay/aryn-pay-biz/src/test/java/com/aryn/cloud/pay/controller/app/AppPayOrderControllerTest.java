package com.aryn.cloud.pay.controller.app;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppPayOrderControllerTest {

	@Test
	void queryRequiresTocIdentityAndScopesOrderToCurrentUser() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayTradeOrder.class);
		IPayTradeOrderService service = mock(IPayTradeOrderService.class);
		PayTradeOrder order = new PayTradeOrder().setOutTradeNo("ORDER-1").setPayStatus("1")
			.setAmount(new BigDecimal("99.00")).setExtra("{\"payType\":\"1\"}")
			.setOpenId("sensitive-open-id").setNotifyUrl("https://internal/notify");
		when(service.getOne(any(Wrapper.class))).thenReturn(order);
		AppPayOrderController controller = new AppPayOrderController(service);
		ArynUser user = new ArynUser();
		user.setUserId("user-1");
		user.setDeviceType(DeviceTypeEnum.TOC);

		try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
			securityUtils.when(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOC)).thenReturn(user);
			Result<?> response = controller.getOrder("ORDER-1");

			securityUtils.verify(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOC));
			Map<?, ?> data = (Map<?, ?>) response.getData();
			assertTrue(data.containsKey("payStatus"));
			assertFalse(data.containsKey("openId"));
			assertFalse(data.containsKey("notifyUrl"));
		}

		ArgumentCaptor<Wrapper<PayTradeOrder>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(service).getOne(wrapperCaptor.capture());
		Wrapper<PayTradeOrder> wrapper = wrapperCaptor.getValue();
		assertTrue(wrapper.getSqlSegment().contains("user_id"));
		AbstractWrapper<?, ?, ?> abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
		assertTrue(abstractWrapper.getParamNameValuePairs().containsValue("user-1"));
	}

}
