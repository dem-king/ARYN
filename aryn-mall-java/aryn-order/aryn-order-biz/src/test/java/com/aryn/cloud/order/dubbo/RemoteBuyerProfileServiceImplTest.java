package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.constant.BuyerOrgConstants;
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.mapper.BuyerOrgMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.promotion.api.vo.FirstOrderEligibilityVO;
import com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 三轨首单资格判定契约（mock BaseMapper，不依赖真实 DB）。
 *
 * <p>核心口径：付过款即消耗（含退款，pay_status 单向）；个人只看 scene=1/历史空场景；
 * 船供按船 scene=2；企业按主体 scene=3 且只算「下单时主体已审核通过」的快照单——
 * 待审核期间下的单支付后不烧首单。任何异常 fail-closed，租户上下文用后还原。
 */
class RemoteBuyerProfileServiceImplTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private OrderInfoMapper orderInfoMapper;

	private BuyerOrgMapper buyerOrgMapper;

	private RemoteMallUserService remoteMallUserService;

	private RemoteBuyerProfileServiceImpl service;

	@BeforeAll
	static void initTableInfoCache() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BuyerOrg.class);
	}

	@BeforeEach
	void setUp() {
		orderInfoMapper = mock(OrderInfoMapper.class);
		buyerOrgMapper = mock(BuyerOrgMapper.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		service = new RemoteBuyerProfileServiceImpl(mock(OrderItemMapper.class), orderInfoMapper, buyerOrgMapper,
				remoteMallUserService);
		ArynTenantContextHolder.setTenantId("caller-tenant");
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private BuyerOrg org(String auditStatus, String status) {
		BuyerOrg org = new BuyerOrg();
		org.setId("org-1");
		org.setTenantId(TENANT);
		org.setAuditStatus(auditStatus);
		org.setStatus(status);
		return org;
	}

	@SuppressWarnings("rawtypes")
	private Wrapper captureCountWrapper() {
		ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(orderInfoMapper).selectCount(captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<String, Object> countParams() {
		return ((AbstractWrapper) captureCountWrapper()).getParamNameValuePairs();
	}

	@Test
	@DisplayName("个人新人：只统计 scene=1 或历史空场景的已支付单，船供/企业单不消耗")
	void personalEligibilityFiltersPersonalScene() {
		when(orderInfoMapper.selectCount(any())).thenReturn(0L);
		when(remoteMallUserService.getUserRegisterTime(USER)).thenReturn(LocalDateTime.now().minusDays(3));

		NewbieEligibilityVO result = service.checkPersonalNewbieEligibility(TENANT, USER, 30);

		assertTrue(result.getEligible());
		String sql = captureCountWrapper().getSqlSegment();
		assertTrue(sql.contains("purchase_scene"));
		assertTrue(sql.contains("IS NULL"));
		assertTrue(sql.contains("pay_status"));
		assertTrue(countParams().containsValue("1"));
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("个人新人：有已支付个人单即失去资格（含退款单，pay_status 不回退）")
	void personalPaidOrderConsumesEligibility() {
		when(orderInfoMapper.selectCount(any())).thenReturn(1L);

		NewbieEligibilityVO result = service.checkPersonalNewbieEligibility(TENANT, USER, 30);

		assertFalse(result.getEligible());
		assertEquals("ALREADY_PAID", result.getReason());
		verifyNoInteractions(remoteMallUserService);
	}

	@Test
	@DisplayName("个人新人：注册超期失去资格")
	void personalRegisterTimeout() {
		when(orderInfoMapper.selectCount(any())).thenReturn(0L);
		when(remoteMallUserService.getUserRegisterTime(USER)).thenReturn(LocalDateTime.now().minusDays(31));

		NewbieEligibilityVO result = service.checkPersonalNewbieEligibility(TENANT, USER, 30);

		assertFalse(result.getEligible());
		assertEquals("REGISTER_TIMEOUT", result.getReason());
	}

	@Test
	@DisplayName("个人新人：查询异常按非新人处理（fail-closed），租户上下文还原")
	void personalQueryErrorFailsClosed() {
		when(orderInfoMapper.selectCount(any())).thenThrow(new RuntimeException("db down"));

		NewbieEligibilityVO result = service.checkPersonalNewbieEligibility(TENANT, USER, 30);

		assertFalse(result.getEligible());
		assertEquals("ERROR", result.getReason());
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("船供首单：按船统计 scene=2 已支付单，个人到船单（scene=1）不消耗")
	void shipEligibilityCountsShipSceneOnly() {
		when(orderInfoMapper.selectCount(any())).thenReturn(0L);

		FirstOrderEligibilityVO result = service.checkShipFirstOrderEligibility(TENANT, "vessel-1");

		assertTrue(result.getEligible());
		String sql = captureCountWrapper().getSqlSegment();
		assertTrue(sql.contains("vessel_id"));
		assertTrue(sql.contains("purchase_scene"));
		assertTrue(sql.contains("pay_status"));
		assertTrue(countParams().containsValue("2"));
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("船供首单：该船已有已支付船供单即失去资格")
	void shipPaidOrderConsumesEligibility() {
		when(orderInfoMapper.selectCount(any())).thenReturn(1L);

		FirstOrderEligibilityVO result = service.checkShipFirstOrderEligibility(TENANT, "vessel-1");

		assertFalse(result.getEligible());
		assertEquals("ALREADY_PAID", result.getReason());
	}

	@Test
	@DisplayName("企业首单：主体不存在/待审核/已停用均不享，且不查订单")
	void orgMustExistAndBeApproved() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(null);
		assertEquals("ORG_NOT_FOUND", service.checkBuyerOrgFirstOrderEligibility(TENANT, "org-1").getReason());

		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_PENDING,
				BuyerOrgConstants.STATUS_NORMAL));
		assertEquals("ORG_NOT_APPROVED", service.checkBuyerOrgFirstOrderEligibility(TENANT, "org-1").getReason());

		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_APPROVED, "0"));
		assertEquals("ORG_DISABLED", service.checkBuyerOrgFirstOrderEligibility(TENANT, "org-1").getReason());

		verifyNoInteractions(orderInfoMapper);
	}

	@Test
	@DisplayName("企业首单：只统计下单时主体已审核通过的已支付单（快照=1），待审核期间支付不烧首单")
	void orgEligibilityCountsApprovedSnapshotOnly() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_APPROVED,
				BuyerOrgConstants.STATUS_NORMAL));
		// 待审核期间下的单快照为 0，被 SQL 过滤，计数为 0 → 审核通过后仍可享首单
		when(orderInfoMapper.selectCount(any())).thenReturn(0L);

		FirstOrderEligibilityVO result = service.checkBuyerOrgFirstOrderEligibility(TENANT, "org-1");

		assertTrue(result.getEligible());
		String sql = captureCountWrapper().getSqlSegment();
		assertTrue(sql.contains("buyer_org_id"));
		assertTrue(sql.contains("purchase_scene"));
		assertTrue(sql.contains("pay_status"));
		assertTrue(sql.contains("buyer_org_audit_status"));
		assertTrue(countParams().containsValue("3"));
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("企业首单：审核通过后下的已支付单消耗资格，退款不恢复")
	void orgApprovedPaidOrderConsumesEligibility() {
		when(buyerOrgMapper.selectOne(any())).thenReturn(org(BuyerOrgConstants.AUDIT_APPROVED,
				BuyerOrgConstants.STATUS_NORMAL));
		when(orderInfoMapper.selectCount(any())).thenReturn(1L);

		FirstOrderEligibilityVO result = service.checkBuyerOrgFirstOrderEligibility(TENANT, "org-1");

		assertFalse(result.getEligible());
		assertEquals("ALREADY_PAID", result.getReason());
	}

	@Test
	@DisplayName("企业首单：查询异常按不合格处理（fail-closed）")
	void orgQueryErrorFailsClosed() {
		when(buyerOrgMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));

		FirstOrderEligibilityVO result = service.checkBuyerOrgFirstOrderEligibility(TENANT, "org-1");

		assertFalse(result.getEligible());
		assertEquals("ERROR", result.getReason());
		assertEquals("caller-tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("缺参直接拒绝，不触发任何查询")
	void blankParamsRejected() {
		assertEquals("PARAM_INVALID", service.checkPersonalNewbieEligibility(TENANT, " ", 30).getReason());
		assertEquals("PARAM_INVALID", service.checkShipFirstOrderEligibility(TENANT, null).getReason());
		assertEquals("PARAM_INVALID", service.checkBuyerOrgFirstOrderEligibility(" ", "org-1").getReason());
		verifyNoInteractions(orderInfoMapper, buyerOrgMapper);
	}
}