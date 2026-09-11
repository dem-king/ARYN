package com.aryn.cloud.order.api.enums;

import com.aryn.cloud.order.api.dto.DeliveryContextDTO;
import com.aryn.cloud.order.api.dto.PurchaseContextDTO;
import com.aryn.cloud.product.api.enums.SaleScopeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 船供化领域契约测试：固定购买场景、配送方式和销售范围的取值语义。
 */
class DomainEnumTest {

	@Test
	@DisplayName("purchase_scene=1 表示海员个人购买")
	void purchaseScenePersonal() {
		assertEquals("1", PurchaseSceneEnum.PERSONAL.getCode());
		assertEquals("海员个人购买", PurchaseSceneEnum.getValue("1"));
	}

	@Test
	@DisplayName("purchase_scene=2 表示船供采购")
	void purchaseSceneShipSupply() {
		assertEquals("2", PurchaseSceneEnum.SHIP_SUPPLY.getCode());
		assertEquals("船供采购", PurchaseSceneEnum.getValue("2"));
	}

	@Test
	@DisplayName("delivery_way=4 表示公司港口/船舶内部配送")
	void deliveryWayInternalPort() {
		assertEquals("4", DeliveryWayEnum.INTERNAL_PORT.getCode());
		assertEquals("公司港口/船舶内部配送", DeliveryWayEnum.getValue("4"));
	}

	@DisplayName("delivery_way 原有取值保持兼容")
	@Test
	void deliveryWayLegacyValues() {
		assertEquals("普通快递", DeliveryWayEnum.getValue("1"));
		assertEquals("上门自提", DeliveryWayEnum.getValue("2"));
		assertEquals("商城配送", DeliveryWayEnum.getValue("3"));
	}

	@DisplayName("sale_scope=1/2/3 分别表示个人、船供、两者可用")
	@Test
	void saleScopeValues() {
		assertEquals("1", SaleScopeEnum.PERSONAL_ONLY.getCode());
		assertEquals("2", SaleScopeEnum.SHIP_SUPPLY_ONLY.getCode());
		assertEquals("3", SaleScopeEnum.BOTH.getCode());
		assertEquals("仅个人购买", SaleScopeEnum.getValue("1"));
		assertEquals("仅船供采购", SaleScopeEnum.getValue("2"));
		assertEquals("个人购买和船供采购", SaleScopeEnum.getValue("3"));
	}

	@DisplayName("无效枚举值返回空，不能静默转成普通快递")
	@Test
	void invalidCodeReturnsNull() {
		assertNull(PurchaseSceneEnum.getValue("9"));
		assertNull(DeliveryWayEnum.getValue("9"));
		assertNull(SaleScopeEnum.getValue("9"));
		assertNull(DeliveryWayEnum.getValue(null));
	}

	@DisplayName("DeliveryContextDTO 承载船舶、港口、泊位、时间窗、收货人和代理联系方式")
	@Test
	void deliveryContextDtoFields() {
		DeliveryContextDTO dto = new DeliveryContextDTO();
		dto.setVesselId("1001");
		dto.setVesselCallId("2001");
		dto.setPortCode("CNSHA");
		dto.setPortName("上海港");
		dto.setBerth("3号泊位");
		LocalDateTime start = LocalDateTime.of(2026, 9, 12, 8, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 12, 12, 0);
		dto.setDeliveryWindowStart(start);
		dto.setDeliveryWindowEnd(end);
		dto.setReceiverName("张船长");
		dto.setReceiverPhone("13800000000");
		dto.setAgentName("李代理");
		dto.setAgentPhone("13900000000");

		assertEquals("1001", dto.getVesselId());
		assertEquals("2001", dto.getVesselCallId());
		assertEquals("CNSHA", dto.getPortCode());
		assertEquals("上海港", dto.getPortName());
		assertEquals("3号泊位", dto.getBerth());
		assertEquals(start, dto.getDeliveryWindowStart());
		assertEquals(end, dto.getDeliveryWindowEnd());
		assertEquals("张船长", dto.getReceiverName());
		assertEquals("13800000000", dto.getReceiverPhone());
		assertEquals("李代理", dto.getAgentName());
		assertEquals("13900000000", dto.getAgentPhone());
	}

	@DisplayName("PurchaseContextDTO 承载购买场景、船舶上下文和购物车类型")
	@Test
	void purchaseContextDtoFields() {
		PurchaseContextDTO dto = new PurchaseContextDTO();
		dto.setPurchaseScene("2");
		dto.setVesselId("1001");
		dto.setVesselCallId("2001");
		dto.setCartType("1");

		assertEquals("2", dto.getPurchaseScene());
		assertEquals("1001", dto.getVesselId());
		assertEquals("2001", dto.getVesselCallId());
		assertEquals("1", dto.getCartType());
	}

	@DisplayName("内部配送 DTO 不包含用户收货地址字段")
	@Test
	void deliveryContextDoesNotCarryUserAddress() {
		DeliveryContextDTO dto = new DeliveryContextDTO();
		dto.setVesselId("1001");
		dto.setVesselCallId("2001");
		dto.setPortCode("CNSHA");
		dto.setPortName("上海港");
		dto.setBerth("3号泊位");

		assertNotNull(dto.getVesselId());
		assertNotNull(dto.getPortCode());
		assertTrue(java.util.Arrays.stream(DeliveryContextDTO.class.getDeclaredFields())
				.noneMatch(field -> "userAddressId".equals(field.getName())),
				"内部配送上下文不得引入用户地址字段，配送上下文以船舶/港口为准");
	}

}
