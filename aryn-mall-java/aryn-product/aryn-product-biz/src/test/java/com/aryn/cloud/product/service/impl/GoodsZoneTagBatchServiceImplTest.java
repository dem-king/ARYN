package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatch;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatchItem;
import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchResultVO;
import com.aryn.cloud.product.api.vo.ZoneTagItemStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagPreviewVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IZoneTagBatchRecordService;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.aryn.cloud.product.service.support.GoodsZoneTagWriter;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量专区操作核心契约：预检与执行共用变更判定、无变化不写库、出清无效项拦截、批量上限。
 */
class GoodsZoneTagBatchServiceImplTest {

	private static final String TENANT = "tenant-1";

	private GoodsSpuMapper goodsSpuMapper;

	private GoodsSkuMapper goodsSkuMapper;

	private GoodsPriceHistoryService goodsPriceHistoryService;

	private IZoneTagBatchRecordService batchRecordService;

	private GoodsZoneTagWriter goodsZoneTagWriter;

	private GoodsZoneTagBatchServiceImpl service;

	@BeforeEach
	void setUp() {
		goodsSpuMapper = mock(GoodsSpuMapper.class);
		goodsSkuMapper = mock(GoodsSkuMapper.class);
		goodsPriceHistoryService = mock(GoodsPriceHistoryService.class);
		batchRecordService = mock(IZoneTagBatchRecordService.class);
		goodsZoneTagWriter = mock(GoodsZoneTagWriter.class);
		GoodsZoneTagStateResolver resolver = new GoodsZoneTagStateResolver();
		service = new GoodsZoneTagBatchServiceImpl(goodsSpuMapper, goodsSkuMapper, resolver, goodsZoneTagWriter,
				goodsPriceHistoryService, batchRecordService);
		RemoteMallZoneSettingService settingService = mock(RemoteMallZoneSettingService.class);
		MallZoneSettingSnapshotVO setting = new MallZoneSettingSnapshotVO();
		setting.setEnabled(true);
		when(settingService.getSetting(anyString(), anyString())).thenReturn(setting);
		ReflectionTestUtils.setField(service, "remoteMallZoneSettingService", settingService);
		when(goodsSkuMapper.selectList(any())).thenReturn(List.of());
		when(goodsPriceHistoryService.recentSalesIncreaseSpuIds(anyCollection(), anyInt())).thenReturn(Set.of());
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	@DisplayName("预检：已打标且规则相同 → NO_CHANGE，不产生变更")
	void previewMarksSameRuleAsNoChange() {
		GoodsSpu spu = memberSpu("spu-1", "0.9000");
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));

		ZoneTagPreviewVO preview = service.preview(memberDto("spu-1", "0.9000"));

