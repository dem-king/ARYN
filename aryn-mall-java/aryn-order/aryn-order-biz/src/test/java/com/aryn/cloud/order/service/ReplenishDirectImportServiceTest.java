package com.aryn.cloud.order.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ReplenishDirectImportConfirmDTO;
import com.aryn.cloud.order.api.entity.ReplenishDirectImport;
import com.aryn.cloud.order.api.entity.ReplenishDirectImportRow;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportConfirmVO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportVO;
import com.aryn.cloud.order.mapper.ReplenishDirectImportMapper;
import com.aryn.cloud.order.mapper.ReplenishDirectImportRowMapper;
import com.aryn.cloud.order.service.impl.ReplenishDirectImportServiceImpl;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 历史报告只读恢复、归属校验与同步写入下线回归。 */
class ReplenishDirectImportServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String OPERATOR = "operator-1";

	private static final String IMPORT_ID = "import-1";

	private static final String SKU_ID = "sku-1";

	private ReplenishDirectImportMapper importMapper;

	private ReplenishDirectImportRowMapper importRowMapper;


	private ReplenishDirectImportServiceImpl service;

	@BeforeEach
	void setUp() {
		importMapper = mock(ReplenishDirectImportMapper.class);
		importRowMapper = mock(ReplenishDirectImportRowMapper.class);

		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				ReplenishDirectImport.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				ReplenishDirectImportRow.class);

		service = new ReplenishDirectImportServiceImpl(importMapper, importRowMapper);

		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", mock(RemoteGoodsSkuService.class));
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", mock(RemoteGoodsSpuService.class));
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private ReplenishDirectImport pendingImport() {
		ReplenishDirectImport job = new ReplenishDirectImport();
		job.setId(IMPORT_ID);
		job.setTenantId(TENANT);
		job.setStatus(ReplenishDirectImport.STATUS_PENDING);
		job.setOperatorUserId(OPERATOR);
		job.setTotalRows(1);
		job.setMatchedRows(1);
		job.setSkippedRows(0);
		return job;
	}

	private ReplenishDirectImportRow row(int rowNo, String resultType, Integer quantity) {
		return row(rowNo, resultType, SKU_ID, quantity);
	}

	private ReplenishDirectImportRow row(int rowNo, String resultType, String skuId, Integer quantity) {
		ReplenishDirectImportRow entity = new ReplenishDirectImportRow();
		entity.setId("row-" + rowNo);
		entity.setImportId(IMPORT_ID);
		entity.setTenantId(TENANT);
		entity.setRowNo(rowNo);
		entity.setMatchType("CODE");
		entity.setMatchedSkuId(skuId);
		entity.setMatchedSpuId("spu-" + skuId);
		entity.setMatchedName("商品-" + skuId);
		entity.setQuantity(quantity);
		entity.setResultType(resultType);
		return entity;
	}

	@Test
	@DisplayName("旧同步写入必须引导采购工作台且不写历史任务")
	void legacyWritesAreRejected() {
		assertThrows(ArynBusinessException.class, () -> service.preview(TENANT, OPERATOR, "a.xlsx", 0, new byte[0]));
		assertThrows(ArynBusinessException.class, () -> service.confirm(TENANT, OPERATOR, IMPORT_ID, new ReplenishDirectImportConfirmDTO()));
		verify(importMapper, never()).insert(any(ReplenishDirectImport.class));
		verify(importMapper, never()).updateById(any(ReplenishDirectImport.class));
		verify(importRowMapper, never()).insert(any(ReplenishDirectImportRow.class));
		verify(importRowMapper, never()).updateById(any(ReplenishDirectImportRow.class));
	}

	@Test
	@DisplayName("恢复：已确认任务只读重建同一份合并清单，可重新进入结算")
	void resumeConfirmedRebuildsMergedItemsWithoutWriting() {
		ReplenishDirectImport done = pendingImport();
		done.setStatus(ReplenishDirectImport.STATUS_CONFIRMED);
		done.setConfirmedRows(2);
		ReplenishDirectImportRow first = row(1, ReplenishDirectImportRow.RESULT_OK, 4);
		first.setResolvedAction(ReplenishDirectImportRow.ACTION_ACCEPT_SPEC);
		first.setResolvedSkuId(SKU_ID);
		first.setResolvedQuantity(4);
		ReplenishDirectImportRow second = row(2, ReplenishDirectImportRow.RESULT_OK, 6);
		second.setResolvedAction(ReplenishDirectImportRow.ACTION_ACCEPT_SPEC);
		second.setResolvedSkuId(SKU_ID);
		second.setResolvedQuantity(6);
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(done);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

		ReplenishDirectImportConfirmVO result = service.resume(TENANT, OPERATOR, IMPORT_ID);

		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, result.getStatus());
		assertTrue(result.isCheckoutReady());
		assertEquals(1, result.getItems().size());
		assertEquals(10, result.getItems().get(0).getQuantity());
		verify(importRowMapper, never()).updateById(any(ReplenishDirectImportRow.class));
		verify(importMapper, never()).updateById(any(ReplenishDirectImport.class));
	}

	@Test
	@DisplayName("恢复：待确认任务不可直接结算，返回不可结算回执引导回报告页")
	void resumePendingIsNotCheckoutReady() {
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(row(1, ReplenishDirectImportRow.RESULT_UNMATCHED, 4)));

		ReplenishDirectImportConfirmVO result = service.resume(TENANT, OPERATOR, IMPORT_ID);

		assertEquals(ReplenishDirectImport.STATUS_PENDING, result.getStatus());
		assertFalse(result.isCheckoutReady());
		assertTrue(result.getItems().isEmpty());
		assertTrue(result.getMessage().contains("尚未确认"));
	}

	@Test
	@DisplayName("恢复：非上传操作人不能拿 importId 恢复他人清单")
	void resumeRejectsNonOperator() {
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());

		assertThrows(ArynBusinessException.class, () -> service.resume(TENANT, "someone-else", IMPORT_ID));
	}

	@Test
	@DisplayName("最近可继续导入：已确认任务也能被找回（重启恢复）")
	void findLatestReturnsConfirmedImport() {
		ReplenishDirectImport done = pendingImport();
		done.setStatus(ReplenishDirectImport.STATUS_CONFIRMED);
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(done);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		ReplenishDirectImportVO vo = service.findLatest(TENANT, OPERATOR);

		assertEquals(IMPORT_ID, vo.getImportId());
		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, vo.getStatus());
	}

}
