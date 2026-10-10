package com.aryn.cloud.order.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.SharedCartImportConfirmDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartImport;
import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartImportVO;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
 * 补给单 Excel 导入服务契约测试。
 *
 * <p>守四件最容易做错、且用户能直接感知的事：
 * <ol>
 *   <li>权限：普通成员不能导入（服务端拦，不能只靠前端隐藏入口）；</li>
 *   <li>幂等：重复确认不把数量翻倍；</li>
 *   <li>唯一键：已逻辑移除的明细必须**复活**而不是新增（会撞唯一键）；</li>
 *   <li>合并：同一 SKU 多行 / 与已有明细重复时合并数量，不产生第二行。</li>
 * </ol>
 */
class SharedCartImportServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String OWNER = "owner-1";

	private static final String MEMBER = "member-1";

	private static final String CART_ID = "cart-1";

	private static final String IMPORT_ID = "import-1";

	private static final String SKU_ID = "sku-1";

	private SharedCartMapper cartMapper;

	private SharedCartMemberMapper memberMapper;

	private SharedCartItemMapper itemMapper;

	private SharedCartImportMapper importMapper;

	private SharedCartImportRowMapper importRowMapper;

	private RemoteReplenishImportMatchService remoteReplenishImportMatchService;

	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		cartMapper = mock(SharedCartMapper.class);
		memberMapper = mock(SharedCartMemberMapper.class);
		itemMapper = mock(SharedCartItemMapper.class);
		importMapper = mock(SharedCartImportMapper.class);
		importRowMapper = mock(SharedCartImportRowMapper.class);
		remoteReplenishImportMatchService = mock(RemoteReplenishImportMatchService.class);

		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCart.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCartMember.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCartItem.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCartImport.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				SharedCartImportRow.class);

		service = new SharedCartServiceImpl(cartMapper, memberMapper, itemMapper, importMapper, importRowMapper,
				mock(IOrderInfoService.class));
		ReflectionTestUtils.setField(service, "remoteReplenishImportMatchService", remoteReplenishImportMatchService);
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", mock(RemoteGoodsSkuService.class));
		ReflectionTestUtils.setField(service, "remoteVesselService", mock(RemoteVesselService.class));
		ReflectionTestUtils.setField(service, "remoteMallUserService", mock(RemoteMallUserService.class));
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private SharedCart collectingCart() {
		SharedCart cart = new SharedCart();
		cart.setId(CART_ID);
		cart.setTenantId(TENANT);
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		cart.setOwnerUserId(OWNER);
		cart.setConfirmerUserId(OWNER);
		cart.setVesselId("vessel-1");
		cart.setVesselCallId("call-1");
		return cart;
	}

	private SharedCartImport pendingImport() {
		SharedCartImport job = new SharedCartImport();
		job.setId(IMPORT_ID);
		job.setCartId(CART_ID);
		job.setTenantId(TENANT);
		job.setStatus(SharedCartImport.STATUS_PENDING);
		job.setTotalRows(1);
		job.setMatchedRows(1);
		return job;
	}

	private SharedCartImportRow row(int rowNo, String resultType, Integer quantity) {
		SharedCartImportRow entity = new SharedCartImportRow();
		entity.setId("row-" + rowNo);
		entity.setImportId(IMPORT_ID);
		entity.setCartId(CART_ID);
		entity.setTenantId(TENANT);
		entity.setRowNo(rowNo);
		entity.setMatchType("CODE");
		entity.setMatchedSkuId(SKU_ID);
		entity.setMatchedSpuId("spu-1");
		entity.setQuantity(quantity);
		entity.setResultType(resultType);
		return entity;
	}

	private ReplenishImportMatchVO currentSku(int stock, int moq, int stepQty, String skuStatus, String spuStatus) {
		ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
		vo.setMatchType("CODE");
		vo.setSkuId(SKU_ID);
		vo.setSpuId("spu-1");
		vo.setStock(stock);
		vo.setMoq(moq);
		vo.setStepQty(stepQty);
		vo.setSkuStatus(skuStatus);
		vo.setSpuStatus(spuStatus);
		return vo;
	}

	private SharedCartImportConfirmDTO confirmAction(int rowNo, String action) {
		SharedCartImportConfirmDTO dto = new SharedCartImportConfirmDTO();
		SharedCartImportConfirmDTO.RowAction rowAction = new SharedCartImportConfirmDTO.RowAction();
		rowAction.setRowNo(rowNo);
		rowAction.setAction(action);
		dto.setRows(List.of(rowAction));
		return dto;
	}

	@Test
	@DisplayName("普通成员不能导入：必须由确认人/发起人执行")
	void memberCannotImport() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		// 非发起人、非确认人：成员表里查不到 can_confirm=1
		when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
		// 任务与行都准备好：否则去掉权限校验后会在"任务不存在"处抛异常，
		// 断言依旧通过 —— 权限用例就变成了空转（缺陷注入实测）。
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 6)));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.confirmImport(TENANT, MEMBER, CART_ID, IMPORT_ID,
						confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC)));
		assertTrue(exception.getMsg().contains("确认人"), "必须明确拒绝的是权限，而不是任务不存在");
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("普通成员不能上传导入：权限与确认并入一致")
	void memberCannotPreview() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
		when(remoteReplenishImportMatchService.matchRows(anyString(), anyList())).thenReturn(List.of());

		byte[] excel = buildExcel(List.of(List.of("", "IMPA1", "抽纸", "10包", "4", "包", "")));
		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.previewImport(TENANT, MEMBER, CART_ID, "a.xlsx", excel.length, excel));
		assertTrue(exception.getMsg().contains("确认人"));
		verify(importMapper, never()).insert(any(SharedCartImport.class));
	}

	@Test
	@DisplayName("确认并入：新 SKU 落一条明细，计划量=已确认数量，已采量为 0")
	void confirmCreatesNewItem() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 6)));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		SharedCartItem saved = captor.getValue();
		assertEquals(SKU_ID, saved.getSkuId());
		assertEquals(OWNER, saved.getUserId());
		assertEquals(6, saved.getRequestedQuantity());
		assertEquals(SharedCartItem.ITEM_PENDING, saved.getStatus());
	}

	@Test
	@DisplayName("幂等：已并入的任务重复确认不再写明细，也不重复累加数量")
	void confirmIsIdempotent() {
		SharedCartImport done = pendingImport();
		done.setStatus(SharedCartImport.STATUS_IMPORTED);
		done.setImportedRows(2);
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(done);
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		SharedCartImportVO vo = service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		assertEquals(SharedCartImport.STATUS_IMPORTED, vo.getStatus());
		assertEquals(2, vo.getImportedRows());
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
		verify(itemMapper, never()).updateById(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("已逻辑移除的明细必须复活，不能新增（唯一键不含 status，插入会撞键）")
	void confirmRevivesRemovedItem() {
		SharedCartItem removed = new SharedCartItem();
		removed.setId("item-removed");
		removed.setCartId(CART_ID);
		removed.setTenantId(TENANT);
		removed.setUserId(OWNER);
		removed.setSkuId(SKU_ID);
		removed.setStatus(SharedCartItem.ITEM_REMOVED);
		removed.setRequestedQuantity(3);

		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 6)));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(removed));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		verify(itemMapper, never()).insert(any(SharedCartItem.class));
		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).updateById(captor.capture());
		assertEquals(SharedCartItem.ITEM_PENDING, captor.getValue().getStatus());
		assertEquals("item-removed", captor.getValue().getId());
		// 复活时按本次导入数量重置，而不是把旧数量加上去（旧数量属于已取消的需求）
		assertEquals(6, captor.getValue().getRequestedQuantity());
	}

	@Test
	@DisplayName("与已有明细重复时合并数量，不产生第二条明细")
	void confirmMergesExistingItem() {
		SharedCartItem existing = new SharedCartItem();
		existing.setId("item-existing");
		existing.setCartId(CART_ID);
		existing.setTenantId(TENANT);
		existing.setUserId(OWNER);
		existing.setSkuId(SKU_ID);
		existing.setStatus(SharedCartItem.ITEM_PENDING);
		existing.setRequestedQuantity(2);

		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 6)));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		verify(itemMapper, never()).insert(any(SharedCartItem.class));
		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).updateById(captor.capture());
		assertEquals(8, captor.getValue().getRequestedQuantity());
	}

	@Test
	@DisplayName("同一 SKU 多行合并成一条明细，数量相加")
	void confirmMergesMultipleRowsOfSameSku() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 4), row(2, SharedCartImportRow.RESULT_OK, 6)));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper, times(1)).insert(captor.capture());
		assertEquals(10, captor.getValue().getRequestedQuantity());
	}

	private SharedCartImportRow chainRow(int rowNo, Integer quantity, String personName) {
		SharedCartImportRow entity = row(rowNo, SharedCartImportRow.RESULT_OK, quantity);
		entity.setSourceType(SharedCartImportRow.SOURCE_CHAIN);
		entity.setPersonName(personName);
		return entity;
	}

	@Test
	@DisplayName("接龙人名命中成员自填姓名：明细挂真实成员，不带归属标签")
	void confirmChainRowResolvesRealMember() {
		SharedCartMember member = new SharedCartMember();
		member.setCartId(CART_ID);
		member.setUserId(MEMBER);
		member.setDisplayName("任化东");
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(chainRow(1, 4, "任化东")));
		when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		assertEquals(MEMBER, captor.getValue().getUserId());
		assertEquals("", captor.getValue().getAttributedName());
	}

	@Test
	@DisplayName("接龙人名不是系统用户：明细挂操作者 + 归属姓名标签")
	void confirmChainRowFallsBackToAttributedName() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(chainRow(1, 4, "水手长")));
		// 成员词典里没有「水手长」
		when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		assertEquals(OWNER, captor.getValue().getUserId());
		assertEquals("水手长", captor.getValue().getAttributedName());
	}

	@Test
	@DisplayName("两个接龙人订同一种商品：按归属姓名各占一行，数量不串")
	void confirmChainTwoPersonsSameSkuStaySeparate() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(chainRow(1, 4, "任化东"), chainRow(2, 6, "汤伟杰")));
		when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper, times(2)).insert(captor.capture());
		List<SharedCartItem> saved = captor.getAllValues();
		assertEquals("任化东", saved.get(0).getAttributedName());
		assertEquals(4, saved.get(0).getRequestedQuantity());
		assertEquals("汤伟杰", saved.get(1).getAttributedName());
		assertEquals(6, saved.get(1).getRequestedQuantity());
	}

	@Test
	@DisplayName("未处置的行按跳过处理，并如实计入 skippedRows")
	void unhandledRowsAreSkipped() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 4)));

		SharedCartImportConfirmDTO empty = new SharedCartImportConfirmDTO();
		empty.setRows(List.of());
		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID, empty);

		verify(itemMapper, never()).insert(any(SharedCartItem.class));
		ArgumentCaptor<SharedCartImport> captor = ArgumentCaptor.forClass(SharedCartImport.class);
		verify(importMapper).updateById(captor.capture());
		assertEquals(0, captor.getValue().getImportedRows());
		assertEquals(1, captor.getValue().getSkippedRows());
	}

	@Test
	@DisplayName("确认时商品已下架：该行跳过，不写进补给单")
	void confirmSkipsOffShelfSku() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 6)));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "1", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		verify(itemMapper, never()).insert(any(SharedCartItem.class));
		ArgumentCaptor<SharedCartImport> captor = ArgumentCaptor.forClass(SharedCartImport.class);
		verify(importMapper).updateById(captor.capture());
		assertEquals(1, captor.getValue().getSkippedRows());
	}

	@Test
	@DisplayName("确认时库存不足：该行跳过，不把超库存数量写进清单")
	void confirmSkipsOverStockSku() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OK, 6)));
		// 库存 5、数量 6：数量规则通过（MOQ=1、步长=1），只有库存这一层能拦住
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(5, 1, 1, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		verify(itemMapper, never()).insert(any(SharedCartItem.class));
		ArgumentCaptor<SharedCartImport> captor = ArgumentCaptor.forClass(SharedCartImport.class);
		verify(importMapper).updateById(captor.capture());
		assertEquals(1, captor.getValue().getSkippedRows());
	}

	@Test
	@DisplayName("人工补选：改用客户端指定 SKU，并按其数量规则校验")
	void replaceSkuUsesRequestedSku() {
		SharedCartImportRow unmatched = row(1, SharedCartImportRow.RESULT_UNMATCHED, 4);
		unmatched.setMatchedSkuId(null);
		unmatched.setMatchType("NONE");
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(unmatched));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		ReplenishImportMatchVO picked = currentSku(100, 4, 2, "0", "1");
		picked.setSkuId("sku-picked");
		picked.setSpuId("spu-picked");
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList())).thenReturn(List.of(picked));

		SharedCartImportConfirmDTO dto = new SharedCartImportConfirmDTO();
		SharedCartImportConfirmDTO.RowAction action = new SharedCartImportConfirmDTO.RowAction();
		action.setRowNo(1);
		action.setAction(SharedCartImportRow.ACTION_REPLACE_SKU);
		action.setSkuId("sku-picked");
		dto.setRows(List.of(action));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID, dto);

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		assertEquals("sku-picked", captor.getValue().getSkuId());
		assertEquals(4, captor.getValue().getRequestedQuantity());
	}

	@Test
	@DisplayName("调减数量：采用客户端数量，且必须仍满足 MOQ/步长")
	void adjustQuantityUsesClientValue() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OVER_STOCK, 20)));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(8, 4, 2, "0", "1")));

		SharedCartImportConfirmDTO dto = new SharedCartImportConfirmDTO();
		SharedCartImportConfirmDTO.RowAction action = new SharedCartImportConfirmDTO.RowAction();
		action.setRowNo(1);
		action.setAction(SharedCartImportRow.ACTION_ADJUST_QTY);
		action.setQuantity(8);
		dto.setRows(List.of(action));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID, dto);

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		assertEquals(8, captor.getValue().getRequestedQuantity());
	}

	@Test
	@DisplayName("调减后仍不符合步长时跳过，不能把非法数量写进清单")
	void adjustQuantityStillInvalidIsSkipped() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class)))
			.thenReturn(List.of(row(1, SharedCartImportRow.RESULT_OVER_STOCK, 20)));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(8, 4, 2, "0", "1")));

		SharedCartImportConfirmDTO dto = new SharedCartImportConfirmDTO();
		SharedCartImportConfirmDTO.RowAction action = new SharedCartImportConfirmDTO.RowAction();
		action.setRowNo(1);
		action.setAction(SharedCartImportRow.ACTION_ADJUST_QTY);
		action.setQuantity(7);
		dto.setRows(List.of(action));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID, dto);

		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("解析：按模板列映射字段，空行跳过，行号连续")
	void previewParsesTemplateRows() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(remoteReplenishImportMatchService.matchRows(anyString(), anyList())).thenReturn(List.of());

		byte[] excel = buildExcel(List.of(
				List.of("", "IMPA123456", "鲜牛奶 950ml", "950ml/瓶", "4", "瓶", "冷藏"),
				List.of("", "", "", "", "", "", ""),
				List.of("", "IMPA999", "番茄 2kg", "2kg/份", "2", "份", "")));

		SharedCartImportVO vo = service.previewImport(TENANT, OWNER, CART_ID, "补给清单.xlsx", excel.length, excel);

		assertEquals(2, vo.getTotalRows(), "空行必须被跳过，行号才与 Excel 视觉行一致");
		assertEquals(2, vo.getUnmatchedRows());
		assertEquals(1, vo.getRows().get(0).getRowNo());
		assertEquals(2, vo.getRows().get(1).getRowNo());
	}

	@Test
	@DisplayName("解析：目录模板中没填数量的行判「未填数量」，不刷成数量异常")
	void previewClassifiesBlankQuantityAsNotFilled() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		ReplenishImportMatchVO matched = currentSku(100, 1, 1, "0", "1");
		when(remoteReplenishImportMatchService.matchRows(anyString(), anyList())).thenReturn(List.of(matched));

		// 目录模板：分类/编码/品名/规格/数量/单位/备注，只有一行填了数量
		byte[] excel = buildExcel(List.of(
				List.of("蔬果/蔬菜", "IMPA1", "抽纸", "10包", "4", "包", ""),
				List.of("蔬果/蔬菜", "IMPA2", "番茄", "2kg/份", "", "份", ""),
				List.of("蔬果/蔬菜", "IMPA3", "土豆", "5kg/袋", "", "袋", "")));
		SharedCartImportVO vo = service.previewImport(TENANT, OWNER, CART_ID, "目录.xlsx", excel.length, excel);

		assertEquals(3, vo.getTotalRows());
		assertEquals(1, vo.getMatchedRows(), "填了数量的那行正常匹配");
		assertEquals(2, vo.getNotFilledRows(), "没填数量的行必须单独计数");
		assertEquals(0, vo.getInvalidRows(), "没填数量不是数量异常，否则报告会被刷屏");
	}

	@Test
	@DisplayName("解析：填了数量的非法文本仍归入数量异常")
	void previewToleratesInvalidQuantity() {
		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		ReplenishImportMatchVO matched = currentSku(100, 1, 1, "0", "1");
		when(remoteReplenishImportMatchService.matchRows(anyString(), anyList())).thenReturn(List.of(matched));
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList())).thenReturn(List.of(matched));

		byte[] excel = buildExcel(List.of(List.of("", "IMPA1", "抽纸", "10包", "十", "包", "")));
		SharedCartImportVO vo = service.previewImport(TENANT, OWNER, CART_ID, "a.xlsx", excel.length, excel);

		assertEquals(1, vo.getTotalRows());
		assertEquals(1, vo.getInvalidRows(), "数量非法必须归入数量异常并由用户调整");
		assertEquals(0, vo.getNotFilledRows(), "填了非法文本不等于没填");
	}

	@Test
	@DisplayName("确认并入：未填数量的行不写明细，也不计入 skippedRows")
	void confirmIgnoresNotFilledRows() {
		SharedCartImportRow filled = row(1, SharedCartImportRow.RESULT_OK, 6);
		SharedCartImportRow blank = row(2, SharedCartImportRow.RESULT_NOT_FILLED, null);
		blank.setMatchedSkuId("sku-2");

		when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(collectingCart());
		when(importMapper.selectOne(any(Wrapper.class))).thenReturn(pendingImport());
		when(importRowMapper.selectList(any(Wrapper.class))).thenReturn(List.of(filled, blank));
		when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(remoteReplenishImportMatchService.matchSkuIds(anyString(), anyList()))
			.thenReturn(List.of(currentSku(100, 4, 2, "0", "1")));

		service.confirmImport(TENANT, OWNER, CART_ID, IMPORT_ID,
				confirmAction(1, SharedCartImportRow.ACTION_ACCEPT_SPEC));

		ArgumentCaptor<SharedCartImport> jobCaptor = ArgumentCaptor.forClass(SharedCartImport.class);
		verify(importMapper).updateById(jobCaptor.capture());
		// 客户没打算买的行既不是「已并入」也不是「被跳过」，它是正常略过
		assertEquals(0, jobCaptor.getValue().getSkippedRows(),
				"未填数量的行算作跳过会让报告说「跳过 268 项」，把正常留空说成处理失败");
		verify(itemMapper, times(1)).insert(any(SharedCartItem.class));
	}

	/** 用 EasyExcel 生成真实 xlsx 字节，避免测试依赖外部文件 */
	private byte[] buildExcel(List<List<String>> rows) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		com.alibaba.excel.EasyExcel.write(out).sheet("补给清单")
			.head(com.aryn.cloud.order.support.ReplenishImportExcel.templateHead()).doWrite(rows);
		return out.toByteArray();
	}

}
