package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsBrandFilterVO;
import com.aryn.cloud.product.service.IGoodsBrandService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

	/**
	 * 品牌筛选项必须把查询条件**原样**透传给 service。
	 *
	 * <p>这是本接口的全部价值所在：C 端品牌条原先拉全租户品牌，与当前分类无关，
	 * 导致水果等分类下每个品牌点进去都是空列表。若这里丢了 categorySecondId
	 * （例如只透传一级类目），问题会以「二级类目下出现死选项」的形式复现，
	 * 且编译与类型检查都拦不住。
	 */
	@Test
	void filterListPassesEveryQueryConditionThrough() {
		IGoodsBrandService goodsBrandService = mock(IGoodsBrandService.class);
		when(goodsBrandService.listFilterOptions(any(GoodsSpu.class)))
				.thenReturn(List.of(new GoodsBrandFilterVO()));
		AppGoodsBrandController controller = new AppGoodsBrandController(goodsBrandService);

		GoodsSpu query = new GoodsSpu();
		query.setCategoryFirstId("9510000000000000002");
		query.setCategorySecondId("9520000000000000007");
		query.setName("苹果");

		controller.filterList(query);

		ArgumentCaptor<GoodsSpu> captor = ArgumentCaptor.forClass(GoodsSpu.class);
		verify(goodsBrandService).listFilterOptions(captor.capture());
		GoodsSpu passed = captor.getValue();
		assertThat(passed.getCategoryFirstId()).isEqualTo("9510000000000000002");
		assertThat(passed.getCategorySecondId()).isEqualTo("9520000000000000007");
		assertThat(passed.getName()).isEqualTo("苹果");
	}

}
