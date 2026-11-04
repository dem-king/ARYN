package com.aryn.cloud.promotion.service;

import com.aryn.cloud.product.api.dto.GoodsZoneSkuSnapshotDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsZoneTagService;
import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.MallZoneSyncResultVO;
import com.aryn.cloud.promotion.api.vo.MallZoneSyncStateVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.ClearanceRuleService;
import com.aryn.cloud.promotion.service.impl.MallZoneActivitySyncServiceImpl;
import com.aryn.cloud.promotion.service.impl.MallZoneSettingService;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.user.api.remote.RemoteMemberLevelService;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员/出清专区系统活动同步契约：幂等、价格口径与到期退出。
 */
class MallZoneActivitySyncTest {

	private static final String TENANT = "tenant-1";

	private PromotionActivityMapper activityMapper;

	private RemoteGoodsZoneTagService goodsZoneTagService;

	private RemoteMemberLevelService memberLevelService;

	private MallZoneSettingService settingService;

	private ClearanceRuleService clearanceRuleService;

	private MallZoneActivitySyncServiceImpl syncService;

	@BeforeEach
	void setUp() {
		activityMapper = mock(PromotionActivityMapper.class);
		goodsZoneTagService = mock(RemoteGoodsZoneTagService.class);
		memberLevelService = mock(RemoteMemberLevelService.class);
		settingService = mock(MallZoneSettingService.class);
		clearanceRuleService = mock(ClearanceRuleService.class);
		when(settingService.getOrDefault(any(), any())).thenAnswer(invocation -> {
			String type = invocation.getArgument(1);
			com.aryn.cloud.promotion.api.entity.MallZoneSetting setting =
				new com.aryn.cloud.promotion.api.entity.MallZoneSetting();
			setting.setZoneType(type);
			setting.setEnabled("1");
			setting.setSortMode("default");
			return setting;
		});
		when(clearanceRuleService.listEnabled(any())).thenReturn(List.of());
		syncService = new MallZoneActivitySyncServiceImpl(activityMapper, settingService, clearanceRuleService);
		ReflectionTestUtils.setField(syncService, "remoteGoodsZoneTagService", goodsZoneTagService);
		ReflectionTestUtils.setField(syncService, "remoteMemberLevelService", memberLevelService);
		ReflectionTestUtils.setField(syncService, "remoteTenantService", mock(RemoteTenantService.class));
		MemberLevelSnapshotVO level = new MemberLevelSnapshotVO();
		level.setLevelId("lv-1");
		level.setLevelName("银卡");
		level.setSortOrder(1);
		when(memberLevelService.listLevels(TENANT)).thenReturn(List.of(level));
	}

