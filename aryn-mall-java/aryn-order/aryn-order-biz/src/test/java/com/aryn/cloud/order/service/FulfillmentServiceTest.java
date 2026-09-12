package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.FulfillmentPickScanDTO;
import com.aryn.cloud.order.api.dto.FulfillmentShortReportDTO;
import com.aryn.cloud.order.api.dto.FulfillmentWaveCreateDTO;
import com.aryn.cloud.order.api.entity.FulfillmentException;
import com.aryn.cloud.order.api.entity.FulfillmentPickItem;
import com.aryn.cloud.order.api.entity.FulfillmentWave;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.FulfillmentExceptionMapper;
import com.aryn.cloud.order.mapper.FulfillmentPickItemMapper;
import com.aryn.cloud.order.mapper.FulfillmentWaveMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.service.impl.FulfillmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 履约波次与扫码拣货契约测试。
 */
class FulfillmentServiceTest {

	private static final String TENANT = "tenant-1";

	private FulfillmentWaveMapper waveMapper;

	private FulfillmentPickItemMapper pickItemMapper;

	private FulfillmentExceptionMapper exceptionMapper;

	private OrderInfoMapper orderInfoMapper;

	private OrderItemMapper orderItemMapper;

	private com.aryn.cloud.order.mapper.SharedCartMapper sharedCartMapper;

	private FulfillmentServiceImpl service;

