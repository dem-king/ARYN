package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.dto.GoodsSalesRankDTO;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 热销榜远程服务单测。
 *
 * <p>核心口径：租户缺失 fail-closed 不查库；统计窗口与条数按契约钳制到安全范围，
 * 聚合结果原样透传（商品域负责过滤在售商品并保持名次）。
 */
class RemoteGoodsSalesRankServiceImplTest {

	private OrderItemMapper orderItemMapper;

	private RemoteGoodsSalesRankServiceImpl service;

	@BeforeEach
	void setUp() {
		orderItemMapper = mock(OrderItemMapper.class);
		service = new RemoteGoodsSalesRankServiceImpl(orderItemMapper);
	}

	@Test
	void blankTenantReturnsEmptyWithoutQuery() {
		assertThat(service.topSalesRank(null, 90, 10)).isEmpty();
		assertThat(service.topSalesRank("  ", 90, 10)).isEmpty();

		verifyNoInteractions(orderItemMapper);
	}

	@Test
	void clampsWindowAndLimitIntoContractRange() {
		// 下界：0/负数一律钳到 1（两次越界调用都落到同一组安全参数）
		service.topSalesRank("tenant-1", 0, 0);
		service.topSalesRank("tenant-1", -5, -1);
		verify(orderItemMapper, times(2)).selectGoodsSalesRank("tenant-1", 1, 1);

		// 上界：days 最多 3650 天、limit 最多 50 条
		service.topSalesRank("tenant-1", 9999, 999);
		verify(orderItemMapper).selectGoodsSalesRank("tenant-1", 3650, 50);
	}

	@Test
	void keepsNormalArgumentsUnchanged() {
		service.topSalesRank("tenant-1", 90, 10);

		verify(orderItemMapper).selectGoodsSalesRank("tenant-1", 90, 10);
	}

	@Test
	void returnsAggregatedRankAsIs() {
		GoodsSalesRankDTO first = rank("spu-1", "高露洁牙膏", 12);
		GoodsSalesRankDTO second = rank("spu-2", "心相印盒抽纸", 12);
		when(orderItemMapper.selectGoodsSalesRank("tenant-1", 90, 10)).thenReturn(List.of(first, second));

		List<GoodsSalesRankDTO> ranks = service.topSalesRank("tenant-1", 90, 10);

		assertThat(ranks).containsExactly(first, second);
	}

	@Test
	void nullMapperResultIsNormalizedToEmptyList() {
		when(orderItemMapper.selectGoodsSalesRank("tenant-1", 90, 10)).thenReturn(null);

		assertThat(service.topSalesRank("tenant-1", 90, 10)).isEmpty();
	}

	private GoodsSalesRankDTO rank(String spuId, String spuName, int salesCount) {
		GoodsSalesRankDTO dto = new GoodsSalesRankDTO();
		dto.setSpuId(spuId);
		dto.setSpuName(spuName);
		dto.setPicUrl("https://cdn/" + spuId + ".jpg");
		dto.setSalesCount(salesCount);
		return dto;
	}

}
