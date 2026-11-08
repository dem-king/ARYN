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
import com.aryn.cloud.order.support.ReplenishImportExcel;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/**
 * 清单直下单导入服务契约测试。
 *
 * <p>守几件最容易做错、且用户能直接感知的事：
 * <ol>
 *   <li>归属：报告与复核清单只对上传操作人可见（importId 不可越权读取）；</li>
 *   <li>防篡改：确认以服务端解析行为准，未下架/库存复检不过的行跳过；</li>
 *   <li>幂等：已确认的任务重复确认返回同一份清单，不再改写解析行；</li>
 *   <li>合并：同一 SKU 多行按合并后总量校验库存与起订规则，返回一条；</li>
 *   <li>失败语义：商品 RPC 故障中止回滚，没有可下单商品时保持待确认。</li>
 * </ol>
 */
class ReplenishDirectImportServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String OPERATOR = "operator-1";

	private static final String IMPORT_ID = "import-1";

	private static final String SKU_ID = "sku-1";

	private ReplenishDirectImportMapper importMapper;

	private ReplenishDirectImportRowMapper importRowMapper;

	private RemoteReplenishImportMatchService remoteReplenishImportMatchService;

	private ReplenishDirectImportServiceImpl service;

	@BeforeEach
	void setUp() {
		importMapper = mock(ReplenishDirectImportMapper.class);
		importRowMapper = mock(ReplenishDirectImportRowMapper.class);
		remoteReplenishImportMatchService = mock(RemoteReplenishImportMatchService.class);

		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				ReplenishDirectImport.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				ReplenishDirectImportRow.class);

		service = new ReplenishDirectImportServiceImpl(importMapper, importRowMapper);
		ReflectionTestUtils.setField(service, "remoteReplenishImportMatchService", remoteReplenishImportMatchService);
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", mock(RemoteGoodsSkuService.class));
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", mock(RemoteGoodsSpuService.class));
		ArynTenantContextHolder.setTenantId(TENANT);
		// 默认：操作人名下存在待确认任务；越权用例按具体参数返回 null
		when(importMapper.selectForConfirm(TENANT, IMPORT_ID, OPERATOR)).thenReturn(pendingImport());
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

	private ReplenishImportMatchVO currentSku(int stock, int moq, int stepQty, String skuStatus, String spuStatus) {
		return currentSku(SKU_ID, stock, moq, stepQty, skuStatus, spuStatus);
	}

	private ReplenishImportMatchVO currentSku(String skuId, int stock, int moq, int stepQty, String skuStatus,
			String spuStatus) {
		ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
		vo.setMatchType("CODE");
		vo.setSkuId(skuId);
		vo.setSpuId("spu-" + skuId);
		vo.setStock(stock);
		vo.setMoq(moq);
		vo.setStepQty(stepQty);
		vo.setSkuStatus(skuStatus);
		vo.setSpuStatus(spuStatus);
		return vo;
	}

	private ReplenishDirectImportConfirmDTO confirmAction(int rowNo, String action) {
		return confirmAction(rowNo, action, null, null);
	}

	private ReplenishDirectImportConfirmDTO confirmAction(int rowNo, String action, String skuId, Integer quantity) {
		ReplenishDirectImportConfirmDTO dto = new ReplenishDirectImportConfirmDTO();
		ReplenishDirectImportConfirmDTO.RowAction rowAction = new ReplenishDirectImportConfirmDTO.RowAction();
		rowAction.setRowNo(rowNo);
		rowAction.setAction(action);
		rowAction.setSkuId(skuId);
		rowAction.setQuantity(quantity);
		dto.setRows(List.of(rowAction));
		return dto;
	}

	private ReplenishDirectImportConfirmVO confirm(ReplenishDirectImportConfirmDTO dto) {
		return service.confirm(TENANT, OPERATOR, IMPORT_ID, dto);
	}

	@Test
	@DisplayName("预览：按模板列解析，空行跳过、行号连续，任务与解析行落库")
	void previewParsesTemplateRows() {
		when(remoteReplenishImportMatchService.matchRows(anyString(), anyList())).thenReturn(List.of());

		byte[] excel = buildExcel(List.of(
				List.of("", "IMPA123456", "鲜牛奶 950ml", "950ml/瓶", "4", "瓶", "冷藏"),
				List.of("", "", "", "", "", "", ""),
				List.of("", "IMPA999", "番茄 2kg", "2kg/份", "2", "份", "")));

		ReplenishDirectImportVO vo = service.preview(TENANT, OPERATOR, "清单.xlsx", excel.length, excel);

		assertEquals(2, vo.getTotalRows(), "空行必须被跳过，行号才与 Excel 视觉行一致");
		assertEquals(2, vo.getUnmatchedRows());
		assertEquals(1, vo.getRows().get(0).getRowNo());
		assertEquals(2, vo.getRows().get(1).getRowNo());
		verify(importMapper, times(1)).insert(any(ReplenishDirectImport.class));
		verify(importRowMapper, times(2)).insert(any(ReplenishDirectImportRow.class));
	}

	@Test
	@DisplayName("预览：目录模板中没填数量的行判「未填数量」，不刷成数量异常")
	void previewClassifiesBlankQuantityAsNotFilled() {
		ReplenishImportMatchVO matched = currentSku(100, 1, 1, "0", "1");
		when(remoteReplenishImportMatchService.matchRows(anyString(), anyList())).thenReturn(List.of(matched));

		byte[] excel = buildExcel(List.of(
				List.of("蔬果/蔬菜", "IMPA1", "抽纸", "10包", "4", "包", ""),
				List.of("蔬果/蔬菜", "IMPA2", "番茄", "2kg/份", "", "份", ""),
				List.of("蔬果/蔬菜", "IMPA3", "土豆", "5kg/袋", "", "袋", "")));
		ReplenishDirectImportVO vo = service.preview(TENANT, OPERATOR, "目录.xlsx", excel.length, excel);

		assertEquals(3, vo.getTotalRows());
		assertEquals(1, vo.getMatchedRows(), "填了数量的那行正常匹配");
		assertEquals(2, vo.getNotFilledRows(), "没填数量的行必须单独计数");
		assertEquals(0, vo.getInvalidRows(), "没填数量不是数量异常，否则报告会被刷屏");
	}

	@Test
	@DisplayName("确认：行锁读取任务，同 SKU 两行合并数量且首次与重试返回同一清单")
	void confirmMergesSameSkuRowsAndRetryReturnsSameList() {
		ReplenishDirectImportRow first = row(1, ReplenishDirectImportRow.RESULT_OK, 4);
		ReplenishDirectImportRow second = row(2, ReplenishDirectImportRow.RESULT_OK, 6);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 1, 1, "0", "1")));

		ReplenishDirectImportConfirmVO firstResult = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		verify(importMapper).selectForConfirm(TENANT, IMPORT_ID, OPERATOR);
		verify(importMapper, never()).selectOne(any(Wrapper.class));
		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, firstResult.getStatus());
		assertTrue(firstResult.isCheckoutReady());
		assertEquals(1, firstResult.getItems().size(), "同 SKU 多行必须合并成一条");
		assertEquals(SKU_ID, firstResult.getItems().get(0).getSkuId());
		assertEquals(10, firstResult.getItems().get(0).getQuantity());
		assertEquals(1, firstResult.getConfirmedRows());

		ArgumentCaptor<ReplenishDirectImport> jobCaptor = ArgumentCaptor.forClass(ReplenishDirectImport.class);
		verify(importMapper).updateById(jobCaptor.capture());
		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, jobCaptor.getValue().getStatus());
		assertEquals(1, jobCaptor.getValue().getConfirmedRows());

		// 模拟已确认后再次进入：数据库仍是 4/6 两行，读侧合并后必须还是同一条 10
		ReplenishDirectImport done = pendingImport();
		done.setStatus(ReplenishDirectImport.STATUS_CONFIRMED);
		done.setConfirmedRows(1);
		done.setSkippedRows(0);
		when(importMapper.selectForConfirm(TENANT, IMPORT_ID, OPERATOR)).thenReturn(done);

		ReplenishDirectImportConfirmVO retry = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, retry.getStatus());
		assertEquals(1, retry.getItems().size(), "重试不能退回逐行清单");
		assertEquals(10, retry.getItems().get(0).getQuantity(), "重试不能把已合并的数量再算一遍");
		verify(importRowMapper, times(2)).updateById(any(ReplenishDirectImportRow.class));
	}

	@Test
	@DisplayName("并发/重复确认：已确认分支不再改写解析行与任务")
	void confirmIsIdempotent() {
		ReplenishDirectImport done = pendingImport();
		done.setStatus(ReplenishDirectImport.STATUS_CONFIRMED);
		done.setConfirmedRows(1);
		ReplenishDirectImportRow resolved = row(1, ReplenishDirectImportRow.RESULT_OK, 6);
		resolved.setResolvedAction(ReplenishDirectImportRow.ACTION_ACCEPT_SPEC);
		resolved.setResolvedSkuId(SKU_ID);
		resolved.setResolvedQuantity(6);
		when(importMapper.selectForConfirm(TENANT, IMPORT_ID, OPERATOR)).thenReturn(done);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(resolved));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, result.getStatus());
		assertEquals(1, result.getItems().size());
		assertEquals(6, result.getItems().get(0).getQuantity());
		verify(importRowMapper, never()).updateById(any(ReplenishDirectImportRow.class));
		verify(importMapper, never()).updateById(any(ReplenishDirectImport.class));
	}

	@Test
	@DisplayName("并发确认依赖任务行锁：selectForConfirm 必须带 FOR UPDATE")
	void confirmUsesTaskRowLock() throws Exception {
		org.apache.ibatis.annotations.Select select = ReplenishDirectImportMapper.class
			.getMethod("selectForConfirm", String.class, String.class, String.class)
			.getAnnotation(org.apache.ibatis.annotations.Select.class);

		assertTrue(select != null, "确认入口必须使用带行锁的查询");
		assertTrue(String.join(" ", select.value()).contains("FOR UPDATE"),
				"并发确认必须在任务行上串行化，否则后提交会覆盖先提交的复核结果");
	}

	@Test
	@DisplayName("商品 RPC 故障：中止确认、不写任何行/任务，恢复后可重试成功")
	void rpcFailureAbortsConfirmWithoutWriting() {
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(row(1, ReplenishDirectImportRow.RESULT_OK, 6)));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenThrow(new RuntimeException("dubbo timeout"))
			.thenReturn(List.of(currentSku(100, 1, 1, "0", "1")));

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC)));

		assertTrue(error.getMsg().contains("商品服务暂时不可用"));
		verify(importRowMapper, never()).updateById(any(ReplenishDirectImportRow.class));
		verify(importMapper, never()).updateById(any(ReplenishDirectImport.class));

		// 商品域恢复：同一任务仍可确认（没有被永久标记为已确认）
		ReplenishDirectImportConfirmVO retry = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));
		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, retry.getStatus());
		assertEquals(1, retry.getItems().size());
	}

	@Test
	@DisplayName("合并后库存复检：同 SKU 4+6、库存 8 时整组跳过，不静默超量确认")
	void mergedQuantityIsCheckedAgainstStock() {
		ReplenishDirectImportRow first = row(1, ReplenishDirectImportRow.RESULT_OK, 4);
		ReplenishDirectImportRow second = row(2, ReplenishDirectImportRow.RESULT_OK, 6);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(8, 1, 1, "0", "1")));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(ReplenishDirectImport.STATUS_PENDING, result.getStatus(),
				"没有一行能下单时不能伪终结成已确认");
		assertFalse(result.isCheckoutReady());
		assertTrue(result.getItems().isEmpty());
		assertEquals(2, result.getSkippedRows());
		assertTrue(result.getMessage().contains("没有可下单的商品"));
		assertTrue(first.getResultMessage().contains("合并后共需 10 件"));
		assertTrue(second.getResultMessage().contains("库存仅 8 件"));

		ArgumentCaptor<ReplenishDirectImport> jobCaptor = ArgumentCaptor.forClass(ReplenishDirectImport.class);
		verify(importMapper).updateById(jobCaptor.capture());
		assertEquals(ReplenishDirectImport.STATUS_PENDING, jobCaptor.getValue().getStatus());
	}

	@Test
	@DisplayName("合并后库存复检：4+4、库存 10 时按总量 8 通过")
	void mergedQuantityWithinStockIsAccepted() {
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
				row(1, ReplenishDirectImportRow.RESULT_OK, 4),
				row(2, ReplenishDirectImportRow.RESULT_OK, 4)));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(10, 4, 2, "0", "1")));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, result.getStatus());
		assertEquals(1, result.getItems().size());
		assertEquals(8, result.getItems().get(0).getQuantity());
		assertEquals(0, result.getSkippedRows());
	}

	@Test
	@DisplayName("合并后数量溢出 int：跳过并给出原因，不把负数写进清单")
	void mergedQuantityOverflowIsRejected() {
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
				row(1, ReplenishDirectImportRow.RESULT_OK, 1_500_000_000),
				row(2, ReplenishDirectImportRow.RESULT_OK, 1_500_000_000)));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(Integer.MAX_VALUE, 1, 1, "0", "1")));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(ReplenishDirectImport.STATUS_PENDING, result.getStatus());
		assertTrue(result.getItems().isEmpty());
		assertTrue(result.getRows().get(0).getResultMessage().contains("超出单 SKU 上限"));
	}

	@Test
	@DisplayName("部分行因库存变化跳过：确认成功但如实回传跳过行与原因")
	void partialSkipReturnsSkippedRows() {
		ReplenishDirectImportRow ok = row(1, ReplenishDirectImportRow.RESULT_OK, 4);
		ReplenishDirectImportRow offShelf = row(2, ReplenishDirectImportRow.RESULT_OK, "sku-2", 6);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ok, offShelf));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 1, 1, "0", "1"),
					currentSku("sku-2", 100, 1, 1, "1", "1")));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, result.getStatus());
		assertTrue(result.isCheckoutReady());
		assertEquals(1, result.getItems().size());
		assertEquals(1, result.getSkippedRows());
		assertTrue(result.getMessage().contains("1 行未纳入"));
		assertEquals(ReplenishDirectImportRow.ACTION_SKIP, offShelf.getResolvedAction());
		assertTrue(offShelf.getResultMessage().contains("已下架"));
	}

	@Test
	@DisplayName("全未处置：保持待确认并回传跳过原因，报告页还能继续处理")
	void unhandledRowsKeepPending() {
		ReplenishDirectImportRow unmatched = row(1, ReplenishDirectImportRow.RESULT_UNMATCHED, 4);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(unmatched));

		ReplenishDirectImportConfirmVO result = confirm(new ReplenishDirectImportConfirmDTO());

		assertEquals(ReplenishDirectImport.STATUS_PENDING, result.getStatus());
		assertFalse(result.isCheckoutReady());
		assertTrue(result.getItems().isEmpty());
		assertEquals(1, result.getSkippedRows());
		assertEquals(ReplenishDirectImportRow.ACTION_SKIP, unmatched.getResolvedAction());
		assertTrue(unmatched.getResultMessage().contains("未处置"));
		ArgumentCaptor<ReplenishDirectImport> jobCaptor = ArgumentCaptor.forClass(ReplenishDirectImport.class);
		verify(importMapper).updateById(jobCaptor.capture());
		assertEquals(ReplenishDirectImport.STATUS_PENDING, jobCaptor.getValue().getStatus());
		assertEquals(0, jobCaptor.getValue().getConfirmedRows());
	}

	@Test
	@DisplayName("确认时商品已下架：行跳过、任务保持待确认")
	void confirmSkipsOffShelfSku() {
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(row(1, ReplenishDirectImportRow.RESULT_OK, 6)));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 1, 1, "1", "1")));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertTrue(result.getItems().isEmpty(), "下架商品不能进复核清单");
		assertEquals(ReplenishDirectImport.STATUS_PENDING, result.getStatus());
		assertEquals(1, result.getSkippedRows());
	}

	@Test
	@DisplayName("归属：非上传操作人确认/取回被拒绝，不能拿 importId 越权读他人清单")
	void nonOperatorIsRejected() {
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());

		ArynBusinessException confirmError = assertThrows(ArynBusinessException.class,
				() -> service.confirm(TENANT, "someone-else", IMPORT_ID,
						confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC)));
		assertTrue(confirmError.getMsg().contains("不存在"), "对越权者按任务不存在处理，不暴露任务的存在性");
		verify(importRowMapper, never()).updateById(any(ReplenishDirectImportRow.class));

		assertThrows(ArynBusinessException.class, () -> service.getImport(TENANT, "someone-else", IMPORT_ID));
	}

	@Test
	@DisplayName("确认：未填数量的行不计入 skippedRows，也不进清单")
	void confirmIgnoresNotFilledRows() {
		ReplenishDirectImportRow filled = row(1, ReplenishDirectImportRow.RESULT_OK, 6);
		ReplenishDirectImportRow blank = row(2, ReplenishDirectImportRow.RESULT_NOT_FILLED, null);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(filled, blank));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 1, 1, "0", "1")));

		ReplenishDirectImportConfirmVO result = confirm(confirmAction(1, ReplenishDirectImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(1, result.getItems().size());
		assertEquals(0, result.getSkippedRows(),
				"未填数量的行算作跳过会让报告说「跳过 268 项」，把正常留空说成处理失败");
		verify(importRowMapper, times(1)).updateById(any(ReplenishDirectImportRow.class));
	}

	@Test
	@DisplayName("调量与人工补选落到同一 SKU：按合并总量 10 通过")
	void adjustedAndReplacedRowsMergeIntoSameSku() {
		ReplenishDirectImportRow overStock = row(1, ReplenishDirectImportRow.RESULT_OVER_STOCK, 100);
		ReplenishDirectImportRow unmatched = row(2, ReplenishDirectImportRow.RESULT_UNMATCHED, "sku-old", 6);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(overStock, unmatched));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(10, 1, 1, "0", "1"),
					currentSku("sku-old", 10, 1, 1, "0", "1")));

		ReplenishDirectImportConfirmDTO dto = new ReplenishDirectImportConfirmDTO();
		ReplenishDirectImportConfirmDTO.RowAction adjust = new ReplenishDirectImportConfirmDTO.RowAction();
		adjust.setRowNo(1);
		adjust.setAction(ReplenishDirectImportRow.ACTION_ADJUST_QTY);
		adjust.setQuantity(4);
		ReplenishDirectImportConfirmDTO.RowAction replace = new ReplenishDirectImportConfirmDTO.RowAction();
		replace.setRowNo(2);
		replace.setAction(ReplenishDirectImportRow.ACTION_REPLACE_SKU);
		replace.setSkuId(SKU_ID);
		dto.setRows(List.of(adjust, replace));

		ReplenishDirectImportConfirmVO result = confirm(dto);

		assertEquals(ReplenishDirectImport.STATUS_CONFIRMED, result.getStatus());
		assertEquals(1, result.getItems().size());
		assertEquals(10, result.getItems().get(0).getQuantity());
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

	/** 用 EasyExcel 生成真实 xlsx 字节，避免测试依赖外部文件 */
	private byte[] buildExcel(List<List<String>> rows) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		com.alibaba.excel.EasyExcel.write(out).sheet("补给清单")
			.head(ReplenishImportExcel.templateHead()).doWrite(rows);
		return out.toByteArray();
	}

}
