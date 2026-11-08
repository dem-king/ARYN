package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesStatService;
import com.aryn.cloud.product.api.vo.AppraiseCountVO;
import com.aryn.cloud.product.api.vo.GoodsSocialBubbleVO;
import com.aryn.cloud.product.mapper.GoodsAppraiseMapper;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商详口碑气泡装配服务的降级契约。
 *
 * <p>社证（与商品卡同源同门槛）/好评率（评价区同口径）/头像三路独立降级，
 * 全部不可得返回 null——气泡属增强展示，绝不拖垮商详主流程。
 * 社证门槛/取整口径在 {@code SocialProofFormatterTest} 钉死，这里只验装配与降级。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商详口碑气泡装配服务")
class GoodsSocialBubbleServiceImplTest {

	private static final String SPU_ID = "spu-1";

	@Mock
	private RemoteGoodsSalesStatService remoteGoodsSalesStatService;

	@Mock
	private IGoodsAppraiseService goodsAppraiseService;

	@Mock
	private GoodsAppraiseMapper goodsAppraiseMapper;

	private GoodsSocialBubbleServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GoodsSocialBubbleServiceImpl(goodsAppraiseService, goodsAppraiseMapper);
		ReflectionTestUtils.setField(service, "remoteGoodsSalesStatService", remoteGoodsSalesStatService);
		ReflectionTestUtils.setField(service, "minBuyerCnt", 1000);
		ReflectionTestUtils.setField(service, "minRepurchaseRate", 30);
		ReflectionTestUtils.setField(service, "minAppraiseCnt", 10);
	}

	private void stat(Integer buyerCnt, Integer repeatBuyerCnt, String repurchaseRate) {
		GoodsSpuSalesStatDTO dto = new GoodsSpuSalesStatDTO();
		dto.setSpuId(SPU_ID);
		dto.setBuyerCnt(buyerCnt);
		dto.setRepeatBuyerCnt(repeatBuyerCnt);
		dto.setRepurchaseRate(repurchaseRate == null ? null : new java.math.BigDecimal(repurchaseRate));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenReturn(List.of(dto));
	}

	private void appraise(int allCount, int goodCount) {
		AppraiseCountVO vo = new AppraiseCountVO();
		vo.setAllCount(allCount);
		vo.setGoodCount(goodCount);
		when(goodsAppraiseService.getCount(any())).thenReturn(vo);
	}

	@Test
	@DisplayName("三路齐备：社证+好评率+头像全量装配")
	void buildComposesAllThreeSources() {
		stat(12000, 3000, "25");
		appraise(500, 480);
		when(goodsAppraiseMapper.selectRecentBuyerAvatars(eq(SPU_ID), anyInt()))
				.thenReturn(List.of("a.png", "b.png", "c.png"));

		GoodsSocialBubbleVO bubble = service.build(SPU_ID);

		assertThat(bubble).isNotNull();
		assertThat(bubble.getSocialText()).isEqualTo("回头客3000+");
		assertThat(bubble.getSocialKind()).isEqualTo("repeat");
		assertThat(bubble.getPraiseText()).isEqualTo("好评率96%");
		assertThat(bubble.getPraiseRate()).isEqualByComparingTo("96");
		assertThat(bubble.getAvatars()).containsExactly("a.png", "b.png", "c.png");
	}

	@Test
	@DisplayName("社证与评价均未达门槛：返回 null，头像查询不执行")
	void buildReturnsNullWhenNothingPassesThreshold() {
		stat(500, 100, "20");
		appraise(5, 5);

		assertThat(service.build(SPU_ID)).isNull();

		verify(goodsAppraiseMapper, never()).selectRecentBuyerAvatars(any(), anyInt());
	}

	@Test
	@DisplayName("好评率半进一位小数去 .0（与评价区前端口径一致）")
	void praiseRateRoundsToOneDecimal() {
		appraise(703, 699);

		GoodsSocialBubbleVO bubble = service.build(SPU_ID);

		assertThat(bubble.getPraiseText()).isEqualTo("好评率99.4%");
		assertThat(bubble.getPraiseRate()).isEqualByComparingTo("99.4");
		assertThat(bubble.getSocialText()).isNull();
	}

	@Test
	@DisplayName("社证远端异常：只丢社证，好评率仍下发")
	void socialFailureDegradesToPraiseOnly() {
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenThrow(new RuntimeException("boom"));
		appraise(100, 98);

		GoodsSocialBubbleVO bubble = service.build(SPU_ID);

		assertThat(bubble).isNotNull();
		assertThat(bubble.getSocialText()).isNull();
		assertThat(bubble.getSocialKind()).isNull();
		assertThat(bubble.getPraiseText()).isEqualTo("好评率98%");
	}

	@Test
	@DisplayName("评价查询异常：只丢好评率，社证仍下发")
	void praiseFailureDegradesToSocialOnly() {
		stat(12000, 3000, "25");
		when(goodsAppraiseService.getCount(any())).thenThrow(new RuntimeException("boom"));

		GoodsSocialBubbleVO bubble = service.build(SPU_ID);

		assertThat(bubble).isNotNull();
		assertThat(bubble.getSocialText()).isEqualTo("回头客3000+");
		assertThat(bubble.getPraiseText()).isNull();
	}

	@Test
	@DisplayName("头像为空：不下发 avatars 字段（纯文字胶囊）")
	void emptyAvatarsNotSet() {
		appraise(100, 98);
		when(goodsAppraiseMapper.selectRecentBuyerAvatars(eq(SPU_ID), anyInt())).thenReturn(List.of());

		GoodsSocialBubbleVO bubble = service.build(SPU_ID);

		assertThat(bubble.getAvatars()).isNull();
		assertThat(bubble.getPraiseText()).isEqualTo("好评率98%");
	}

	@Test
	@DisplayName("空 spuId：直接返回 null")
	void blankSpuIdReturnsNull() {
		assertThat(service.build(" ")).isNull();
		verifyNoInteractionsWithRemoteAndMappers();
	}

	private void verifyNoInteractionsWithRemoteAndMappers() {
		org.mockito.Mockito.verifyNoInteractions(remoteGoodsSalesStatService, goodsAppraiseService,
				goodsAppraiseMapper);
	}

}
