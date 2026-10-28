package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.promotion.api.dto.DiscountActivityDTO;
import com.aryn.cloud.promotion.api.dto.DiscountGoodsDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.api.vo.DiscountActivityVO;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.service.IDiscountGoodsService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 折扣活动编辑：已产生销量的商品不能被移除（P3-03）。
 *
 * <p>背景：updateWithGoods 是「删光商品行再重建」。若某 SKU 已被下单占用又被移除，
 * 占用明细会指向不存在的商品行，取消/退款时回补不到 —— 库存就此永久少一份。
 */
class DiscountActivityServiceImplTest {

	private IDiscountGoodsService discountGoodsService;
	private DiscountActivityMapper activityMapper;
	private RemoteGoodsSpuService remoteGoodsSpuService;
	private DiscountActivityServiceImpl service;

	@BeforeEach
	void setUp() {
		discountGoodsService = mock(IDiscountGoodsService.class);
		activityMapper = mock(DiscountActivityMapper.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);
		service = new DiscountActivityServiceImpl(discountGoodsService, mock(RemoteGoodsSkuService.class));
		ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", remoteGoodsSpuService);
		// 纯单测无 Spring 上下文：LambdaUpdateWrapper 需要先注册实体的字段缓存
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				DiscountActivity.class);
	}

	private void stubActivity(boolean soldGoodsExists) {
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setScope(2);
		when(activityMapper.selectById("act-1")).thenReturn(activity);
		when(activityMapper.updateById(any(DiscountActivity.class))).thenReturn(1);
		when(discountGoodsService.listByActivityId("act-1")).thenReturn(List.of(
				new DiscountGoods().setActivityId("act-1").setSkuId("sku-kept").setStock(10).setSoldCount(0),
				new DiscountGoods().setActivityId("act-1").setSkuId("sku-sold").setStock(10)
					.setSoldCount(soldGoodsExists ? 3 : 0)));
	}

	private DiscountActivityDTO dtoWithSkus(String... skuIds) {
		DiscountActivityDTO dto = new DiscountActivityDTO();
		dto.setId("act-1");
		dto.setScope(2);
		dto.setDiscountType(1);
		dto.setDiscountValue(java.math.BigDecimal.valueOf(0.8));
		dto.setGoodsList(java.util.Arrays.stream(skuIds)
			.map(sku -> {
				DiscountGoodsDTO goods = new DiscountGoodsDTO();
				goods.setSkuId(sku);
				goods.setSpuId("spu-1");
				goods.setStock(10);
				return goods;
			})
			.toList());
		return dto;
	}

	@Test
	void removingSoldSkuIsRejected() {
		stubActivity(true);

		assertThatThrownBy(() -> service.updateWithGoods(dtoWithSkus("sku-kept")))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("不能移除"));

		// 拒绝发生在删除之前：商品行与占用都不该被动过
		verify(discountGoodsService, never()).remove(any());
		verify(discountGoodsService, never()).saveBatch(anyList());
	}

	@Test
	void removingUnsoldSkuIsAllowed() {
		stubActivity(false);
		when(discountGoodsService.saveBatch(anyList())).thenReturn(true);

		assertThat(service.updateWithGoods(dtoWithSkus("sku-kept"))).isTrue();
		verify(discountGoodsService).remove(any());
		verify(discountGoodsService).saveBatch(anyList());
	}

	@Test
	void loweringStockBelowSoldIsRejected() {
		stubActivity(true);
		DiscountActivityDTO dto = dtoWithSkus("sku-kept", "sku-sold");
		dto.getGoodsList().stream()
			.filter(goods -> "sku-sold".equals(goods.getSkuId()))
			.findFirst()
			.orElseThrow()
			.setStock(1);

		assertThatThrownBy(() -> service.updateWithGoods(dto))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("不能小于已售"));
	}

	/** discount_goods 只存 ID：详情必须回填名称/图片，否则编辑页商品信息列只剩一串 SPU ID */
	@Test
	void getDetailBackfillsSpuNameAndImage() {
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setScope(2);
		when(activityMapper.selectById("act-1")).thenReturn(activity);
		when(discountGoodsService.listByActivityId("act-1")).thenReturn(List.of(
				new DiscountGoods().setActivityId("act-1").setSpuId("spu-1").setSkuId("sku-1"),
				new DiscountGoods().setActivityId("act-1").setSpuId("spu-2").setSkuId("sku-2")));
		when(remoteGoodsSpuService.getSpuByIds(anyList())).thenReturn(List.of(
				new GoodsSpu().setId("spu-1").setName("矿泉水").setSpuUrls(new String[] {"http://img/1.png"}),
				new GoodsSpu().setId("spu-2").setName("泡面")));

		DiscountActivityVO vo = service.getDetail("act-1");

		assertThat(vo.getGoodsList()).hasSize(2);
		assertThat(vo.getGoodsList().get(0).getSpuName()).isEqualTo("矿泉水");
		assertThat(vo.getGoodsList().get(0).getSpuUrls()).containsExactly("http://img/1.png");
		assertThat(vo.getGoodsList().get(1).getSpuName()).isEqualTo("泡面");
	}

	/** 名称查询挂了不能拖垮详情：降级为仅返回 ID，编辑页回退显示 SPU ID */
	@Test
	void getDetailDegradesWhenSpuLookupFails() {
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setScope(2);
		when(activityMapper.selectById("act-1")).thenReturn(activity);
		when(discountGoodsService.listByActivityId("act-1")).thenReturn(List.of(
				new DiscountGoods().setActivityId("act-1").setSpuId("spu-1").setSkuId("sku-1")));
		when(remoteGoodsSpuService.getSpuByIds(anyList())).thenThrow(new RuntimeException("dubbo timeout"));

		DiscountActivityVO vo = service.getDetail("act-1");

		assertThat(vo.getGoodsList()).hasSize(1);
		assertThat(vo.getGoodsList().get(0).getSpuId()).isEqualTo("spu-1");
		assertThat(vo.getGoodsList().get(0).getSpuName()).isNull();
	}

	/** 启用必须直接落到进行中，不能停在 0 等定时任务；否则小程序会有一段时间看不到活动 */
	@Test
	void enablingWithinWindowWritesRunningStatusDirectly() {
		stubStatusActivity(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
		when(activityMapper.update(any(), any())).thenReturn(1);

		assertThat(service.updateStatus("act-1", 0)).isTrue();
		assertThat(capturedStatus()).isEqualTo(1);
	}

	@Test
	void enablingBeforeStartWritesNotStartedStatus() {
		stubStatusActivity(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
		when(activityMapper.update(any(), any())).thenReturn(1);

		assertThat(service.updateStatus("act-1", 0)).isTrue();
		assertThat(capturedStatus()).isZero();
	}

	@Test
	void enablingAfterEndWritesEndedStatus() {
		stubStatusActivity(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
		when(activityMapper.update(any(), any())).thenReturn(1);

		assertThat(service.updateStatus("act-1", 0)).isTrue();
		assertThat(capturedStatus()).isEqualTo(2);
	}

	@Test
	void pausingKeepsPausedStatusRegardlessOfWindow() {
		stubStatusActivity(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
		when(activityMapper.update(any(), any())).thenReturn(1);

		assertThat(service.updateStatus("act-1", 3)).isTrue();
		assertThat(capturedStatus()).isEqualTo(3);
	}

	@Test
	void updatingMissingActivityReturnsFalse() {
		when(activityMapper.selectById("act-1")).thenReturn(null);

		assertThat(service.updateStatus("act-1", 0)).isFalse();
		verify(activityMapper, never()).update(any(), any());
	}

	/** 并发删除等场景下影响 0 行必须返回失败，否则前端会误报「启用成功」 */
	@Test
	void updatingZeroRowsReturnsFalse() {
		stubStatusActivity(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
		when(activityMapper.update(any(), any())).thenReturn(0);

		assertThat(service.updateStatus("act-1", 0)).isFalse();
	}

	private void stubStatusActivity(LocalDateTime startTime, LocalDateTime endTime) {
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setStatus(3);
		activity.setStartTime(startTime);
		activity.setEndTime(endTime);
		when(activityMapper.selectById("act-1")).thenReturn(activity);
	}

	@SuppressWarnings("unchecked")
	private int capturedStatus() {
		ArgumentCaptor<Wrapper<DiscountActivity>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(activityMapper).update(isNull(), captor.capture());
		return ((LambdaUpdateWrapper<DiscountActivity>) captor.getValue()).getParamNameValuePairs()
			.values()
			.stream()
			.filter(Integer.class::isInstance)
			.map(Integer.class::cast)
			.findFirst()
			.orElseThrow();
	}

}
