package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.vo.GroupBuyActivityVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyMemberMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拼团「已拼 N 件」展示口径测试。
 *
 * <p>展示值 = 运营配置的 {@code virtualNum}（冷启动虚增）+ 真实已付款参团件数；
 * 列表页必须按本页活动一次批量统计，不能逐活动 count（N+1）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupBuyActivityServiceImplTest {

	@Mock
	private GroupBuyActivityMapper activityMapper;

	@Mock
	private GroupBuyRecordMapper recordMapper;

	@Mock
	private GroupBuyMemberMapper memberMapper;

	private GroupBuyActivityServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GroupBuyActivityServiceImpl(recordMapper, memberMapper);
		ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
	}

	private GroupBuyActivity activity(String id, Integer virtualNum) {
		GroupBuyActivity activity = new GroupBuyActivity();
		activity.setId(id);
		activity.setActivityName("活动" + id);
		activity.setSpuId("spu-" + id);
		activity.setSkuId("sku-" + id);
		activity.setOriginalPrice(new BigDecimal("29.90"));
		activity.setGroupPrice(new BigDecimal("25.90"));
		activity.setGroupNum(2);
		activity.setLimitNum(2);
		activity.setVirtualNum(virtualNum);
		activity.setActivityStatus("1");
		return activity;
	}

	@Test
	void appPageComposesVirtualAndRealSoldCount() {
		Page<GroupBuyActivity> dbPage = new Page<>(1, 10);
		dbPage.setRecords(List.of(activity("a1", 5), activity("a2", null), activity("a3", 0)));
		when(activityMapper.selectAppPage(any(), any())).thenReturn(dbPage);
		when(recordMapper.selectMaps(any())).thenReturn(List.of(
				Map.of("activity_id", "a1", "cnt", 2L),
				Map.of("activity_id", "a2", "cnt", 1L)));
		when(memberMapper.selectMaps(any())).thenReturn(List.of(
				Map.of("activity_id", "a1", "cnt", 3L),
				Map.of("activity_id", "a2", "cnt", 4L)));

		IPage<GroupBuyActivityVO> page = service.getAppPage(new Page<>(1, 10), new GroupBuyActivity());

		GroupBuyActivityVO first = page.getRecords().get(0);
		assertThat(first.getJoinCount()).isEqualTo(2L);
		assertThat(first.getRealSoldCount()).isEqualTo(3L);
		assertThat(first.getSoldCount()).isEqualTo(8L);

		GroupBuyActivityVO second = page.getRecords().get(1);
		assertThat(second.getJoinCount()).isEqualTo(1L);
		assertThat(second.getRealSoldCount()).isEqualTo(4L);
		assertThat(second.getSoldCount()).as("virtualNum 为空按 0 处理").isEqualTo(4L);

		GroupBuyActivityVO third = page.getRecords().get(2);
		assertThat(third.getJoinCount()).isZero();
		assertThat(third.getRealSoldCount()).isZero();
		assertThat(third.getSoldCount()).isZero();

		// 本页活动只做一次批量统计，不按活动逐条 count
		verify(recordMapper, times(1)).selectMaps(any());
		verify(memberMapper, times(1)).selectMaps(any());
	}

	@Test
	void appPageSkipsStatsQueriesWhenPageEmpty() {
		Page<GroupBuyActivity> emptyPage = new Page<>(1, 10);
		emptyPage.setRecords(List.of());
		when(activityMapper.selectAppPage(any(), any())).thenReturn(emptyPage);

		IPage<GroupBuyActivityVO> page = service.getAppPage(new Page<>(1, 10), new GroupBuyActivity());

		assertThat(page.getRecords()).isEmpty();
		verify(recordMapper, never()).selectMaps(any());
		verify(memberMapper, never()).selectMaps(any());
	}

	@Test
	void appDetailReturnsComposedCounts() {
		when(activityMapper.selectById("a1")).thenReturn(activity("a1", 5));
		when(recordMapper.selectMaps(any())).thenReturn(List.of(Map.of("activity_id", "a1", "cnt", 1L)));
		when(memberMapper.selectMaps(any())).thenReturn(List.of(Map.of("activity_id", "a1", "cnt", 3L)));

		GroupBuyActivityVO detail = service.getAppDetail("a1");

		assertThat(detail).isNotNull();
		assertThat(detail.getVirtualNum()).isEqualTo(5);
		assertThat(detail.getJoinCount()).isEqualTo(1L);
		assertThat(detail.getRealSoldCount()).isEqualTo(3L);
		assertThat(detail.getSoldCount()).isEqualTo(8L);
	}

	@Test
	void appDetailReturnsNullWhenActivityMissing() {
		when(activityMapper.selectById("missing")).thenReturn(null);

		assertThat(service.getAppDetail("missing")).isNull();
		verify(recordMapper, never()).selectMaps(any());
		verify(memberMapper, never()).selectMaps(any());
	}
}
