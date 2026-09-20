package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.enums.DeliveryWayEnum;
import com.aryn.cloud.order.api.enums.PurchaseSceneEnum;
import com.aryn.cloud.order.validator.DeliveryContextValidator;
import com.aryn.cloud.order.validator.PurchaseSceneValidator;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单配送上下文校验契约测试。
 */
class OrderDeliveryContextTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private static final String VESSEL_ID = "vessel-1";

	private static final String CALL_ID = "call-1";

	private RemoteVesselService remoteVesselService;

	private PurchaseSceneValidator purchaseSceneValidator;

	private DeliveryContextValidator deliveryContextValidator;

	@BeforeEach
	void setUp() {
		remoteVesselService = mock(RemoteVesselService.class);
		purchaseSceneValidator = new PurchaseSceneValidator();
		deliveryContextValidator = new DeliveryContextValidator();
		ReflectionTestUtils.setField(deliveryContextValidator, "remoteVesselService", remoteVesselService);
	}

	private VesselContextDTO context() {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselId(VESSEL_ID);
		context.setVesselName("测试轮");
		context.setVesselCallId(CALL_ID);
		context.setPortCode("CNSHA");
		context.setPortName("上海港");
		context.setBerth("3号泊位");
		context.setEta(LocalDateTime.now().plusDays(1));
		context.setEtd(LocalDateTime.now().plusDays(1).plusHours(12));
		context.setDeliveryWindowStart(LocalDateTime.now().plusDays(1).plusHours(2));
		context.setDeliveryWindowEnd(LocalDateTime.now().plusDays(1).plusHours(6));
		return context;
	}

	@Test
	@DisplayName("delivery_way=4 缺少船舶或靠港计划时校验失败")
	void internalDeliveryRequiresVesselContext() {
		assertThrows(ArynBusinessException.class,
				() -> deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.INTERNAL_PORT.getCode(), null,
						CALL_ID));
		assertThrows(ArynBusinessException.class,
				() -> deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.INTERNAL_PORT.getCode(),
						VESSEL_ID, null));
	}

	@Test
	@DisplayName("用户不是船舶成员时创建配送上下文失败")
	void internalDeliveryRequiresMembership() {
		when(remoteVesselService.isVesselMember(TENANT, VESSEL_ID, USER)).thenReturn(false);
		assertThrows(ArynBusinessException.class,
				() -> deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.INTERNAL_PORT.getCode(),
						VESSEL_ID, CALL_ID));
	}

	@Test
	@DisplayName("靠港计划不存在或已过期时校验失败")
	void internalDeliveryRequiresOrderableCall() {
		when(remoteVesselService.isVesselMember(TENANT, VESSEL_ID, USER)).thenReturn(true);
		when(remoteVesselService.getVesselCallContext(TENANT, CALL_ID)).thenReturn(null);
		assertThrows(ArynBusinessException.class,
				() -> deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.INTERNAL_PORT.getCode(),
						VESSEL_ID, CALL_ID));
	}

	@Test
	@DisplayName("靠港计划与船舶不匹配时校验失败")
	void internalDeliveryRequiresMatchingVessel() {
		when(remoteVesselService.isVesselMember(TENANT, VESSEL_ID, USER)).thenReturn(true);
		VesselContextDTO mismatch = context();
		mismatch.setVesselId("vessel-other");
		when(remoteVesselService.getVesselCallContext(TENANT, CALL_ID)).thenReturn(mismatch);
		assertThrows(ArynBusinessException.class,
				() -> deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.INTERNAL_PORT.getCode(),
						VESSEL_ID, CALL_ID));
	}

	@Test
	@DisplayName("内部配送校验通过返回服务端港口/泊位/时间窗快照")
	void internalDeliveryReturnsServerSideContext() {
		when(remoteVesselService.isVesselMember(TENANT, VESSEL_ID, USER)).thenReturn(true);
		when(remoteVesselService.getVesselCallContext(TENANT, CALL_ID)).thenReturn(context());
		VesselContextDTO result = deliveryContextValidator.validate(TENANT, USER,
				DeliveryWayEnum.INTERNAL_PORT.getCode(), VESSEL_ID, CALL_ID);
		assertNotNull(result);
		assertEquals("上海港", result.getPortName());
		assertEquals("3号泊位", result.getBerth());
		assertNotNull(result.getDeliveryWindowStart());
		assertNotNull(result.getDeliveryWindowEnd());
	}

	@Test
	@DisplayName("普通零售配送方式不触发内部配送校验")
	void legacyDeliveryWaysPassThrough() {
		assertNull(deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.EXPRESS.getCode(), null, null));
		assertNull(deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.SELF_PICKUP.getCode(), null, null));
		assertNull(deliveryContextValidator.validate(TENANT, USER, DeliveryWayEnum.MALL_DELIVERY.getCode(), null,
				null));
	}

	@Test
	@DisplayName("船供采购不能选择普通快递等非内部配送方式")
	void shipSupplyRequiresInternalDelivery() {
		assertThrows(ArynBusinessException.class,
				() -> purchaseSceneValidator.validate(PurchaseSceneEnum.SHIP_SUPPLY.getCode(),
						DeliveryWayEnum.EXPRESS.getCode()));
		assertThrows(ArynBusinessException.class,
				() -> purchaseSceneValidator.validate(PurchaseSceneEnum.SHIP_SUPPLY.getCode(),
						DeliveryWayEnum.MALL_DELIVERY.getCode()));
	}

	@Test
	@DisplayName("个人购买与普通零售租户仍可使用原有配送方式")
	void legacyScenesRemainCompatible() {
		purchaseSceneValidator.validate(null, DeliveryWayEnum.EXPRESS.getCode());
		purchaseSceneValidator.validate(PurchaseSceneEnum.PERSONAL.getCode(), DeliveryWayEnum.EXPRESS.getCode());
		purchaseSceneValidator.validate(PurchaseSceneEnum.PERSONAL.getCode(), DeliveryWayEnum.MALL_DELIVERY.getCode());
		purchaseSceneValidator.validate(PurchaseSceneEnum.SHIP_SUPPLY.getCode(),
				DeliveryWayEnum.INTERNAL_PORT.getCode());
	}

	@Test
	@DisplayName("无效购买场景直接拒绝")
	void invalidSceneRejected() {
		assertThrows(ArynBusinessException.class, () -> purchaseSceneValidator.validate("9",
				DeliveryWayEnum.INTERNAL_PORT.getCode()));
	}

}
