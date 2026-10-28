package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.order.service.IShoppingCartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 购物车远程服务单测。
 *
 * <p>核心口径：参数校验不通过直接跳过（不产生跨租户写）；归属迁移委托给
 * {@link IShoppingCartService}（撞唯一键的数量合并在服务层统一实现），
 * 本类只负责守门与日志；秒杀人数统计仍直连 mapper 聚合查询。
 */
class RemoteShoppingCartServiceImplTest {

	private IShoppingCartService shoppingCartService;
	private ShoppingCartMapper mapper;
	private RemoteShoppingCartServiceImpl service;

	@BeforeEach
	void setUp() {
		shoppingCartService = mock(IShoppingCartService.class);
		mapper = mock(ShoppingCartMapper.class);
		service = new RemoteShoppingCartServiceImpl(shoppingCartService, mapper);
	}

	@Test
	void reattachDelegatesToServiceWithExplicitScope() {
		when(shoppingCartService.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-1")))
			.thenReturn(3);

		int rows = service.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-1"));

		assertThat(rows).isEqualTo(3);
		verify(shoppingCartService).reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-1"));
	}

	@Test
	void nullStaleListIsNormalizedToEmptyList() {
		when(shoppingCartService.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of()))
			.thenReturn(2);

		int rows = service.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", null);

		assertThat(rows).isEqualTo(2);
		verify(shoppingCartService).reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of());
	}

	@Test
	void blankArgumentsSkipMigration() {
		int rows = service.reattachRowsToVesselCall("tenant-1", " ", "call-2", List.of("call-1"));

		assertThat(rows).isZero();
		verifyNoInteractions(shoppingCartService);
	}

	@Test
	void cartUserCountDelegatesWithExplicitTenantAndSince() {
		LocalDateTime since = LocalDateTime.of(2026, 10, 23, 11, 0);
		when(mapper.countDistinctSkuUsersSince("tenant-1", List.of("sku-1", "sku-2"), since)).thenReturn(23);

		int count = service.countDistinctSkuUsersSince("tenant-1", List.of("sku-1", "sku-2"), since);

		assertThat(count).isEqualTo(23);
		verify(mapper).countDistinctSkuUsersSince("tenant-1", List.of("sku-1", "sku-2"), since);
	}

	@Test
	void cartUserCountSkipsQueryOnBlankTenantNullOrEmptySkus() {
		LocalDateTime since = LocalDateTime.of(2026, 10, 23, 11, 0);

		assertThat(service.countDistinctSkuUsersSince(" ", List.of("sku-1"), since)).isZero();
		assertThat(service.countDistinctSkuUsersSince("tenant-1", List.of("sku-1"), null)).isZero();
		assertThat(service.countDistinctSkuUsersSince("tenant-1", List.of(), since)).isZero();
		verifyNoInteractions(mapper);
		verifyNoInteractions(shoppingCartService);
	}

}
