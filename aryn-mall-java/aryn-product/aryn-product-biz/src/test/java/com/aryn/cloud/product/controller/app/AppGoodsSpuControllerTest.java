package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsSpuService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
		// 被测方法不触达快捷加购/榜单服务，传 null 避免 Mockito 内联 mock 其依赖层次失败
		AppGoodsSpuController controller = new AppGoodsSpuController(goodsSpuService, null, null);

		controller.getById(List.of("goods-1", "goods-2"));

		ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
		verify(goodsSpuService).apiListByIds(idsCaptor.capture());
		assertThat(idsCaptor.getValue()).containsExactly("goods-1", "goods-2");
	}

	@Test
	void pagePassesBargainRateThroughToService() {
		// 契约：GET /app/goodsspu/page?bargainRate=1.15 —— 控制器只负责透传，
		// 区间校验/清空 asc/desc 在服务层（见 GoodsSpuServiceImplTest），避免控制器成为绕过校验的入口
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		Page<GoodsSpu> page = new Page<>(1, 10);
		GoodsSpu query = new GoodsSpu();
		when(goodsSpuService.apiPage(any(Page.class), any(GoodsSpu.class), any())).thenReturn(page);
		AppGoodsSpuController controller = new AppGoodsSpuController(goodsSpuService, null, null);

		controller.page(page, query, new BigDecimal("1.15"));

		verify(goodsSpuService).apiPage(page, query, new BigDecimal("1.15"));
	}

	@Test
	void pagePassesNullBargainRateWhenParameterAbsent() {
		// 兼容路径：不传 bargainRate 时必须原样传 null，服务层才会走「存量零影响」分支
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		Page<GoodsSpu> page = new Page<>(1, 10);
		GoodsSpu query = new GoodsSpu();
		when(goodsSpuService.apiPage(any(Page.class), any(GoodsSpu.class), isNull())).thenReturn(page);
		AppGoodsSpuController controller = new AppGoodsSpuController(goodsSpuService, null, null);

		controller.page(page, query, null);

		verify(goodsSpuService).apiPage(page, query, null);
	}

	@Test
	void recommendPassesContextThroughToService() {
		// 推荐流：分类解析、排除、脱敏都在服务层，控制器只透传上下文参数
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		Page<GoodsSpu> page = new Page<>(1, 10);
		when(goodsSpuService.recommendPage(page, "spu-1", List.of("spu-2"), List.of("spu-9"))).thenReturn(page);
		AppGoodsSpuController controller = new AppGoodsSpuController(goodsSpuService, null, null);

		controller.recommend(page, "spu-1", List.of("spu-2"), List.of("spu-9"));

		verify(goodsSpuService).recommendPage(page, "spu-1", List.of("spu-2"), List.of("spu-9"));
	}

}