		assertThat(preview.getSummary().getNoChange()).isEqualTo(1);
		assertThat(preview.getSummary().getAdd()).isZero();
		assertThat(preview.getSummary().getUpdate()).isZero();
		ZoneTagItemStateVO item = preview.getItems().get(0);
		assertThat(item.getChanges()).extracting(change -> change.getZone() + ":" + change.getType())
			.contains("member:NO_CHANGE", "clearance:NO_CHANGE");
	}

	@Test
	@DisplayName("预检：未选择任何动作也返回当前状态（弹窗打开即可展示，不报「请选择操作」）")
	void previewAllowsNoAction() {
		GoodsSpu spu = memberSpu("spu-1", "0.9000");
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of("spu-1"));

		ZoneTagPreviewVO preview = service.preview(dto);

		assertThat(preview.getSummary().getTotal()).isEqualTo(1);
		assertThat(preview.getSummary().getNoChange()).isEqualTo(1);
		assertThat(preview.getSummary().getInvalid()).isZero();
		assertThat(preview.getItems().get(0).getChanges()).extracting(change -> change.getType())
			.containsOnly("NO_CHANGE");
	}

	@Test
	@DisplayName("预检：显式 memberAction=REMOVE 时已打标商品返回 REMOVE（兼容前端契约动作字段）")
	void previewMapsMemberActionRemove() {
		GoodsSpu spu = memberSpu("spu-1", "0.9000");
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of("spu-1"));
		dto.setMemberAction("REMOVE");

		ZoneTagPreviewVO preview = service.preview(dto);

		assertThat(preview.getSummary().getRemove()).isEqualTo(1);
		assertThat(preview.getSummary().getNoChange()).isZero();
		assertThat(preview.getItems().get(0).getChanges())
			.anySatisfy(change -> {
				if ("member".equals(change.getZone())) {
					assertThat(change.getType()).isEqualTo("REMOVE");
				}
			});
	}

	@Test
	@DisplayName("预检：显式 memberAction=ADD 时未打标商品返回 ADD")
	void previewMapsMemberActionAdd() {
		GoodsSpu spu = memberSpu("spu-1", null);
		spu.setIsMemberItem(0);
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of("spu-1"));
		dto.setMemberAction("ADD");
		dto.setMemberDiscountRate(new BigDecimal("0.9000"));

		ZoneTagPreviewVO preview = service.preview(dto);

		assertThat(preview.getSummary().getAdd()).isEqualTo(1);
	}

	@Test
	@DisplayName("执行：无变化不触发写入与批次明细的变更，仅落批次记录")
	void executeSkipsWritesWhenNoChange() {
		GoodsSpu spu = memberSpu("spu-1", "0.9000");
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));

		ZoneTagBatchResultVO result = service.execute(memberDto("spu-1", "0.9000"));

		assertThat(result.getSummary().getChanged()).isZero();
		assertThat(result.getSummary().getNoChange()).isEqualTo(1);
		assertThat(result.getItems().get(0).getChangeType()).isEqualTo("NO_CHANGE");
		verify(goodsZoneTagWriter, never()).applyBatch(any(), anyList(), any(), anyBoolean(), anyBoolean(),
				anyString());
		verify(batchRecordService).saveBatch(any(GoodsZoneTagBatch.class), anyList());
		assertThat(result.getBatchId()).isNotBlank();
	}

	@Test
	@DisplayName("执行：未打标商品 → ADD，写批次前后快照并只触碰会员专区")
	void executeAddsMemberAndRecordsBeforeAfterSnapshot() {
		GoodsSpu spu = memberSpu("spu-1", null);
		spu.setIsMemberItem(0);
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));
		when(goodsZoneTagWriter.applyBatch(any(), anyList(), any(), anyBoolean(), anyBoolean(), anyString()))
			.thenReturn(afterSnapshot(1, "0.9000"));

		ZoneTagBatchResultVO result = service.execute(memberDto("spu-1", "0.9000"));

		assertThat(result.getSummary().getAdded()).isEqualTo(1);
		assertThat(result.getSummary().getChanged()).isEqualTo(1);
		assertThat(result.getItems().get(0).getChangeType()).isEqualTo("ADD");
		verify(goodsZoneTagWriter).applyBatch(any(), anyList(), any(), eq(true), eq(false), anyString());
		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<List<GoodsZoneTagBatchItem>> itemsCaptor = org.mockito.ArgumentCaptor
			.forClass(List.class);
		verify(batchRecordService).saveBatch(any(GoodsZoneTagBatch.class), itemsCaptor.capture());
		assertThat(itemsCaptor.getValue()).hasSize(1);
		assertThat(itemsCaptor.getValue().get(0).getBeforeSnapshot()).contains("\"memberTag\":0");
		assertThat(itemsCaptor.getValue().get(0).getAfterSnapshot()).contains("\"memberTag\":1");
	}

	@Test
	@DisplayName("预检：出清打标无规则且专区无默认折扣 → INVALID")
	void previewMarksClearanceInvalidWithoutRule() {
		GoodsSpu spu = memberSpu("spu-1", null);
		spu.setIsMemberItem(0);
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of("spu-1"));
		dto.setClearanceTag(true);

		ZoneTagPreviewVO preview = service.preview(dto);

		assertThat(preview.getSummary().getInvalid()).isEqualTo(1);
		assertThat(preview.getItems().get(0).getChanges())
			.anySatisfy(change -> {
				assertThat(change.getZone()).isEqualTo("clearance");
				assertThat(change.getType()).isEqualTo("INVALID");
			});
		verify(goodsZoneTagWriter, never()).applyBatch(any(), anyList(), any(), anyBoolean(), anyBoolean(),
				anyString());
	}

	@Test
	@DisplayName("执行：出清无效项整条失败，返回 INVALID_RULE 且不写商品")
	void executeFailsItemWhenClearanceInvalid() {
		GoodsSpu spu = memberSpu("spu-1", null);
		spu.setIsMemberItem(0);
		when(goodsSpuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(spu));
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of("spu-1"));
		dto.setClearanceTag(true);

		ZoneTagBatchResultVO result = service.execute(dto);

		assertThat(result.getSummary().getFailed()).isEqualTo(1);
		assertThat(result.getSummary().getChanged()).isZero();
		assertThat(result.getItems().get(0).getSuccess()).isFalse();
		assertThat(result.getItems().get(0).getErrorCode()).isEqualTo("INVALID_RULE");
		verify(goodsZoneTagWriter, never()).applyBatch(any(), anyList(), any(), anyBoolean(), anyBoolean(),
				anyString());
	}

	@Test
	@DisplayName("批量上限：超过 200 个商品直接拒绝并提示分批")
	void rejectsMoreThanMaxBatchSize() {
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 201; i++) {
			ids.add("spu-" + i);
		}
		dto.setSpuIds(ids);
		dto.setMemberTag(true);

		assertThatThrownBy(() -> service.preview(dto)).isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "单次最多处理 200 个商品，请分批提交");
	}

	private GoodsZoneTagBatchDTO memberDto(String spuId, String rate) {
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of(spuId));
		dto.setMemberTag(true);
		dto.setMemberDiscountRate(rate == null ? null : new BigDecimal(rate));
		return dto;
	}

	private GoodsSpu memberSpu(String id, String rate) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(id);
		spu.setName("测试商品");
		spu.setStatus("1");
		spu.setSalesPrice(new BigDecimal("39.90"));
		spu.setOriginalPrice(new BigDecimal("59.90"));
		if (rate != null) {
			spu.setIsMemberItem(1);
			spu.setMemberDiscountRate(new BigDecimal(rate));
		}
		return spu;
	}

	private ZoneRuleSnapshotVO afterSnapshot(int memberTag, String rate) {
		ZoneRuleSnapshotVO snapshot = new ZoneRuleSnapshotVO();
		snapshot.setMemberTag(memberTag);
		snapshot.setMemberDiscountRate(rate == null ? null : new BigDecimal(rate));
		snapshot.setClearanceTag(0);
		snapshot.setSkuOverrideCount(0);
		return snapshot;
	}

}
