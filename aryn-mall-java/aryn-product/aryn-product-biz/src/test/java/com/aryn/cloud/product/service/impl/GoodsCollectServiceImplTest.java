
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsCollect;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCollectVO;
import com.aryn.cloud.product.mapper.GoodsCollectMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 收藏列表出参脱敏：VO 内嵌完整 GoodsSpu 实体（嵌套 selectById 全列查询），
 * 成本价不能随 C 端收藏列表下发。
 */
class GoodsCollectServiceImplTest {

	private GoodsCollectMapper collectMapper;

	private GoodsSpuMapper spuMapper;

	private GoodsCollectServiceImpl service;

	@BeforeEach
	void setUp() {
		collectMapper = mock(GoodsCollectMapper.class);
		spuMapper = mock(GoodsSpuMapper.class);
		service = new TestGoodsCollectService(spuMapper, collectMapper);
	}

	@Test
	void collectPageMasksCostPriceOnNestedSpu() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setSalesPrice(BigDecimal.valueOf(20));
		spu.setOriginalPrice(BigDecimal.valueOf(80));
		spu.setCostPrice(BigDecimal.valueOf(40));
		GoodsCollectVO vo = new GoodsCollectVO();
		vo.setId("collect-1");
		vo.setGoodsSpu(spu);
		Page<GoodsCollectVO> page = new Page<>(1, 10);
		page.setRecords(List.of(vo));
		when(collectMapper.selectCollectPage(any(Page.class), any(GoodsCollect.class))).thenReturn(page);

		IPage<GoodsCollectVO> result = service.getPage(new Page<>(1, 10), new GoodsCollect());

		assertThat(result.getRecords()).hasSize(1);
		assertThat(result.getRecords().get(0).getGoodsSpu().getCostPrice()).isNull();
		// 售价/原价是「加入时价格」上下文所需，保留
		assertThat(result.getRecords().get(0).getGoodsSpu().getSalesPrice()).isEqualByComparingTo("20");
		assertThat(result.getRecords().get(0).getGoodsSpu().getOriginalPrice()).isEqualByComparingTo("80");
	}

	private static final class TestGoodsCollectService extends GoodsCollectServiceImpl {

		private TestGoodsCollectService(GoodsSpuMapper goodsSpuMapper, GoodsCollectMapper goodsCollectMapper) {
			super(goodsSpuMapper);
			this.baseMapper = goodsCollectMapper;
		}

	}

}
