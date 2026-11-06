package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.vo.OrderContextVO;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P2-A 会员/订单上下文：订单域远程接口单测（mock BaseMapper，不依赖真实 DB）。
 *
 * <p>覆盖：租户+会员+未删除过滤条件、create_time 倒序、limit 归一化（1..10，默认 3）、
 * 字段映射（状态中文名/实收金额优先/配送方式）、空数据与查询异常降级为空列表、租户上下文还原。</p>
 */
class RemoteOrderContextServiceImplTest {

	private OrderInfoMapper orderInfoMapper;
	private RemoteOrderContextServiceImpl service;

	@BeforeAll
	static void initTableInfoCache() {
		// 纯单测没有 MyBatis 容器：先初始化实体 TableInfo/lambda 缓存，才能断言 LambdaQueryWrapper 生成的 SQL。
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
	}

	@BeforeEach
	void setUp() {
		orderInfoMapper = mock(OrderInfoMapper.class);
		service = new RemoteOrderContextServiceImpl(orderInfoMapper);
		// 模拟杜波过滤器/调用方线程已有租户：实现必须用显式入参替换并在结束后还原。
		ArynTenantContextHolder.setTenantId("caller-tenant");
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void mapsOrdersAndPrefersActualPayAmount() {
		LocalDateTime base = LocalDateTime.of(2026, 11, 6, 10, 30, 0);
		OrderInfo completed = order("order-1", "NO-1", "4", "1");
		completed.setActualPayPrice(new BigDecimal("88.80"));
		completed.setPaymentPrice(new BigDecimal("100.00"));
		completed.setDeliveryWay("1");
		completed.setCreateTime(base);
		OrderInfo waiting = order("order-2", "NO-2", "1", "0");
		waiting.setPaymentPrice(new BigDecimal("45.00"));
		waiting.setCreateTime(base.minusHours(1));
		when(orderInfoMapper.selectList(any())).thenReturn(List.of(completed, waiting));

		List<OrderContextVO> orders = service.queryRecentOrders("tenant-1", "member-1", 3);

		assertEquals(2, orders.size());
		OrderContextVO first = orders.get(0);
		assertEquals("order-1", first.getOrderId());
		assertEquals("NO-1", first.getOrderNo());
		assertEquals("4", first.getStatus());
		// 中文状态名来自既有 OrderStatusEnum。
		assertEquals("已完成", first.getStatusName());
		assertEquals("1", first.getPayStatus());
		// 实收金额优先于应付金额。
		assertEquals(new BigDecimal("88.80"), first.getPayAmount());
		assertEquals("1", first.getDeliveryWay());
		assertEquals(base, first.getCreateTime());
		// 商品摘要本批次固定降级为 null（不扩展订单明细查询）。
		assertNull(first.getProductSummary());
		// 无实收金额时回退 paymentPrice。
		assertEquals(new BigDecimal("45.00"), orders.get(1).getPayAmount());
		assertEquals("待付款", orders.get(1).getStatusName());
		// 查询结束后租户上下文还原为调用方快照。
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	void queryAppliesTenantMemberFiltersDescendingOrderAndLimit() {
		when(orderInfoMapper.selectList(any())).thenReturn(List.of());

		service.queryRecentOrders("tenant-1", "member-1", 50);

		Wrapper<OrderInfo> wrapper = captureWrapper();
		String sql = wrapper.getSqlSegment();
		assertTrue(sql.contains("tenant_id"));
		assertTrue(sql.contains("user_id"));
		assertTrue(sql.contains("del_flag"));
		assertTrue(sql.contains("ORDER BY create_time DESC"));
		// limit 50 被截断为上限 10。
		assertTrue(sql.contains("LIMIT 10"));

		Map<String, Object> params = captureParams();
		assertTrue(params.containsValue("tenant-1"));
		assertTrue(params.containsValue("member-1"));
		assertTrue(params.containsValue("0"));
	}

	@Test
	void limitIsNormalizedToOneToTen() {
		assertEquals(3, RemoteOrderContextServiceImpl.normalizeLimit(0));
		assertEquals(3, RemoteOrderContextServiceImpl.normalizeLimit(-5));
		assertEquals(3, RemoteOrderContextServiceImpl.normalizeLimit(3));
		assertEquals(5, RemoteOrderContextServiceImpl.normalizeLimit(5));
		assertEquals(10, RemoteOrderContextServiceImpl.normalizeLimit(11));
		assertEquals(10, RemoteOrderContextServiceImpl.normalizeLimit(100));
	}

	@Test
	void unknownStatusHasNoChineseName() {
		OrderInfo unknown = order("order-3", "NO-3", "99", "1");
		when(orderInfoMapper.selectList(any())).thenReturn(List.of(unknown));

		List<OrderContextVO> orders = service.queryRecentOrders("tenant-1", "member-1", 3);

		assertEquals("99", orders.get(0).getStatus());
		assertNull(orders.get(0).getStatusName());
	}

	@Test
	void emptyAndNullRowsReturnEmptyList() {
		when(orderInfoMapper.selectList(any())).thenReturn(List.of());
		assertTrue(service.queryRecentOrders("tenant-1", "member-1", 3).isEmpty());

		when(orderInfoMapper.selectList(any())).thenReturn(null);
		assertTrue(service.queryRecentOrders("tenant-1", "member-1", 3).isEmpty());
	}

	@Test
	void mapperFailureDegradesToEmptyListAndRestoresTenant() {
		when(orderInfoMapper.selectList(any())).thenThrow(new IllegalStateException("db down"));

		List<OrderContextVO> orders = assertDoesNotThrow(
				() -> service.queryRecentOrders("tenant-1", "member-1", 3));

		assertTrue(orders.isEmpty());
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	void blankParamsSkipQuery() {
		assertTrue(service.queryRecentOrders(null, "member-1", 3).isEmpty());
		assertTrue(service.queryRecentOrders("tenant-1", "  ", 3).isEmpty());

		verifyNoInteractions(orderInfoMapper);
	}

	private OrderInfo order(String id, String orderNo, String status, String payStatus) {
		OrderInfo order = new OrderInfo();
		order.setId(id);
		order.setOrderNo(orderNo);
		order.setStatus(status);
		order.setPayStatus(payStatus);
		order.setUserId("member-1");
		order.setTenantId("tenant-1");
		order.setDelFlag("0");
		return order;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Wrapper<OrderInfo> captureWrapper() {
		ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(orderInfoMapper).selectList(captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<String, Object> captureParams() {
		ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(orderInfoMapper).selectList(captor.capture());
		return ((AbstractWrapper) captor.getValue()).getParamNameValuePairs();
	}

}
