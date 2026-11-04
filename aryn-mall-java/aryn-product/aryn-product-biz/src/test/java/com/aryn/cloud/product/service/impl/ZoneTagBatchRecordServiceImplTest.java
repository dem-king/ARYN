package com.aryn.cloud.product.service.impl;

import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ZoneTagPersistSnapshot;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatch;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatchItem;
import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.api.vo.ZoneSkuStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchDetailVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchVO;
import com.aryn.cloud.product.api.vo.ZoneTagUndoResultVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.GoodsZoneTagBatchItemMapper;
import com.aryn.cloud.product.mapper.GoodsZoneTagBatchMapper;
import com.aryn.cloud.product.mapper.ZoneGoodsQueryMapper;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次记录/撤销契约：批次落库、详情快照还原、撤销冲突跳过与回滚。
 */
class ZoneTagBatchRecordServiceImplTest {

	private static final String TENANT = "tenant-1";

	private GoodsZoneTagBatchMapper batchMapper;

	private GoodsZoneTagBatchItemMapper itemMapper;

	private GoodsSpuMapper spuMapper;

	private GoodsSkuMapper skuMapper;

	private ZoneGoodsQueryMapper zoneGoodsQueryMapper;

	private ZoneTagBatchRecordServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				GoodsZoneTagBatch.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				GoodsZoneTagBatchItem.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
		batchMapper = mock(GoodsZoneTagBatchMapper.class);
		itemMapper = mock(GoodsZoneTagBatchItemMapper.class);
		spuMapper = mock(GoodsSpuMapper.class);
		skuMapper = mock(GoodsSkuMapper.class);
		zoneGoodsQueryMapper = mock(ZoneGoodsQueryMapper.class);
		service = new ZoneTagBatchRecordServiceImpl(batchMapper, itemMapper, spuMapper, skuMapper,
				zoneGoodsQueryMapper, new GoodsZoneTagStateResolver());
	}

	@Test
	@DisplayName("保存批次：不覆盖调用方生成的 id；明细回填 batchId/tenantId")
	void saveBatchKeepsBatchIdAndBackfillsItems() {
		GoodsZoneTagBatch batch = new GoodsZoneTagBatch();
		batch.setId("batch-1");
		batch.setTenantId(TENANT);
		GoodsZoneTagBatchItem item = new GoodsZoneTagBatchItem();
		item.setSpuId("spu-1");

		service.saveBatch(batch, List.of(item));

		verify(batchMapper).insert(batch);
		assertThat(batch.getDelFlag()).isEqualTo("0");
		assertThat(item.getBatchId()).isEqualTo("batch-1");
		assertThat(item.getTenantId()).isEqualTo(TENANT);
		assertThat(item.getDelFlag()).isEqualTo("0");
		verify(itemMapper).insert(item);
	}

	@Test
	@DisplayName("批次分页：按 create_time 倒序返回头字段")
	void pageMapsBatchHeader() {
		Page<GoodsZoneTagBatch> raw = new Page<>(1, 10);
		raw.setRecords(List.of(batchEntity("batch-1")));
		raw.setTotal(1);
		when(batchMapper.selectPage(any(), any())).thenReturn(raw);

		IPage<ZoneTagBatchVO> result = service.page(1, 10);

		assertThat(result.getTotal()).isEqualTo(1);
		assertThat(result.getRecords()).hasSize(1);
		ZoneTagBatchVO vo = result.getRecords().get(0);
		assertThat(vo.getBatchId()).isEqualTo("batch-1");
		assertThat(vo.getTotal()).isEqualTo(3);
		assertThat(vo.getChanged()).isEqualTo(2);
		assertThat(vo.getMemberAction()).isEqualTo("ADD");
		assertThat(vo.getSyncStatus()).isEqualTo("SUCCESS");
		assertThat(vo.getUndoStatus()).isEqualTo("NONE");
	}

	@Test
	@DisplayName("批次详情：批次不存在抛业务异常")
	void detailThrowsWhenMissing() {
		when(batchMapper.selectById("batch-x")).thenReturn(null);

		ArynBusinessException ex = catchThrowableOfType(() -> service.detail("batch-x"),
				ArynBusinessException.class);
		assertThat(ex).isNotNull();
		assertThat(ex.getMsg()).contains("批次不存在");
	}

	@Test
	@DisplayName("批次详情：由 before/after 快照还原分专区变更")
	void detailDerivesChangesFromSnapshots() {
		GoodsZoneTagBatch batch = batchEntity("batch-1");
		when(batchMapper.selectById("batch-1")).thenReturn(batch);
		GoodsZoneTagBatchItem item = itemEntity("spu-1", "ADD",
				snapshot(0, null, null, null), snapshot(1, new BigDecimal("0.9"), null, null));
		when(itemMapper.selectList(any())).thenReturn(List.of(item));

		ZoneTagBatchDetailVO detail = service.detail("batch-1");

		assertThat(detail.getBatchId()).isEqualTo("batch-1");
		assertThat(detail.getItems()).hasSize(1);
		assertThat(detail.getItems().get(0).getChanges()).hasSize(1);
		assertThat(detail.getItems().get(0).getChanges().get(0).getZone()).isEqualTo("member");
		assertThat(detail.getItems().get(0).getChanges().get(0).getType()).isEqualTo("ADD");
	}

	@Test
	@DisplayName("撤销：已撤销批次重复撤销报错")
	void undoRejectsAlreadyUndoneBatch() {
		GoodsZoneTagBatch batch = batchEntity("batch-1");
		batch.setUndoStatus("UNDONE");
		when(batchMapper.selectById("batch-1")).thenReturn(batch);

		ArynBusinessException ex = catchThrowableOfType(() -> service.undo("batch-1"),
				ArynBusinessException.class);
		assertThat(ex).isNotNull();
		assertThat(ex.getMsg()).contains("已撤销");
	}

	@Test
	@DisplayName("撤销冲突：当前值已被后续修改时跳过并报告 conflicts")
	void undoSkipsConflictWhenCurrentValueChanged() {
		GoodsZoneTagBatch batch = batchEntity("batch-1");
		when(batchMapper.selectById("batch-1")).thenReturn(batch);
		GoodsZoneTagBatchItem item = itemEntity("spu-1", "UPDATE",
				snapshot(1, new BigDecimal("0.8"), null, null),
				snapshot(1, new BigDecimal("0.9"), null, null));
		when(itemMapper.selectList(any())).thenReturn(List.of(item));
		// 当前商品被后续改成了 0.95，不等于批次 after 的 0.9
		GoodsSpu spu = goodsSpu("spu-1", new BigDecimal("0.95"));
		when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));
		when(zoneGoodsQueryMapper.selectSkuZoneRows(any())).thenReturn(List.of(sku("sku-1")));
		when(batchMapper.update(any(), any())).thenReturn(1);

		ZoneTagUndoResultVO result = service.undo("batch-1");

		assertThat(result.getRestored()).isZero();
		assertThat(result.getSkipped()).isEqualTo(1);
		assertThat(result.getConflicts()).hasSize(1);
		assertThat(result.getConflicts().get(0).getReason()).contains("SPU 专区字段已被后续修改");
		verify(spuMapper, never()).update(any(), any());
		verify(skuMapper, never()).update(any(), any());
		// 批次仍标记为已撤销（避免无限重试），事务内由调用方保证失败回滚
		verify(batchMapper).update(any(), any());
	}

	@Test
	@DisplayName("撤销成功：当前值等于 after 时按 before 回写 SPU 与 SKU")
	void undoRestoresWhenCurrentMatchesAfter() {
		GoodsZoneTagBatch batch = batchEntity("batch-1");
		when(batchMapper.selectById("batch-1")).thenReturn(batch);
		GoodsZoneTagBatchItem item = itemEntity("spu-1", "UPDATE",
				snapshot(1, new BigDecimal("0.8"), null, null),
				snapshot(1, new BigDecimal("0.9"), 0, null));
		when(itemMapper.selectList(any())).thenReturn(List.of(item));
		GoodsSpu spu = goodsSpu("spu-1", new BigDecimal("0.9"));
		when(spuMapper.selectBatchIds(any())).thenReturn(List.of(spu));
		when(zoneGoodsQueryMapper.selectSkuZoneRows(any())).thenReturn(List.of(sku("sku-1")));
		when(batchMapper.update(any(), any())).thenReturn(1);

		ZoneTagUndoResultVO result = service.undo("batch-1");

		assertThat(result.getRestored()).isEqualTo(1);
		assertThat(result.getSkipped()).isZero();
		AbstractWrapper<?, ?, ?> spuWrapper = captureUpdate(spuMapper);
		assertThat(setValue(spuWrapper, "member_discount_rate"))
			.isEqualTo(new BigDecimal("0.8"));
		AbstractWrapper<?, ?, ?> skuWrapper = captureUpdate(skuMapper);
		assertThat(setValue(skuWrapper, "is_member_item")).isNull();
	}

	@Test
	@DisplayName("撤销：无实际变更项（NO_CHANGE）时不动商品，仅标记批次已撤销")
	void undoNoopsWhenNoChangedItems() {
		GoodsZoneTagBatch batch = batchEntity("batch-1");
		when(batchMapper.selectById("batch-1")).thenReturn(batch);
		GoodsZoneTagBatchItem item = itemEntity("spu-1", "NO_CHANGE",
				snapshot(1, new BigDecimal("0.9"), null, null),
				snapshot(1, new BigDecimal("0.9"), null, null));
		when(itemMapper.selectList(any())).thenReturn(List.of(item));
		when(batchMapper.update(any(), any())).thenReturn(1);

		ZoneTagUndoResultVO result = service.undo("batch-1");

		assertThat(result.getRestored()).isZero();
		assertThat(result.getSkipped()).isZero();
		verify(spuMapper, never()).update(any(), any());
		verify(batchMapper).update(any(), any());
	}

	@Test
	@DisplayName("回写同步状态：批次不存在时只告警不抛错（同步结果不影响打标结果）")
	void updateSyncStatusIsBestEffort() {
		when(batchMapper.update(any(), any())).thenReturn(0);

		service.updateSyncStatus("batch-x", "FAILED", "同步失败原因");

		verify(batchMapper).update(any(), any());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private AbstractWrapper<?, ?, ?> captureUpdate(Object mapper) {
		ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
		if (mapper instanceof GoodsSpuMapper) {
			verify((GoodsSpuMapper) mapper).update(any(), captor.capture());
		}
		else {
			verify((GoodsSkuMapper) mapper).update(any(), captor.capture());
		}
		assertThat(captor.getValue()).isInstanceOf(AbstractWrapper.class);
		return (AbstractWrapper<?, ?, ?>) captor.getValue();
	}

	private Object setValue(AbstractWrapper<?, ?, ?> wrapper, String column) {
		String sqlSet = wrapper.getSqlSet();
		assertThat(sqlSet).isNotNull();
		String segment = Arrays.stream(sqlSet.split(","))
			.filter(part -> part.startsWith(column + "="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("SET 片段缺少列：" + column + "，实际：" + sqlSet));
		String paramRef = segment.substring(segment.indexOf("#{") + 2, segment.indexOf("}"));
		String key = paramRef.substring(paramRef.lastIndexOf('.') + 1);
		return wrapper.getParamNameValuePairs().get(key);
	}

	private GoodsZoneTagBatch batchEntity(String batchId) {
		GoodsZoneTagBatch batch = new GoodsZoneTagBatch();
		batch.setId(batchId);
		batch.setTenantId(TENANT);
		batch.setBatchNo("Z202611040001");
		batch.setOperatorId("user-1");
		batch.setOperatorName("运营");
		batch.setMemberAction("ADD");
		batch.setClearanceAction("NONE");
		batch.setTotal(3);
		batch.setChanged(2);
		batch.setAdded(2);
		batch.setUpdated(0);
		batch.setRemoved(0);
		batch.setNoChange(1);
		batch.setFailed(0);
		batch.setSyncStatus("SUCCESS");
		batch.setUndoStatus("NONE");
		return batch;
	}

	private GoodsZoneTagBatchItem itemEntity(String spuId, String changeType, String before, String after) {
		GoodsZoneTagBatchItem item = new GoodsZoneTagBatchItem();
		item.setSpuId(spuId);
		item.setSpuName("测试商品");
		item.setChangeType(changeType);
		item.setSuccess(true);
		item.setMessage("OK");
		item.setBeforeSnapshot(before);
		item.setAfterSnapshot(after);
		return item;
	}

	private GoodsSpu goodsSpu(String spuId, BigDecimal memberRate) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(spuId);
		spu.setName("测试商品");
		spu.setStatus("1");
		spu.setSalesPrice(new BigDecimal("39.9"));
		spu.setOriginalPrice(new BigDecimal("59.9"));
		spu.setIsMemberItem(1);
		spu.setMemberDiscountRate(memberRate);
		spu.setIsClearanceItem(0);
		return spu;
	}

	private GoodsSku sku(String skuId) {
		GoodsSku sku = new GoodsSku();
		sku.setId(skuId);
		sku.setSpuId("spu-1");
		sku.setStatus("0");
		sku.setSalesPrice(new BigDecimal("39.9"));
		sku.setOriginalPrice(new BigDecimal("59.9"));
		sku.setIsMemberItem(0);
		return sku;
	}

	private String snapshot(Integer memberTag, BigDecimal memberRate, Integer skuMemberTag,
			BigDecimal skuMemberRate) {
		ZoneRuleSnapshotVO rule = new ZoneRuleSnapshotVO();
		rule.setMemberTag(memberTag);
		rule.setMemberDiscountRate(memberRate);
		rule.setClearanceTag(0);
		ZoneSkuStateVO sku = new ZoneSkuStateVO();
		sku.setSkuId("sku-1");
		sku.setIsMemberItem(skuMemberTag);
		sku.setMemberDiscountRate(skuMemberRate);
		ZoneTagPersistSnapshot snapshot = new ZoneTagPersistSnapshot();
		snapshot.setRule(rule);
		snapshot.setSkus(List.of(sku));
		return JSONUtil.toJsonStr(snapshot);
	}

}
