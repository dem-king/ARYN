package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.order.api.dto.GoodsSalesRankDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesRankService;
import com.aryn.cloud.product.api.vo.AppGoodsRankVO;
import com.aryn.cloud.product.mapper.GoodsRankMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * C 端商品榜单服务单测。
 *
 * <p>核心口径：租户缺失两条链路都 fail-closed；热搜榜按 30 天窗口查浏览足迹；
 * 热销榜以订单域名次为准，回商品域过滤当前在售商品（缺失/下架跳过、名次顺延），
 * 远程异常降级为空列表而不抛出。
 */
class GoodsRankServiceImplTest {

	private GoodsRankMapper goodsRankMapper;

	private RemoteGoodsSalesRankService remoteGoodsSalesRankService;

	private GoodsRankServiceImpl service;

	@BeforeEach
	void setUp() {
		goodsRankMapper = mock(GoodsRankMapper.class);
		remoteGoodsSalesRankService = mock(RemoteGoodsSalesRankService.class);
		service = new GoodsRankServiceImpl(goodsRankMapper);
		// @DubboReference 字段不走构造器，测试里反射注入
		ReflectionTestUtils.setField(service, "remoteGoodsSalesRankService", remoteGoodsSalesRankService);
	}

	@Test
	void hotBlankTenantReturnsEmptyWithoutQuery() {
		assertThat(service.hotTop10(null)).isEmpty();
		assertThat(service.hotTop10("  ")).isEmpty();

		verifyNoInteractions(goodsRankMapper);
	}

	@Test
	void hotTop10QueriesFootprintOfRecent30Days() {
		AppGoodsRankVO first = rank("spu-1", "老李卤豆干小包装500g");
		when(goodsRankMapper.selectHotTop10("tenant-1", 30, 10)).thenReturn(List.of(first));

		assertThat(service.hotTop10("tenant-1")).containsExactly(first);

		verify(goodsRankMapper).selectHotTop10("tenant-1", 30, 10);
	}

	@Test
	void salesBlankTenantReturnsEmptyWithoutRemoteCall() {
		assertThat(service.salesTop10(null)).isEmpty();
		assertThat(service.salesTop10(" ")).isEmpty();

		verifyNoInteractions(remoteGoodsSalesRankService);
		verifyNoInteractions(goodsRankMapper);
	}

	@Test
	void salesTop10KeepsOrderRankAndOverridesWithCurrentGoods() {
		// 订单域名次：spu-2 > spu-1 > spu-3（spu-3 已下架，商品域查不到）
		when(remoteGoodsSalesRankService.topSalesRank("tenant-1", 90, 10))
			.thenReturn(List.of(rankDto("spu-2", "快照名2"), rankDto("spu-1", "快照名1"), rankDto("spu-3", "快照名3")));
		when(goodsRankMapper.selectOnSaleByIds("tenant-1", List.of("spu-2", "spu-1", "spu-3")))
			.thenReturn(List.of(rank("spu-1", "高露洁牙膏"), rank("spu-2", "心相印盒抽纸")));

		List<AppGoodsRankVO> ranks = service.salesTop10("tenant-1");

		// 名次以订单域为准，且用当前商品信息覆盖下单快照；下架商品被跳过、名次顺延
		assertThat(ranks).extracting(AppGoodsRankVO::getId).containsExactly("spu-2", "spu-1");
		assertThat(ranks.get(0).getName()).isEqualTo("心相印盒抽纸");
		assertThat(ranks.get(1).getName()).isEqualTo("高露洁牙膏");
	}

	@Test
	void salesTop10DeduplicatesSpuIdsAndSkipsBlankOnes() {
		GoodsSalesRankDTO blankSpu = rankDto(" ", "脏数据");
		when(remoteGoodsSalesRankService.topSalesRank("tenant-1", 90, 10))
			.thenReturn(List.of(blankSpu, rankDto("spu-1", "快照名1"), rankDto("spu-1", "重复快照")));
		when(goodsRankMapper.selectOnSaleByIds("tenant-1", List.of("spu-1"))).thenReturn(List.of(rank("spu-1", "高露洁牙膏")));

		List<AppGoodsRankVO> ranks = service.salesTop10("tenant-1");

		assertThat(ranks).extracting(AppGoodsRankVO::getId).containsExactly("spu-1");
		verify(goodsRankMapper).selectOnSaleByIds("tenant-1", List.of("spu-1"));
	}

	@Test
	void salesRemoteFailureDegradesToEmptyList() {
		when(remoteGoodsSalesRankService.topSalesRank("tenant-1", 90, 10))
			.thenThrow(new RuntimeException("order-service-unavailable"));

		// 跨域异常不向控制器抛出：搜索页展示空榜即可
		assertThat(service.salesTop10("tenant-1")).isEmpty();
		verifyNoInteractions(goodsRankMapper);
	}

	@Test
	void salesEmptyRemoteResultReturnsEmptyWithoutQueryingGoods() {
		when(remoteGoodsSalesRankService.topSalesRank("tenant-1", 90, 10)).thenReturn(List.of());

		assertThat(service.salesTop10("tenant-1")).isEmpty();

		verifyNoInteractions(goodsRankMapper);
	}

	private AppGoodsRankVO rank(String spuId, String name) {
		AppGoodsRankVO vo = new AppGoodsRankVO();
		vo.setId(spuId);
		vo.setName(name);
		vo.setSubTitle(name + "副标题");
		vo.setPicUrl("https://cdn/" + spuId + ".jpg");
		return vo;
	}

	private GoodsSalesRankDTO rankDto(String spuId, String spuName) {
		GoodsSalesRankDTO dto = new GoodsSalesRankDTO();
		dto.setSpuId(spuId);
		dto.setSpuName(spuName);
		dto.setSalesCount(12);
		return dto;
	}

}
