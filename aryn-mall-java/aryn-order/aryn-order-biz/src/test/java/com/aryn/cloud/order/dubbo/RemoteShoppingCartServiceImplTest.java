package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 购物车归属迁移远程服务单测。
 *
 * <p>核心口径：参数校验不通过直接跳过（不产生跨租户写），staleCallIds 空集合
 * 归一化后下推 SQL——XML 侧按「无归属」条件执行，不允许出现 IN () 非法 SQL。
 */
class RemoteShoppingCartServiceImplTest {

	private ShoppingCartMapper mapper;
	private RemoteShoppingCartServiceImpl service;

	@BeforeEach
	void setUp() {
		mapper = mock(ShoppingCartMapper.class);
		service = new RemoteShoppingCartServiceImpl(mapper);
	}

	@Test
	void reattachDelegatesToMapperWithExplicitTenantScope() {
		when(mapper.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-1")))
			.thenReturn(3);

		int rows = service.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-1"));

		assertThat(rows).isEqualTo(3);
		verify(mapper).reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-1"));
	}

	@Test
	void nullStaleListIsNormalizedToEmptyList() {
		when(mapper.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of())).thenReturn(2);

		int rows = service.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", null);

		assertThat(rows).isEqualTo(2);
		verify(mapper).reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of());
	}

	@Test
	void blankArgumentsSkipMigration() {
		int rows = service.reattachRowsToVesselCall("tenant-1", " ", "call-2", List.of("call-1"));

		assertThat(rows).isZero();
		verifyNoInteractions(mapper);
	}

}
