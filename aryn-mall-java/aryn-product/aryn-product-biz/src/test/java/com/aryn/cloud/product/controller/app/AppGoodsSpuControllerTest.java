package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppGoodsSpuControllerTest {

	@Test
	void batchLookupOnlyReturnsPublishedGoods() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		when(goodsSpuService.list(any(Wrapper.class))).thenReturn(List.of());
		// 被测方法不触达船供/快捷加购服务，传 null 避免 Mockito 内联 mock 其依赖层次失败
		AppGoodsSpuController controller = new AppGoodsSpuController(goodsSpuService, null, null);

		controller.getById(List.of("goods-1", "goods-2"));

		ArgumentCaptor<Wrapper<GoodsSpu>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(goodsSpuService).list(wrapperCaptor.capture());
		Wrapper<GoodsSpu> wrapper = wrapperCaptor.getValue();
		assertTrue(wrapper.getSqlSegment().contains("status"));
		AbstractWrapper<?, ?, ?> abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
		assertTrue(abstractWrapper.getParamNameValuePairs().containsValue("1"));
	}

}