	@Test
	@DisplayName("首次同步创建两个系统活动，会员价按率取低并跳过倒挂")
	void createsSystemActivitiesOnFirstSync() throws Exception {
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				memberSku("sku-member", "100", "0.9000", null, null),
				memberSku("sku-inverted", "100", "1.1000", null, null),
				clearanceSku("sku-clear", "150", "200", null, "0.5000", LocalDateTime.now().plusDays(3)),
				clearanceSku("sku-expired", "150", "200", null, "0.5000", LocalDateTime.now().minusDays(1))));

		syncService.syncTenant(TENANT);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
		List<PromotionActivity> inserted = captor.getAllValues();
		PromotionActivity member = inserted.stream()
			.filter(item -> MallZoneActivitySyncServiceImpl.SYSTEM_MEMBER_ZONE_CODE.equals(item.getActivityCode()))
			.findFirst()
			.orElseThrow();
		PromotionActivity clearance = inserted.stream()
			.filter(item -> MallZoneActivitySyncServiceImpl.SYSTEM_CLEARANCE_ZONE_CODE.equals(item.getActivityCode()))
			.findFirst()
			.orElseThrow();
		assertEquals(PromotionActivity.TYPE_MEMBER_PRICE, member.getActivityType());
		assertEquals(PromotionActivity.TYPE_CLEARANCE, clearance.getActivityType());
		assertEquals(PromotionActivity.STATUS_PUBLISHED, member.getStatus());

		JsonNode memberRules = new ObjectMapper().readTree(member.getRules());
		assertEquals("lv-1", memberRules.path("minLevelId").asText());
		assertEquals(1, memberRules.path("skus").size(), "倒挂 SKU 不应写入系统活动");
		assertEquals("sku-member", memberRules.path("skus").get(0).path("skuId").asText());
		assertEquals(0, new BigDecimal("90").compareTo(memberRules.path("skus").get(0).path("unitPrice").decimalValue()));

		JsonNode clearanceRules = new ObjectMapper().readTree(clearance.getRules());
		assertEquals(1, clearanceRules.path("skus").size(), "已到期 SKU 不应写入系统活动");
		assertEquals("sku-clear", clearanceRules.path("skus").get(0).path("skuId").asText());
		assertEquals(0, new BigDecimal("100").compareTo(clearanceRules.path("skus").get(0).path("price").decimalValue()));
	}

	@Test
	@DisplayName("专区默认折扣率：商品未配置出清规则时按原价 × 默认折扣算价")
	void appliesZoneDefaultDiscountRate() throws Exception {
		com.aryn.cloud.promotion.api.entity.MallZoneSetting clearanceSetting =
			new com.aryn.cloud.promotion.api.entity.MallZoneSetting();
		clearanceSetting.setZoneType("clearance");
		clearanceSetting.setEnabled("1");
		clearanceSetting.setSortMode("default");
		clearanceSetting.setDefaultDiscountRate(new BigDecimal("0.5000"));
		when(settingService.getOrDefault(TENANT, "clearance")).thenReturn(clearanceSetting);
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				clearanceSku("sku-default", "150", "200", null, null, LocalDateTime.now().plusDays(3))));

		syncService.syncTenant(TENANT);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper, org.mockito.Mockito.times(1)).insert(captor.capture());
		JsonNode rules = new ObjectMapper().readTree(captor.getValue().getRules());
		assertEquals(0, new BigDecimal("100").compareTo(rules.path("skus").get(0).path("price").decimalValue()));
	}

	@Test
	@DisplayName("出清时间分层：命中规则时在基础出清价上再打折")
	void appliesClearanceTierRate() throws Exception {
		when(clearanceRuleService.resolveTierRate(any(), any(), any())).thenReturn(new BigDecimal("0.5000"));
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				clearanceSku("sku-tier", "150", "200", "100", null, LocalDateTime.now().plusHours(12))));

		syncService.syncTenant(TENANT);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper, org.mockito.Mockito.times(1)).insert(captor.capture());
		JsonNode rules = new ObjectMapper().readTree(captor.getValue().getRules());
		assertEquals(0, new BigDecimal("50").compareTo(rules.path("skus").get(0).path("price").decimalValue()));
	}

	@Test
	@DisplayName("专区开关关闭：不生成出清活动（会员专区不受影响）")
	void disabledClearanceZoneSkipsRules() {
		com.aryn.cloud.promotion.api.entity.MallZoneSetting clearanceSetting =
			new com.aryn.cloud.promotion.api.entity.MallZoneSetting();
		clearanceSetting.setZoneType("clearance");
		clearanceSetting.setEnabled("0");
		when(settingService.getOrDefault(TENANT, "clearance")).thenReturn(clearanceSetting);
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				memberSku("sku-member", "100", "0.9000", null, null),
				clearanceSku("sku-clear", "150", "200", "100", null, LocalDateTime.now().plusDays(3))));

		syncService.syncTenant(TENANT);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper, org.mockito.Mockito.times(1)).insert(captor.capture());
		assertEquals(MallZoneActivitySyncServiceImpl.SYSTEM_MEMBER_ZONE_CODE,
			captor.getValue().getActivityCode());
	}

	@Test
	@DisplayName("会员价未配下限时按成本×倍率兜底，显式下限仍优先")
	void memberPriceCostFloorAppliedWhenExplicitFloorMissing() throws Exception {
		com.aryn.cloud.promotion.api.entity.MallZoneSetting memberSetting =
			new com.aryn.cloud.promotion.api.entity.MallZoneSetting();
		memberSetting.setZoneType("member");
		memberSetting.setEnabled("1");
		memberSetting.setCostFloorMultiplier(new BigDecimal("1.05"));
		when(settingService.getOrDefault(TENANT, "member")).thenReturn(memberSetting);
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		GoodsZoneSkuSnapshotDTO autoFloor = memberSku("sku-auto", "20", "0.5000", null, null);
		autoFloor.setCostPrice(new BigDecimal("10"));
		GoodsZoneSkuSnapshotDTO explicitFloor = memberSku("sku-explicit", "20", "0.5000", null, "9.00");
		explicitFloor.setCostPrice(new BigDecimal("10"));
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(autoFloor, explicitFloor));

		syncService.syncTenant(TENANT);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper).insert(captor.capture());
		JsonNode rules = new ObjectMapper().readTree(captor.getValue().getRules());
		java.util.Map<String, BigDecimal> prices = new java.util.HashMap<>();
		for (JsonNode node : rules.path("skus")) {
			prices.put(node.path("skuId").asText(), node.path("unitPrice").decimalValue());
		}
		// 自动兜底：成本 10 × 1.05 = 10.50 > 5 折价 10.00
		assertEquals(0, new BigDecimal("10.50").compareTo(prices.get("sku-auto")));
		// 显式下限 9.00 优先于成本兜底（5 折价 10.00 已高于 9.00）
		assertEquals(0, new BigDecimal("10.00").compareTo(prices.get("sku-explicit")));
	}

	@Test
	@DisplayName("重复同步更新原活动而非新增，activityId 保持稳定")
	void updatesExistingActivitiesWithoutInsert() {
		PromotionActivity existingMember = systemActivity("act-member", MallZoneActivitySyncServiceImpl.SYSTEM_MEMBER_ZONE_CODE,
			PromotionActivity.TYPE_MEMBER_PRICE);
		PromotionActivity existingClearance = systemActivity("act-clear",
			MallZoneActivitySyncServiceImpl.SYSTEM_CLEARANCE_ZONE_CODE, PromotionActivity.TYPE_CLEARANCE);
		when(activityMapper.selectOne(any())).thenReturn(existingMember, existingClearance);
		when(activityMapper.updateById(any(PromotionActivity.class))).thenReturn(1);
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				memberSku("sku-member", "100", "0.9000", null, null),
				clearanceSku("sku-clear", "150", "200", null, "0.5000", LocalDateTime.now().plusDays(3))));

		syncService.syncTenant(TENANT);

		verify(activityMapper, never()).insert(any(PromotionActivity.class));
		verify(activityMapper, org.mockito.Mockito.times(2)).updateById(any(PromotionActivity.class));
		assertEquals("act-member", existingMember.getId());
		assertNotNull(existingMember.getRules());
		assertTrue(existingMember.getRules().contains("sku-member"));
		assertTrue(existingClearance.getRules().contains("sku-clear"));
	}

	@Test
	@DisplayName("没有可用会员等级时不创建空会员活动，出清活动照常同步")
	void clearsMemberRulesWhenNoLevel() {
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		when(memberLevelService.listLevels(TENANT)).thenReturn(List.of());
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				memberSku("sku-member", "100", "0.9000", null, null),
				clearanceSku("sku-clear", "150", "200", null, "0.5000", LocalDateTime.now().plusDays(3))));

		syncService.syncTenant(TENANT);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper, org.mockito.Mockito.times(1)).insert(captor.capture());
		assertEquals(MallZoneActivitySyncServiceImpl.SYSTEM_CLEARANCE_ZONE_CODE,
			captor.getValue().getActivityCode());
	}

	@Test
	@DisplayName("同步成功返回两专区状态并记录 last_sync（G19）")
	void reportsZonesAndRecordsSyncStatus() throws Exception {
		when(activityMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.insert(any(PromotionActivity.class))).thenReturn(1);
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenReturn(List.of(
				memberSku("sku-member", "100", "0.9000", null, null),
				clearanceSku("sku-clear", "150", "200", null, "0.5000", LocalDateTime.now().plusDays(3))));

		MallZoneSyncResultVO result = syncService.syncTenant(TENANT);

		assertTrue(result.getSuccess());
		assertNotNull(result.getFinishedAt());
		assertEquals(2, result.getZones().size());
		MallZoneSyncStateVO memberState = result.getZones().get(0);
		assertEquals(MallZoneSetting.ZONE_MEMBER, memberState.getZone());
		assertEquals(MallZoneSyncStateVO.LAST_SYNC_SUCCESS, memberState.getLastSyncStatus());
		assertEquals(1, memberState.getRulesSkuCount());
		assertEquals(PromotionActivity.STATUS_PUBLISHED, memberState.getActivityStatus());
		MallZoneSyncStateVO clearanceState = result.getZones().get(1);
		assertEquals(MallZoneSetting.ZONE_CLEARANCE, clearanceState.getZone());
		assertEquals(1, clearanceState.getRulesSkuCount());
		verify(settingService).recordSyncStatus(eq(TENANT), eq(MallZoneSetting.ZONE_MEMBER),
				eq(MallZoneSyncStateVO.LAST_SYNC_SUCCESS), any(), any());
		verify(settingService).recordSyncStatus(eq(TENANT), eq(MallZoneSetting.ZONE_CLEARANCE),
				eq(MallZoneSyncStateVO.LAST_SYNC_SUCCESS), any(), any());
	}

	@Test
	@DisplayName("快照查询失败：返回 FAILED 且不覆盖系统活动（G13）")
	void snapshotFailureAbortsAndReportsFailed() {
		when(goodsZoneTagService.listZoneSkus(TENANT)).thenThrow(new IllegalStateException("db down"));

		MallZoneSyncResultVO result = syncService.syncTenant(TENANT);

		assertFalse(result.getSuccess());
		assertNotNull(result.getMessage());
		assertTrue(result.getMessage().contains("db down"));
		assertEquals(2, result.getZones().size());
		assertEquals(MallZoneSyncStateVO.LAST_SYNC_FAILED, result.getZones().get(0).getLastSyncStatus());
		assertEquals(MallZoneSyncStateVO.LAST_SYNC_FAILED, result.getZones().get(1).getLastSyncStatus());
		// 关键：不得用空快照清空活动
		verify(activityMapper, never()).insert(any(PromotionActivity.class));
		verify(activityMapper, never()).updateById(any(PromotionActivity.class));
		verify(settingService).recordSyncStatus(eq(TENANT), eq(MallZoneSetting.ZONE_MEMBER),
				eq(MallZoneSyncStateVO.LAST_SYNC_FAILED), any(), any());
		verify(settingService).recordSyncStatus(eq(TENANT), eq(MallZoneSetting.ZONE_CLEARANCE),
				eq(MallZoneSyncStateVO.LAST_SYNC_FAILED), any(), any());
	}

	@Test
	@DisplayName("同步状态查询：返回活动状态、rules SKU 数与上次同步结果")
	void syncStatusReadsActivitiesAndSettings() {
		MallZoneSetting memberSetting = new MallZoneSetting();
		memberSetting.setZoneType(MallZoneSetting.ZONE_MEMBER);
		memberSetting.setEnabled("1");
		memberSetting.setLastSyncTime(LocalDateTime.now().minusMinutes(5));
		memberSetting.setLastSyncStatus(MallZoneSyncStateVO.LAST_SYNC_SUCCESS);
		MallZoneSetting clearanceSetting = new MallZoneSetting();
		clearanceSetting.setZoneType(MallZoneSetting.ZONE_CLEARANCE);
		clearanceSetting.setEnabled("0");
		clearanceSetting.setLastSyncStatus(MallZoneSyncStateVO.LAST_SYNC_FAILED);
		clearanceSetting.setLastSyncError("快照查询失败");
		when(settingService.getOrDefault(TENANT, MallZoneSetting.ZONE_MEMBER)).thenReturn(memberSetting);
		when(settingService.getOrDefault(TENANT, MallZoneSetting.ZONE_CLEARANCE)).thenReturn(clearanceSetting);
		PromotionActivity memberActivity = new PromotionActivity();
		memberActivity.setId("act-member");
		memberActivity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		memberActivity.setRules("{\"minLevelId\":\"lv-1\",\"skus\":[{\"skuId\":\"s1\"},{\"skuId\":\"s2\"}]}");
		PromotionActivity clearanceActivity = new PromotionActivity();
		clearanceActivity.setId("act-clear");
		clearanceActivity.setStatus(PromotionActivity.STATUS_PAUSED);
		clearanceActivity.setRules("{\"skus\":[]}");
		when(activityMapper.selectOne(any())).thenReturn(memberActivity, clearanceActivity);

		MallZoneSyncResultVO result = syncService.syncStatus(TENANT);

		assertFalse(result.getSuccess(), "存在上次同步失败的专区时整体标记失败");
		assertNotNull(result.getCheckedAt());
		assertEquals(2, result.getZones().size());
		MallZoneSyncStateVO memberState = result.getZones().get(0);
		assertEquals("act-member", memberState.getActivityId());
		assertEquals(2, memberState.getRulesSkuCount());
		assertEquals(Boolean.TRUE, memberState.getEnabled());
		assertEquals(MallZoneSyncStateVO.LAST_SYNC_SUCCESS, memberState.getLastSyncStatus());
		MallZoneSyncStateVO clearanceState = result.getZones().get(1);
		assertEquals("act-clear", clearanceState.getActivityId());
		assertEquals(PromotionActivity.STATUS_PAUSED, clearanceState.getActivityStatus());
		assertEquals(Boolean.FALSE, clearanceState.getEnabled());
		assertEquals(MallZoneSyncStateVO.LAST_SYNC_FAILED, clearanceState.getLastSyncStatus());
		assertEquals("快照查询失败", clearanceState.getLastError());
	}

	@Test
	@DisplayName("无上次同步记录时同步状态为 UNKNOWN")
	void syncStatusDefaultsToUnknown() {
		when(settingService.getOrDefault(eq(TENANT), any())).thenAnswer(invocation -> {
			MallZoneSetting setting = new MallZoneSetting();
			setting.setZoneType(invocation.getArgument(1));
			setting.setEnabled("1");
			return setting;
		});
		when(activityMapper.selectOne(any())).thenReturn(null);

		MallZoneSyncResultVO result = syncService.syncStatus(TENANT);

		assertTrue(result.getSuccess());
		assertEquals(2, result.getZones().size());
		result.getZones().forEach(state -> {
			assertEquals(MallZoneSyncStateVO.LAST_SYNC_UNKNOWN, state.getLastSyncStatus());
			assertNull(state.getActivityId());
			assertEquals(0, state.getRulesSkuCount());
		});
	}

	private PromotionActivity systemActivity(String id, String code, String type) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId(id);
		activity.setTenantId(TENANT);
		activity.setActivityCode(code);
		activity.setActivityType(type);
		activity.setRules("{}");
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		return activity;
	}

	private GoodsZoneSkuSnapshotDTO memberSku(String skuId, String salesPrice, String rate, String fixed, String floor) {
		GoodsZoneSkuSnapshotDTO sku = new GoodsZoneSkuSnapshotDTO();
		sku.setSkuId(skuId);
		sku.setSpuId("spu-" + skuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		sku.setOriginalPrice(new BigDecimal(salesPrice));
		sku.setMemberItem(true);
		sku.setClearanceItem(false);
		sku.setMemberDiscountRate(rate == null ? null : new BigDecimal(rate));
		sku.setMemberPriceFixed(fixed == null ? null : new BigDecimal(fixed));
		sku.setMemberPriceFloor(floor == null ? null : new BigDecimal(floor));
		return sku;
	}

	private GoodsZoneSkuSnapshotDTO clearanceSku(String skuId, String salesPrice, String originalPrice, String fixed,
			String rate, LocalDateTime expireTime) {
		GoodsZoneSkuSnapshotDTO sku = new GoodsZoneSkuSnapshotDTO();
		sku.setSkuId(skuId);
		sku.setSpuId("spu-" + skuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		sku.setOriginalPrice(new BigDecimal(originalPrice));
		sku.setMemberItem(false);
		sku.setClearanceItem(true);
		sku.setClearancePrice(fixed == null ? null : new BigDecimal(fixed));
		sku.setClearanceDiscountRate(rate == null ? null : new BigDecimal(rate));
		sku.setClearanceExpireTime(expireTime);
		return sku;
	}

}
