package com.aryn.cloud.product.controller.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.service.IGoodsBrandService;
import com.aryn.cloud.product.service.IGoodsSpuService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsBrandControllerTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void pageUsesMapperCompatibleWrapper() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsBrand.class);
		IGoodsBrandService goodsBrandService = mock(IGoodsBrandService.class);
		when(goodsBrandService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());
		GoodsBrandController controller = new GoodsBrandController(goodsBrandService, mock(IGoodsSpuService.class));

		controller.page(new Page<>(), new GoodsBrand().setName("测试品牌").setStatus("0"));

		ArgumentCaptor<Wrapper<GoodsBrand>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(goodsBrandService).page(any(Page.class), wrapperCaptor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		assertThat(wrapper.getClass().getSimpleName()).isEqualTo("LambdaQueryWrapper");
		assertThat(wrapper.getSqlSegment()).contains("name", "status", "sort", "create_time");
		assertThat(wrapper.getParamNameValuePairs()).containsValues("%测试品牌%", "0");
	}

}
