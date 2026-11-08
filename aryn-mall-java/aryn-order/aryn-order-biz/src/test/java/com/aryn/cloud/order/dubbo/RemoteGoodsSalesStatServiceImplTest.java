package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.service.ISpuSalesStatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SPU 社证统计远程实现的 fail-closed 契约。
 *
 * <p>硬约束：租户缺失直接返回空（绝不退化成跨租户统计）；异常降级空列表不穿透
 * （社证是卡面增强文案，任何失败都不能打断消费方）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SPU社证统计远程实现")
class RemoteGoodsSalesStatServiceImplTest {

	@Mock
	private ISpuSalesStatService spuSalesStatService;

	@InjectMocks
	private RemoteGoodsSalesStatServiceImpl service;

	@Test
	@DisplayName("租户缺失 fail-closed 空列表且不打服务")
	void blankTenantReturnsEmpty() {
		assertThat(service.latestStats(null, List.of("spu-1"))).isEmpty();
		assertThat(service.latestStats(" ", List.of("spu-1"))).isEmpty();
		verifyNoInteractions(spuSalesStatService);
	}

	@Test
	@DisplayName("服务异常降级空列表不穿透")
	void serviceFailureDegradesToEmpty() {
		when(spuSalesStatService.latestStats("t1", List.of("spu-1"))).thenThrow(new RuntimeException("boom"));

		assertThat(service.latestStats("t1", List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("正常路径透传统计结果")
	void happyPathPassesThrough() {
		GoodsSpuSalesStatDTO stat = new GoodsSpuSalesStatDTO();
		stat.setSpuId("spu-1");
		stat.setBuyerCnt(4000);
		stat.setRepeatBuyerCnt(1600);
		stat.setRepurchaseRate(new BigDecimal("40.00"));
		when(spuSalesStatService.latestStats("t1", List.of("spu-1"))).thenReturn(List.of(stat));

		List<GoodsSpuSalesStatDTO> result = service.latestStats("t1", List.of("spu-1"));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getBuyerCnt()).isEqualTo(4000);
	}

}
