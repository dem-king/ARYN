package com.aryn.cloud.boot.ai;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.remote.RemoteOrderScenarioService;
import com.aryn.cloud.order.api.vo.OrderScenarioFactsVO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.vo.*;
import com.aryn.cloud.promotion.mapper.*;
import com.aryn.cloud.promotion.service.impl.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际MySQL+租户插件；只能连接aryn_ai_verify_*隔离库，外部事实域为fixture。 */
@EnabledIfEnvironmentVariable(named = "ARYN_AI_TEST_JDBC_URL", matches = ".*")
class CampaignSimulationPersistenceTest {
	private CampaignSimulationMapper records;
	private B2bActivityCommandService commands;
	private PromotionEngineServiceImpl engine;
	private ActivityPublishGovernanceService governance;
	private CampaignSimulationService service;
	private RemoteOrderScenarioService order;
	private JdbcTemplate jdbc;
	private String tenant;

	@BeforeEach void setup() throws Exception {
		String url = System.getenv("ARYN_AI_TEST_JDBC_URL");
		assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aryn_ai_verify_[a-zA-Z0-9_]+(?:\\?.*)?"));
		var data = new DriverManagerDataSource(url, System.getenv("ARYN_AI_TEST_DB_USER"), System.getenv("ARYN_AI_TEST_DB_PASSWORD"));
		jdbc = new JdbcTemplate(data);
		tenant = "sim-" + UUID.randomUUID().toString().substring(0, 16);
		ArynTenantContextHolder.setTenantId(tenant);
		var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
		var interceptor = new MybatisPlusInterceptor();
		interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
			public Expression getTenantId() { return new StringValue(ArynTenantContextHolder.getTenantId()); }
		}));
		var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(data); factory.setConfiguration(config); factory.setPlugins(interceptor);
		var sqlFactory = factory.getObject();
		sqlFactory.getConfiguration().addMapper(CampaignSimulationMapper.class);
		sqlFactory.getConfiguration().addMapper(PromotionActivityMapper.class);
		var session = new SqlSessionTemplate(sqlFactory);
		records = session.getMapper(CampaignSimulationMapper.class);
		var activities = session.getMapper(PromotionActivityMapper.class);
		governance = new ActivityPublishGovernanceService(activities);
		commands = new B2bActivityCommandService(activities, governance, mock(PromoCalendarMapper.class));
		engine = new PromotionEngineServiceImpl(activities, mock(PricePolicyMapper.class), mock(PricePolicyItemMapper.class),
				mock(PromoCalendarMapper.class), new PromotionLockUsage(mock(PromotionLockMapper.class)));
		order = mock(RemoteOrderScenarioService.class);
		when(order.capture(any(), any())).thenAnswer(invocation -> {
			PromotionContextDTO source = invocation.getArgument(0);
			var facts = new OrderScenarioFactsVO(); facts.setContext(source);
			var spu = new GoodsSpu(); spu.setId("spu");
			facts.setSkus(List.of(new GoodsSku().setId("sku").setTenantId(tenant).setStock(100)
					.setSalesPrice(new BigDecimal("100")).setGoodsSpu(spu)));
			return facts;
		});
		when(order.compare(any(), any(), any())).thenAnswer(invocation -> {
			var result = new CampaignOrderComparisonVO(); result.setBaseline(quote(invocation.getArgument(1)));
			result.setProposed(quote(invocation.getArgument(2))); return result;
		});
		service = newService();
	}
	@AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }

	@Test void survivesNewServiceInstanceAndSourceRevisionChangeInvalidatesRecord() {
		var activity = commands.create(draft());
		long before = jdbc.queryForObject("SELECT COUNT(*) FROM promotion_activity WHERE tenant_id=?", Long.class, tenant);
		var request = request(activity.getId());
		var result = service.simulate("operator", request);
		assertEquals(0, new BigDecimal("10").compareTo(result.getProposed().getWholeDiscount()));
		assertTrue(newService().get("operator", result.getId(), true).isCurrent());
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM campaign_simulation WHERE tenant_id=?", Long.class, tenant));
		assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM promotion_activity WHERE tenant_id=?", Long.class, tenant));
		assertEquals("1", commands.get(activity.getId()).getStatus());
		assertEquals(0, commands.get(activity.getId()).getConsumedBudget().signum());
		var edit = draft(); edit.setExpectedRevision(0L); edit.setActivityName("人工修订后");
		commands.update(activity.getId(), edit);
		assertFalse(newService().get("operator", result.getId(), true).isCurrent());
	}

	@Test void actualSqlScopesTenantActorLogicalDeletionAndExpiry() {
		var activity = commands.create(draft()); var result = service.simulate("operator", request(activity.getId()));
		assertThrows(RuntimeException.class, () -> newService().get("another", result.getId(), true));
		ArynTenantContextHolder.setTenantId("other-tenant");
		assertThrows(RuntimeException.class, () -> newService().get("operator", result.getId(), true));
		ArynTenantContextHolder.setTenantId(tenant);
		jdbc.update("UPDATE campaign_simulation SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE tenant_id=? AND id=?", tenant, result.getId());
		assertFalse(newService().get("operator", result.getId(), true).isCurrent());
		jdbc.update("UPDATE campaign_simulation SET del_flag='1' WHERE tenant_id=? AND id=?", tenant, result.getId());
		assertThrows(RuntimeException.class, () -> newService().get("operator", result.getId(), true));
	}

	@Test void lateSourceChangeDuringRemoteWorkDoesNotPersistMislabelledResult() {
		var activity = commands.create(draft());
		doAnswer(invocation -> {
			var edit = draft(); edit.setExpectedRevision(0L); commands.update(activity.getId(), edit);
			var result = new CampaignOrderComparisonVO(); result.setBaseline(quote(invocation.getArgument(1)));
			result.setProposed(quote(invocation.getArgument(2))); return result;
		}).when(order).compare(any(), any(), any());
		assertThrows(RuntimeException.class, () -> service.simulate("operator", request(activity.getId())));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM campaign_simulation WHERE tenant_id=?", Long.class, tenant));
	}

	private CampaignSimulationService newService() {
		var target = new CampaignSimulationService(records, commands, governance, engine, new ObjectMapper().findAndRegisterModules(), mock(CampaignDraftStore.class), mock(PricePolicyService.class));
		ReflectionTestUtils.setField(target, "remoteOrderScenarioService", order); return target;
	}
	private B2bActivitySaveDTO draft() {
		var dto = new B2bActivitySaveDTO(); dto.setActivityName("隔离模拟草稿"); dto.setActivityType("7"); dto.setScopeType("1");
		dto.setStartTime(LocalDateTime.of(2031, 1, 1, 0, 0)); dto.setEndTime(LocalDateTime.of(2031, 3, 1, 0, 0));
		dto.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]}"); return dto;
	}
	private CampaignSimulationDTO request(String activityId) {
		var dto = new CampaignSimulationDTO(); dto.setDraftActivityId(activityId); dto.setExpectedRevision(0L);
		var sample = new PromotionContextDTO(); sample.setTenantId(tenant); sample.setUserId("buyer"); sample.setPurchaseScene("1");
		sample.setActivityTime(LocalDateTime.of(2031, 2, 1, 0, 0));
		var item = new PromotionContextDTO.SkuItem(); item.setSkuId("sku"); item.setQuantity(2); item.setSalesPrice(new BigDecimal("100"));
		sample.setSkuItems(List.of(item)); dto.setSample(sample); return dto;
	}
	private CampaignOrderComparisonVO.Quote quote(PromotionCalculationVO calculation) {
		var result = new CampaignOrderComparisonVO.Quote(); result.setGoodsPayable(new BigDecimal("200").subtract(calculation.getTotalDiscount()));
		var line = new CampaignOrderComparisonVO.Line(); line.setSkuId("sku"); line.setGoodsPayable(result.getGoodsPayable());
		result.setLines(List.of(line)); return result;
	}
}
