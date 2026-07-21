package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.service.IGoodsBrandService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppGoodsBrandControllerTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void onlyReturnsEnabledBrands() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsBrand.class);
		IGoodsBrandService goodsBrandService = mock(IGoodsBrandService.class);
		when(goodsBrandService.list(any(Wrapper.class))).thenReturn(List.of());
		AppGoodsBrandController controller = new AppGoodsBrandController(goodsBrandService);

		controller.list();

		ArgumentCaptor<Wrapper<GoodsBrand>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(goodsBrandService).list(wrapperCaptor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		assertTrue(wrapper.getSqlSegment().contains("status"));
		assertTrue(wrapper.getParamNameValuePairs().containsValue("0"));
	}

}