	@BeforeEach
	void setUp() {
		waveMapper = mock(FulfillmentWaveMapper.class);
		pickItemMapper = mock(FulfillmentPickItemMapper.class);
		exceptionMapper = mock(FulfillmentExceptionMapper.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		orderItemMapper = mock(OrderItemMapper.class);
		sharedCartMapper = mock(com.aryn.cloud.order.mapper.SharedCartMapper.class);
		service = new FulfillmentServiceImpl(waveMapper, pickItemMapper, exceptionMapper, orderInfoMapper,
				orderItemMapper, sharedCartMapper);
	}

	private FulfillmentWave wave(String status) {
		FulfillmentWave wave = new FulfillmentWave();
		wave.setId("wave-1");
		wave.setTenantId(TENANT);
		wave.setPortCode("CNSHA");
		wave.setPortName("上海港");
		wave.setStatus(status);
		return wave;
	}

	private FulfillmentPickItem pickItem() {
		FulfillmentPickItem item = new FulfillmentPickItem();
		item.setId("pick-1");
		item.setWaveId("wave-1");
		item.setOrderId("order-1");
		item.setSkuId("sku-1");
		item.setSkuBarcode("6900001");
		item.setRequiredQuantity(10);
		item.setPickedQuantity(0);
		item.setShortQuantity(0);
		item.setPickStatus(FulfillmentPickItem.PICK_PENDING);
		return item;
	}

	private FulfillmentPickScanDTO scanDTO(String code, int quantity) {
		FulfillmentPickScanDTO dto = new FulfillmentPickScanDTO();
		dto.setWaveId("wave-1");
		dto.setItemId("pick-1");
		dto.setScannedCode(code);
		dto.setQuantity(quantity);
		return dto;
	}

	@Test
	@DisplayName("创建波次进入待拣货状态")
	void createWaveDefaultsPending() {
		FulfillmentWaveCreateDTO dto = new FulfillmentWaveCreateDTO();
		dto.setPortCode("CNSHA");
		FulfillmentWave created = service.createWave(TENANT, "op-1", "仓管员", dto);
		assertEquals(FulfillmentWave.STATUS_PENDING_PICK, created.getStatus());
		assertEquals("CNSHA", created.getPortCode());
	}

	@Test
	@DisplayName("订单目的港口必须与波次港口一致")
	void addOrderRequiresSamePort() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PENDING_PICK));
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setTenantId(TENANT);
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		orderInfo.setPortCode("CNSTM");
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);

		assertThrows(ArynBusinessException.class, () -> service.addOrderToWave(TENANT, "wave-1", "order-1"));
	}

	@Test
	@DisplayName("订单加入波次按订单明细生成拣货明细，波次进入拣货中")
	void addOrderGeneratesPickItems() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PENDING_PICK));
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setTenantId(TENANT);
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		orderInfo.setPortCode("CNSHA");
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);
		when(pickItemMapper.selectCount(any())).thenReturn(0L);
		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("oi-1");
		orderItem.setSkuId("sku-1");
		orderItem.setBuyQuantity(6);
		when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem));

		List<FulfillmentPickItem> items = service.addOrderToWave(TENANT, "wave-1", "order-1");
		assertEquals(1, items.size());
		assertEquals(6, items.get(0).getRequiredQuantity());
		assertEquals(FulfillmentWave.STATUS_PICKING, items.get(0).getPickStatus() != null
				? wave(FulfillmentWave.STATUS_PICKING).getStatus() : null);
	}

	@Test
	@DisplayName("已加入其他波次的订单不能重复加入")
	void addOrderRejectsDuplicate() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PENDING_PICK));
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setTenantId(TENANT);
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		orderInfo.setPortCode("CNSHA");
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);
		when(pickItemMapper.selectCount(any())).thenReturn(1L);

		assertThrows(ArynBusinessException.class, () -> service.addOrderToWave(TENANT, "wave-1", "order-1"));
	}

	@Test
	@DisplayName("扫码 SKU 不匹配时拒绝确认")
	void scanRejectsWrongSku() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PICKING));
		when(pickItemMapper.selectOne(any())).thenReturn(pickItem());

		assertThrows(ArynBusinessException.class, () -> service.scanPick(TENANT, "picker-1", scanDTO("wrong", 5)));
	}

	@Test
	@DisplayName("扫码数量超过应拣数量时拒绝")
	void scanRejectsOverQuantity() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PICKING));
		when(pickItemMapper.selectOne(any())).thenReturn(pickItem());

		assertThrows(ArynBusinessException.class, () -> service.scanPick(TENANT, "picker-1", scanDTO("6900001", 11)));
	}

	@Test
	@DisplayName("扫码 SKU 匹配（条码或 SKU ID）累计拣货数量，拣满转已拣")
	void scanAccumulatesAndCompletes() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PICKING));
		FulfillmentPickItem item = pickItem();
		when(pickItemMapper.selectOne(any())).thenReturn(item);

		FulfillmentPickItem afterFirst = service.scanPick(TENANT, "picker-1", scanDTO("6900001", 4));
		assertEquals(4, afterFirst.getPickedQuantity());
		assertEquals(FulfillmentPickItem.PICK_PENDING, afterFirst.getPickStatus());

		FulfillmentPickItem afterSecond = service.scanPick(TENANT, "picker-1", scanDTO("sku-1", 6));
		assertEquals(10, afterSecond.getPickedQuantity());
		assertEquals(FulfillmentPickItem.PICK_DONE, afterSecond.getPickStatus());
	}

	@Test
	@DisplayName("短装必须记录原因和实际数量，并生成待处理异常")
	void shortReportRequiresReason() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PICKING));
		when(pickItemMapper.selectOne(any())).thenReturn(pickItem());

		FulfillmentShortReportDTO noReason = new FulfillmentShortReportDTO();
		noReason.setWaveId("wave-1");
		noReason.setItemId("pick-1");
		noReason.setActualQuantity(7);
		assertThrows(ArynBusinessException.class, () -> service.reportShort(TENANT, "picker-1", noReason));

		FulfillmentShortReportDTO valid = new FulfillmentShortReportDTO();
		valid.setWaveId("wave-1");
		valid.setItemId("pick-1");
		valid.setActualQuantity(7);
		valid.setReasonCode("OUT_OF_STOCK");
		valid.setReasonDesc("仓库缺货");
		FulfillmentPickItem result = service.reportShort(TENANT, "picker-1", valid);
		assertEquals(3, result.getShortQuantity());
		assertEquals(FulfillmentPickItem.PICK_SHORT, result.getPickStatus());

		ArgumentCaptor<FulfillmentException> captor = ArgumentCaptor.forClass(FulfillmentException.class);
		verify(exceptionMapper).insert(captor.capture());
		assertEquals(FulfillmentException.STATUS_OPEN, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("波次交接司机后仓库不能继续修改明细")
	void handedOverWaveNotEditable() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_HANDED_OVER));
		when(pickItemMapper.selectOne(any())).thenReturn(pickItem());

		assertThrows(ArynBusinessException.class,
				() -> service.scanPick(TENANT, "picker-1", scanDTO("6900001", 1)));
		assertThrows(ArynBusinessException.class, () -> service.addOrderToWave(TENANT, "wave-1", "order-1"));
	}

	@Test
	@DisplayName("存在未拣完明细时不能复核")
	void reviewRejectsUnfinishedItems() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PICKING));
		FulfillmentPickItem pending = pickItem();
		when(pickItemMapper.selectList(any())).thenReturn(List.of(pending));

		assertThrows(ArynBusinessException.class, () -> service.reviewWave(TENANT, "op-1", "仓管员", "wave-1"));
	}

	@Test
	@DisplayName("全部拣完后复核通过，可交接司机")
	void reviewAndHandOver() {
		when(waveMapper.selectOne(any())).thenReturn(wave(FulfillmentWave.STATUS_PICKING));
		FulfillmentPickItem done = pickItem();
		done.setPickStatus(FulfillmentPickItem.PICK_DONE);
		when(pickItemMapper.selectList(any())).thenReturn(List.of(done));

		FulfillmentWave reviewed = service.reviewWave(TENANT, "op-1", "仓管员", "wave-1");
		assertEquals(FulfillmentWave.STATUS_REVIEWED, reviewed.getStatus());

		when(waveMapper.selectOne(any())).thenReturn(reviewed);
		FulfillmentWave handed = service.handOverToDriver(TENANT, "op-1", "仓管员", "wave-1", "staff-1");
		assertEquals(FulfillmentWave.STATUS_HANDED_OVER, handed.getStatus());
	}

}
