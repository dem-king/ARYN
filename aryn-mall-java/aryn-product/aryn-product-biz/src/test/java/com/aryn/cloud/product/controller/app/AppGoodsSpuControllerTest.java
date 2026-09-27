package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppGoodsSpuControllerTest {

	@Test
	void batchLookupDelegatesToMaskedServiceQuery() {
		// 批量查询的过滤条件（仅上架）与出参脱敏已收拢进 GoodsSpuServiceImpl.apiListByIds，
		// 这里只锁「控制器必须走服务方法」——防止有人改回在控制器里直查实体、绕过脱敏
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		when(goodsSpuService.apiListByIds(List.of("goods-1", "goods-2"))).thenReturn(List.of());
		// 被测方法不触达船供/快捷加购服务，传 null 避免 Mockito 内联 mock 其依赖层次失败
		AppGoodsSpuController controller = new AppGoodsSpuController(goodsSpuService, null, null);

		controller.getById(List.of("goods-1", "goods-2"));

		ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
		verify(goodsSpuService).apiListByIds(idsCaptor.capture());
		assertThat(idsCaptor.getValue()).containsExactly("goods-1", "goods-2");
	}

}
