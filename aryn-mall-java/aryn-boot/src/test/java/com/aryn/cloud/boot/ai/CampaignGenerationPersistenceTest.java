package com.aryn.cloud.boot.ai;

import com.aryn.cloud.common.ai.error.*;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.remote.RemoteOrderScenarioService;
import com.aryn.cloud.order.api.vo.OrderScenarioFactsVO;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.*;
import com.aryn.cloud.promotion.mapper.*;
import com.aryn.cloud.promotion.service.impl.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.*;
import net.sf.jsqlparser.expression.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际隔离MySQL、租户插件和Spring事务代理；模型与商品域使用可控fixture，不计费。 */
@EnabledIfEnvironmentVariable(named="ARYN_AI_TEST_JDBC_URL", matches=".*")
class CampaignGenerationPersistenceTest {
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
	private DriverManagerDataSource data;
	private JdbcTemplate jdbc;
	private String tenant;
	private com.aryn.cloud.promotion.service.IPageDesignService pages;
	private CampaignPageDraftService campaignPages;
 private CampaignPublishService publisher;
 private CampaignPublishStore publications;
 private AtomicReference<String> publishFailure;
	private CampaignSimulationService simulationFixture;
	private PricePolicyService policyService;
	private CampaignPolicyDraftService policyDrafts;
	private CampaignDraftStore draftStore;
	private CampaignDraftService drafts;
	private CampaignGenerationStore jobs;
	private CampaignGenerationService service;
	private CampaignGenerationJobMapper jobMapper;
	private B2bActivityCommandService commands;
	private CampaignProposalValidator validator;
	private CampaignGenerationParser parser;
	private AtomicReference<Runnable> duringCall;
	private List<String> opened;
	private AtomicInteger calls;
	private AtomicReference<BigDecimal> livePrice;
	private SqlSessionTemplate session;
	private ActivityPublishGovernanceService governance;
	private RemoteOrderScenarioService order;
	private AtomicReference<String> reply;
	private AtomicReference<RuntimeException> failure;

	@BeforeEach void setup() throws Exception {
		String url=System.getenv("ARYN_AI_TEST_JDBC_URL");
		assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aryn_ai_verify_[a-zA-Z0-9_]+(?:\\?.*)?"));
		data=new DriverManagerDataSource(url,System.getenv("ARYN_AI_TEST_DB_USER"),System.getenv("ARYN_AI_TEST_DB_PASSWORD"));
		jdbc=new JdbcTemplate(data); tenant="gen-"+UUID.randomUUID().toString().substring(0,16); ArynTenantContextHolder.setTenantId(tenant);
		var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
		var plugin=new MybatisPlusInterceptor(); plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
			public Expression getTenantId() { return new StringValue(ArynTenantContextHolder.getTenantId()); }
		}));
		var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(data); factory.setConfiguration(config); factory.setPlugins(plugin);
		var sql=factory.getObject();
		for (Class<?> type: List.of(CampaignDraftMapper.class,CampaignGenerationJobMapper.class,PromotionActivityMapper.class,CampaignSimulationMapper.class,PricePolicyMapper.class,PricePolicyItemMapper.class,PageDesignMapper.class,PageDesignVersionMapper.class,CouponInfoMapper.class,CampaignPublishOperationMapper.class)) { sql.getConfiguration().addMapper(type); }
		session=new SqlSessionTemplate(sql); jobMapper=session.getMapper(CampaignGenerationJobMapper.class);
		var activityMapper=session.getMapper(PromotionActivityMapper.class);
		governance=new ActivityPublishGovernanceService(activityMapper);
		commands=tx(new B2bActivityCommandService(activityMapper,governance,mock(PromoCalendarMapper.class)));
		draftStore=tx(new CampaignDraftStore(session.getMapper(CampaignDraftMapper.class),commands,json));
		validator=new CampaignProposalValidator(governance);
		order=mock(RemoteOrderScenarioService.class); livePrice=new AtomicReference<>(new BigDecimal("100"));
		when(order.capture(any(),any())).thenAnswer(call -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"商品RPC不能在写事务中执行");
			var facts=new OrderScenarioFactsVO(); facts.setContext(call.getArgument(0));
			facts.setSkus(List.of(new GoodsSku().setId("sku").setTenantId(tenant).setSalesPrice(livePrice.get())
				.setCostPrice(new BigDecimal("60")).setStock(100).setGoodsSpu(new GoodsSpu().setId("spu").setName("核实商品")))); return facts;
		});
		ReflectionTestUtils.setField(validator,"remoteOrderScenarioService",order);
		policyService=tx(new PricePolicyService(session.getMapper(PricePolicyMapper.class),session.getMapper(PricePolicyItemMapper.class)));
		drafts=new CampaignDraftService(draftStore,validator,policyService);
		policyDrafts=new CampaignPolicyDraftService(draftStore,drafts,validator,tx(new CampaignPolicyDraftStore(draftStore,session.getMapper(CampaignDraftMapper.class),policyService,new CampaignResourceDigest(json))));
		var pageTarget=new PageDesignServiceImpl(mock(org.springframework.data.redis.core.StringRedisTemplate.class),mock(org.redisson.api.RedissonClient.class),mock(com.aryn.cloud.promotion.service.PageDesignAuditService.class),mock(com.aryn.cloud.promotion.service.PageDesignPreviewService.class),mock(PageDesignVersionMapper.class));
		ReflectionTestUtils.setField(pageTarget,"baseMapper",session.getMapper(PageDesignMapper.class));pages=tx(pageTarget);
		simulationFixture=mock(CampaignSimulationService.class);
		campaignPages=new CampaignPageDraftService(drafts,tx(new CampaignPageDraftStore(draftStore,session.getMapper(CampaignDraftMapper.class),pages,new CampaignResourceDigest(json))),validator,new CampaignPageDraftBuilder(),new DefaultPageDesignDocumentValidator(),pages,simulationFixture,new CampaignResourceDigest(json));
  publishFailure=new AtomicReference<>();
  var redis=mock(org.redisson.api.RedissonClient.class);var lock=mock(org.redisson.api.RLock.class);
  when(redis.getLock(anyString())).thenReturn(lock);when(lock.tryLock(anyLong(),any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
  var themes=mock(com.aryn.cloud.promotion.service.IPageDesignThemeService.class);when(themes.embedThemeSnapshot(anyString())).thenAnswer(c->c.getArgument(0));
  var user=mock(com.aryn.cloud.common.core.security.UserSupplier.class);when(user.getCurrentUserName()).thenReturn("测试操作者");
  var versionTarget=tx(new PageDesignVersionServiceImpl(session.getMapper(PageDesignMapper.class),session.getMapper(PageDesignVersionMapper.class),new DefaultPageDesignDocumentValidator(),themes,mock(com.aryn.cloud.promotion.service.PageDesignAuditService.class),mock(org.springframework.data.redis.core.StringRedisTemplate.class),redis,user));
  var versions=mock(PageDesignVersionServiceImpl.class);when(versions.publishCampaignSnapshot(anyString(),anyString(),anyString(),anyLong())).thenAnswer(c->{
   var result=versionTarget.publishCampaignSnapshot(c.getArgument(0),c.getArgument(1),c.getArgument(2),c.getArgument(3));
   if("AFTER_PAGE".equals(publishFailure.get())){throw new IllegalStateException("injected page failure");}return result;
  });
  var realOperations=session.getMapper(CampaignPublishOperationMapper.class);
  var operationMapper=mock(CampaignPublishOperationMapper.class,org.mockito.AdditionalAnswers.delegatesTo(realOperations));
  doAnswer(c->{var op=(CampaignPublishOperation)c.getArgument(0);var result=realOperations.updateById(op);if(op.getStatus().equals(publishFailure.get())){throw new IllegalStateException("injected phase failure");}return result;}).when(operationMapper).updateById(any(CampaignPublishOperation.class));
  publications=tx(new CampaignPublishStore(operationMapper,draftStore,session.getMapper(CampaignDraftMapper.class),activityMapper,session.getMapper(PricePolicyMapper.class),session.getMapper(PricePolicyItemMapper.class),session.getMapper(PageDesignMapper.class),session.getMapper(CouponInfoMapper.class),commands,policyService,versions,new CampaignResourceDigest(json)));
  publisher=new CampaignPublishService(publications,campaignPages,drafts,validator);

		jobs=tx(new CampaignGenerationStore(jobMapper,draftStore,json)); parser=new CampaignGenerationParser(validator,json);
		calls=new AtomicInteger(); duringCall=new AtomicReference<>(() -> {}); opened=new ArrayList<>(); reply=new AtomicReference<>(valid()); failure=new AtomicReference<>();
		CampaignModelGateway gateway=(task,version) -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"配置RPC不能在写事务中执行"); opened.add(version);
			return new CampaignModelGateway.Session() {
				public CampaignModelGateway.Metadata metadata() { return new CampaignModelGateway.Metadata(version==null?"config-A":version,"provider","fake-model"); }
				public JsonNode generate(String input) {
					assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"模型请求不能在写事务中执行");
					calls.incrementAndGet(); duringCall.get().run(); if(failure.get()!=null){throw failure.get();}
					try { return json.readTree(reply.get()); } catch(Exception invalid) { throw new IllegalStateException(invalid); }
				}
			};
		};
		service=new CampaignGenerationService(drafts,validator,parser,jobs,gateway,json);
	}
	@AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }

	@Test void durableGenerationPinsConfigDeduplicatesAndCreatesOnlyUnpublishedActivity() {
		var draft=drafts.save("actor",null,definition());
		var job=service.enqueue("actor",draft.getId(),0L,"network-retry");
		assertEquals("config-A",job.getConfigVersion()); assertEquals(0,calls.get());
		assertEquals(job.getId(),service.enqueue("actor",draft.getId(),0L,"network-retry").getId()); assertEquals(1,opened.size());
		service.process(job.getId()); assertEquals("SUCCEEDED",jobs.get("actor",job.getId()).getStatus());
		var saved=new CampaignDraftService(draftStore,validator,mock(PricePolicyService.class)).get("actor",draft.getId());
		assertEquals(1L,saved.getRevision()); assertEquals(1,saved.getGenerationVersion()); assertEquals("READY",saved.getStatus());
		assertEquals("1",commands.get(saved.getMainActivityId()).getStatus());
		assertEquals(Arrays.asList(null,"config-A"),opened); assertEquals(1,calls.get());
		assertEquals(1L,count("promotion_activity")); assertEquals(0,commands.get(saved.getMainActivityId()).getConsumedBudget().signum());
		assertEquals(job.getId(),service.enqueue("actor",draft.getId(),0L,"network-retry").getId());
	}

	@Test void manualEditDuringModelCallMakesResultStale() {
		var draft=drafts.save("actor",null,definition()); var job=service.enqueue("actor",draft.getId(),0L,"edit");
		duringCall.set(() -> { var edit=definition();edit.setExpectedRevision(0L);edit.setIntent("人工已改稿");drafts.save("actor",draft.getId(),edit); });
		service.process(job.getId()); assertEquals("STALE",jobs.get("actor",job.getId()).getStatus()); assertEquals(0L,count("promotion_activity"));
		assertEquals("人工已改稿",drafts.get("actor",draft.getId()).getDefinition().getIntent());
	}

	@Test void cancelledOrExpiredWorkerCannotWriteAndReclaimedLeaseHasUniqueWinner() throws Exception {
		var draft=drafts.save("actor",null,definition()); var job=service.enqueue("actor",draft.getId(),0L,"cancel");
		duringCall.set(() -> jobs.cancel("actor",job.getId())); service.process(job.getId());
		assertEquals("CANCELLED",jobs.get("actor",job.getId()).getStatus()); assertEquals(0L,count("promotion_activity"));
		var second=service.enqueue("actor",draft.getId(),0L,"lease"); String old=jobs.claim(second.getId());
		jdbc.update("UPDATE campaign_generation_job SET lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=? AND tenant_id=?",second.getId(),tenant);
		var pool=Executors.newFixedThreadPool(2);
		try {
			Callable<String> claim=() -> {ArynTenantContextHolder.setTenantId(tenant);try{return jobs.claim(second.getId());}finally{ArynTenantContextHolder.removeTenantId();}};
			var results=pool.invokeAll(List.of(claim,claim)); assertEquals(1,results.stream().filter(f -> {try{return f.get()!=null;}catch(Exception e){throw new RuntimeException(e);}}).count());
			assertThrows(RuntimeException.class,() -> jobs.complete(second.getId(),old,proposal(),"{}")); assertEquals(0L,count("promotion_activity"));
		} finally {pool.shutdownNow();}
	}

	@Test void retryKeepsOriginalVersionAndAutomaticRetriesAreBounded() {
		var draft=drafts.save("actor",null,definition()); var job=service.enqueue("actor",draft.getId(),0L,"retry");
		failure.set(new AiCallException(AiErrorCode.TIMEOUT));
		for(int i=0;i<3;i++) {
			service.process(job.getId());
			if(i<2){assertNotNull(jobs.get("actor",job.getId()).getNextRetryAt());jdbc.update("UPDATE campaign_generation_job SET next_retry_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=? AND tenant_id=?",job.getId(),tenant);}
		}
		assertNull(jobs.get("actor",job.getId()).getNextRetryAt()); service.process(job.getId()); assertEquals(3,calls.get());
		assertEquals(Arrays.asList(null,"config-A","config-A","config-A"),opened);
		jobs.retry("actor",job.getId()); failure.set(null); service.process(job.getId()); assertEquals("SUCCEEDED",jobs.get("actor",job.getId()).getStatus());
	}

	@Test void invalidModelOutputAndChangedCurrentPriceCannotEnterDraft() {
		var draft=drafts.save("actor",null,definition()); var job=service.enqueue("actor",draft.getId(),0L,"bad");
		reply.set(valid().replace("\"7\"","\"9\"")); service.process(job.getId());
		assertEquals("BUSINESS_RULE_REJECTED",jobs.get("actor",job.getId()).getErrorCode()); assertNull(jobs.get("actor",job.getId()).getNextRetryAt());
		assertEquals(0L,count("promotion_activity")); assertEquals(0L,drafts.get("actor",draft.getId()).getRevision());
		jobs.retry("actor",job.getId()); reply.set(valid());
		duringCall.set(() -> { var change=definition();change.setMaxDiscountPercent(new BigDecimal("1"));change.setExpectedRevision(0L);drafts.save("actor",draft.getId(),change); });
		service.process(job.getId()); assertEquals("STALE",jobs.get("actor",job.getId()).getStatus());
	}

	@Test void priceChangedWhileModelWasRunningIsValidatedAgainstFreshFacts() {
		var draft=drafts.save("actor",null,definition()); var job=service.enqueue("actor",draft.getId(),0L,"fresh-price");
		reply.set("{\"activityType\":\"4\",\"name\":\"阶梯价\",\"rules\":{\"skuLadders\":[{\"skuId\":\"sku\",\"ladders\":[{\"minQty\":1,\"unitPrice\":90}]}]},\"reason\":\"旧售价提议\"}");
		duringCall.set(() -> livePrice.set(new BigDecimal("80"))); service.process(job.getId());
		assertEquals("BUSINESS_RULE_REJECTED",jobs.get("actor",job.getId()).getErrorCode());assertEquals(0L,count("promotion_activity"));
	}

	@Test void packageSimulationUsesOwnedRevisionAndUnissuedCouponAndEditingInvalidatesIt() {
		var def=definition();
		var coupon=new CampaignCouponDraftDTO();coupon.setName("定向券草案");coupon.setAmount(BigDecimal.TEN);coupon.setThreshold(new BigDecimal("100"));coupon.setQuantity(10);def.setCoupon(coupon);
		var main=new B2bActivitySaveDTO();main.setActivityName("采购满减");main.setActivityType("7");main.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]}");def.setActivity(main);
		var draft=drafts.save("actor",null,def);
		assertEquals(0,new BigDecimal("900").compareTo(commands.get(draft.getMainActivityId()).getTotalBudget()));
		when(order.compare(any(),any(),any())).thenAnswer(call -> {
			OrderScenarioFactsVO facts=call.getArgument(0);assertNull(facts.getCoupon());assertNotNull(facts.getProposedCoupon());
			assertTrue(facts.getProposedCoupon().getId().startsWith("draft:"));
			var comparison=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO();
			var quote=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Quote();quote.setGoodsPayable(new BigDecimal("80"));
			var line=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Line();line.setSkuId("sku");line.setGoodsPayable(new BigDecimal("80"));quote.setLines(List.of(line));
			comparison.setBaseline(quote);comparison.setProposed(quote);return comparison;
		});
		var engine=new PromotionEngineServiceImpl(session.getMapper(PromotionActivityMapper.class),mock(PricePolicyMapper.class),mock(PricePolicyItemMapper.class),mock(PromoCalendarMapper.class),new PromotionLockUsage(mock(PromotionLockMapper.class)));
		var simulation=new CampaignSimulationService(session.getMapper(CampaignSimulationMapper.class),commands,governance,engine,json,draftStore,mock(PricePolicyService.class));
		ReflectionTestUtils.setField(simulation,"remoteOrderScenarioService",order);
		var request=new CampaignSimulationDTO();request.setCampaignDraftId(draft.getId());request.setExpectedCampaignRevision(0L);
		var sample=new PromotionContextDTO();sample.setTenantId(tenant);sample.setUserId("buyer");sample.setPurchaseScene("1");sample.setActivityTime(LocalDateTime.of(2031,2,1,0,0));
		var row=new PromotionContextDTO.SkuItem();row.setSkuId("sku");row.setQuantity(1);row.setSalesPrice(new BigDecimal("100"));sample.setSkuItems(List.of(row));request.setSample(sample);
		assertThrows(RuntimeException.class,()->simulation.simulate("other",request));
		sample.setUserId("other-buyer");assertThrows(RuntimeException.class,()->simulation.simulate("actor",request));sample.setUserId("buyer");
		var result=simulation.simulate("actor",request);assertTrue(simulation.get("actor",result.getId(),true).isCurrent());
		assertEquals(result.getId(),drafts.get("actor",draft.getId()).getSimulationId());
		var edit=drafts.get("actor",draft.getId()).getDefinition();edit.setTitle("只改包标题也失效");drafts.save("actor",draft.getId(),edit);
		assertFalse(simulation.get("actor",result.getId(),false).isCurrent());assertNull(drafts.get("actor",draft.getId()).getSimulationId());
	}

	@Test void explicitPolicyCommandCreatesIsolatedDraftAndSimulationUsesFormalPolicyCore() {
		var def=definition();def.setPurchaseScene("3");def.setBuyerOrgId("org");var campaign=drafts.save("actor",null,def);
		var command=new CampaignPolicyCommandDTO();command.setExpectedRevision(0L);command.setName("核定企业协议");
		var item=new CampaignPolicyCommandDTO.Item();item.setSkuId("sku");item.setPrice(new BigDecimal("90"));item.setMinQty(1);command.setItems(List.of(item));
		var saved=policyDrafts.save("actor",campaign.getId(),command);
		assertEquals("READY",saved.getStatus());assertEquals(1L,saved.getRevision());assertNull(saved.getMainActivityId());assertNotNull(saved.getMainPolicyId());
		assertEquals("0",policyService.get(tenant,saved.getMainPolicyId()).getStatus());assertEquals(0L,count("promotion_activity"));
		assertThrows(RuntimeException.class,()->service.enqueue("actor",saved.getId(),1L,"model-policy"));
		when(order.compare(any(),any(),any())).thenAnswer(call->{
			var result=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO();
			var quote=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Quote();quote.setGoodsPayable(new BigDecimal("90"));
			var line=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Line();line.setSkuId("sku");line.setGoodsPayable(new BigDecimal("90"));quote.setLines(List.of(line));result.setBaseline(quote);result.setProposed(quote);return result;
		});
		var engine=new PromotionEngineServiceImpl(session.getMapper(PromotionActivityMapper.class),session.getMapper(PricePolicyMapper.class),session.getMapper(PricePolicyItemMapper.class),mock(PromoCalendarMapper.class),new PromotionLockUsage(mock(PromotionLockMapper.class)));
		var simulator=new CampaignSimulationService(session.getMapper(CampaignSimulationMapper.class),commands,governance,engine,json,draftStore,policyService);ReflectionTestUtils.setField(simulator,"remoteOrderScenarioService",order);
		var request=new CampaignSimulationDTO();request.setCampaignDraftId(saved.getId());request.setExpectedCampaignRevision(1L);
		var sample=new PromotionContextDTO();sample.setTenantId(tenant);sample.setPurchaseScene("3");sample.setBuyerOrgId("org");sample.setUserId("buyer");sample.setActivityTime(LocalDateTime.of(2031,2,1,0,0));
		var row=new PromotionContextDTO.SkuItem();row.setSkuId("sku");row.setQuantity(1);row.setSalesPrice(new BigDecimal("100"));sample.setSkuItems(List.of(row));request.setSample(sample);
		var result=simulator.simulate("actor",request);assertEquals(0,new BigDecimal("10").compareTo(result.getProposed().getTotalDiscount()));assertEquals(0,result.getBaseline().getTotalDiscount().signum());
		assertTrue(result.getRisks().stream().anyMatch(r->"POLICY_BUDGET_NOT_ENFORCED".equals(r.getCode())));assertTrue(simulator.get("actor",result.getId(),true).isCurrent());
		jdbc.update("UPDATE price_policy_item SET policy_price=85 WHERE policy_id=? AND tenant_id=?",saved.getMainPolicyId(),tenant);
		assertFalse(simulator.get("actor",result.getId(),false).isCurrent());
	}

	@Test void explicitPolicyRejectsPersonalSubjectOutsideSkuAndExcessiveDiscount() {
		var personal=drafts.save("actor",null,definition());var command=new CampaignPolicyCommandDTO();command.setExpectedRevision(0L);command.setName("policy");
		var item=new CampaignPolicyCommandDTO.Item();item.setSkuId("sku");item.setPrice(new BigDecimal("90"));item.setMinQty(1);command.setItems(List.of(item));
		assertThrows(RuntimeException.class,()->policyDrafts.save("actor",personal.getId(),command));
		var def=definition();def.setPurchaseScene("3");def.setBuyerOrgId("org");var draft=drafts.save("actor",null,def);
		item.setSkuId("outside");assertThrows(RuntimeException.class,()->policyDrafts.save("actor",draft.getId(),command));item.setSkuId("sku");item.setPrice(BigDecimal.ONE);
		assertThrows(RuntimeException.class,()->policyDrafts.save("actor",draft.getId(),command));assertEquals(0L,count("price_policy"));
	}

	@Test void generatedPageUsesOriginalEditorRevisionAndManualEditRequiresNewReview() {
		var def=definition();var main=new B2bActivitySaveDTO();main.setActivityName("采购满减");main.setActivityType("7");main.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]}");def.setActivity(main);
		var draft=drafts.save("actor",null,def);var command=new CampaignPageCommandDTO();command.setExpectedRevision(0L);command.setSimulationId("simulation");
		var created=campaignPages.generate("actor",draft.getId(),command);assertNotNull(created.getPageId());assertEquals(1L,created.getPageRevision());
		assertEquals("0",pages.getById(created.getPageId()).getPublishedStatus());assertEquals(created.getId(),pages.getById(created.getPageId()).getCampaignDraftId());
		var simulation=new com.aryn.cloud.promotion.api.vo.CampaignSimulationVO();simulation.setCampaignDraftId(draft.getId());simulation.setExpectedCampaignRevision(0L);simulation.setCurrent(true);when(simulationFixture.requireCurrent("actor","simulation")).thenReturn(simulation);
		assertTrue(campaignPages.preview("actor",draft.getId(),command).isCurrent());
		assertTrue(campaignPages.preview("actor",draft.getId(),command).getGoods().isEmpty(),"未报价商品不能回落普通价格");
		var comparison=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO();
		var proposed=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Quote();
		var line=new com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Line();line.setSkuId("sku");line.setUnitPrice(new BigDecimal("87.50"));
		proposed.setLines(List.of(line));comparison.setProposed(proposed);simulation.setOrderComparison(comparison);
		var quoted=campaignPages.preview("actor",draft.getId(),command).getGoods();assertEquals(1,quoted.size());assertEquals("spu",quoted.get(0).getId());assertEquals(new BigDecimal("87.50"),quoted.get(0).getPrice());
		var editor=pages.getEditor(created.getPageId());var save=new PageDesignDraftDTO();save.setId(editor.getId());save.setPageName("人工装修");save.setSchemaVersion(3);save.setDraftRevision(editor.getDraftRevision());
		editor.getPageContent().getJSONArray("sections").getJSONObject(0).getJSONArray("components").getJSONObject(2).getJSONObject("props").put("title","人工改后的选品标题");save.setPageContent(editor.getPageContent());pages.saveDraft(save);
		assertFalse(campaignPages.preview("actor",draft.getId(),command).isCurrent());command.setExpectedPageRevision(2L);assertTrue(campaignPages.review("actor",draft.getId(),command).isCurrent());
		assertEquals(2L,drafts.get("actor",draft.getId()).getPageRevision());assertThrows(RuntimeException.class,()->campaignPages.preview("other",draft.getId(),command));
		command.setExpectedPageRevision(1L);assertThrows(RuntimeException.class,()->campaignPages.generate("actor",draft.getId(),command));assertEquals(2L,pages.getEditor(editor.getId()).getDraftRevision());
		assertEquals(1L,count("page_design"));
	}

 @Test void publicationFailureAtEveryLocalPhaseRollsBackAndRestoredServiceResumesSameEnvelope() {
  for(var phase:List.of("DRAFT_VALIDATED","RESOURCES_PREPARED","AFTER_PAGE")){
   var command=publicationFixture(true);var draftId=drafts.list("actor").get(0).getId();var draft=drafts.get("actor",draftId);
   publishFailure.set(phase);assertThrows(RuntimeException.class,()->publisher.publish("actor",draftId,command));
   var failed=publications.list("actor",draftId).get(0);assertEquals("FAILED_RECOVERABLE",failed.getStatus());
   assertEquals(PromotionActivity.STATUS_DRAFT,commands.get(draft.getMainActivityId()).getStatus());
   assertEquals(0L,count("coupon_info"));assertEquals(0L,count("page_design_version"));assertEquals("0",pages.getById(draft.getPageId()).getPublishedStatus());
   publishFailure.set(null);var restored=new CampaignPublishService(publications,campaignPages,drafts,validator);
   var complete=restored.retry("actor",failed.getId());assertEquals("PUBLISHED",complete.getStatus());
   assertEquals(complete.getId(),restored.publish("actor",draftId,command).getId());assertEquals(1,publications.list("actor",draftId).size());
   assertEquals("PUBLISHED",drafts.get("actor",draftId).getStatus());assertEquals(PromotionActivity.STATUS_PUBLISHED,commands.get(draft.getMainActivityId()).getStatus());
   assertEquals(1L,count("coupon_info"));assertEquals(1L,count("page_design_version"));assertEquals("1",pages.getById(draft.getPageId()).getPublishedStatus());
   assertEquals("1",session.getMapper(CouponInfoMapper.class).selectById(complete.getCouponId()).getStatus(),"只准备停用模板，不发券");
   assertThrows(RuntimeException.class,()->restored.retry("other",failed.getId()));
   command.setAcceptOverlap(!command.isAcceptOverlap());assertThrows(RuntimeException.class,()->restored.publish("actor",draftId,command));
   // 每轮独立租户，保留所有已发布资源，不删除测试记录。
   tenant="pub-"+UUID.randomUUID().toString().substring(0,16);ArynTenantContextHolder.setTenantId(tenant);
  }
 }
 @Test void staleSimulationPageAndUnacceptedCostRiskCannotPublish() {
  var command=publicationFixture(false);var id=drafts.list("actor").get(0).getId();
  var simulation=simulationFixture.requireCurrent("actor","publication-simulation");simulation.setRisks(List.of(new com.aryn.cloud.promotion.api.vo.CampaignSimulationVO.Risk("COST_UNKNOWN","sku","未知成本")));
  assertThrows(RuntimeException.class,()->publisher.publish("actor",id,command));assertEquals(0L,count("page_design_version"));
  command.setRequestId("accepted");command.setAcceptedRiskCodes(Set.of("COST_UNKNOWN"));
  simulation.setFactDigest("changed");assertThrows(RuntimeException.class,()->publisher.publish("actor",id,command));assertEquals(0L,count("page_design_version"));
  simulation.setFactDigest("facts");command.setRequestId("page-changed");var page=pages.getEditor(drafts.get("actor",id).getPageId());
  var edit=new PageDesignDraftDTO();edit.setId(page.getId());edit.setDraftRevision(page.getDraftRevision());edit.setSchemaVersion(3);edit.setPageName("独立修改");edit.setPageContent(page.getPageContent());pages.saveDraft(edit);
  assertThrows(RuntimeException.class,()->publisher.publish("actor",id,command));assertEquals(0L,count("page_design_version"));
 }

 @Test void explicitPolicyPublicationRequiresBudgetRiskAndCannotOverwriteIndependentEdit() {
  var command=publicationFixture(false);var id=drafts.list("actor").get(0).getId();var def=definition();def.setPurchaseScene("3");def.setBuyerOrgId("org");def.setExpectedRevision(0L);drafts.save("actor",id,def);
  var policyCommand=new CampaignPolicyCommandDTO();policyCommand.setName("企业采购协议");policyCommand.setExpectedRevision(1L);var item=new CampaignPolicyCommandDTO.Item();item.setSkuId("sku");item.setPrice(new BigDecimal("90"));item.setMinQty(1);policyCommand.setItems(List.of(item));var saved=policyDrafts.save("actor",id,policyCommand);
  var pageCommand=new CampaignPageCommandDTO();pageCommand.setExpectedRevision(2L);pageCommand.setExpectedPageRevision(saved.getPageRevision());saved=campaignPages.generate("actor",id,pageCommand);
  var simulation=simulationFixture.requireCurrent("actor","publication-simulation");simulation.setExpectedCampaignRevision(2L);simulation.setRisks(List.of(new com.aryn.cloud.promotion.api.vo.CampaignSimulationVO.Risk("POLICY_BUDGET_NOT_ENFORCED",null,"协议无累计预算")));
  draftStore.bindSimulation("actor",id,2L,simulation.getId());command.setExpectedRevision(2L);command.setExpectedPageRevision(saved.getPageRevision());command.setPageDigest(saved.getPageDigest());
  assertThrows(RuntimeException.class,()->publisher.publish("actor",id,command));assertEquals(PricePolicy.STATUS_DRAFT,policyService.get(tenant,saved.getMainPolicyId()).getStatus());
  command.setRequestId("policy-confirmed");command.setAcceptedRiskCodes(Set.of("POLICY_BUDGET_NOT_ENFORCED"));
  var published=publisher.publish("actor",id,command);assertNotNull(published.getMainPolicyId());assertEquals(PricePolicy.STATUS_ACTIVE,policyService.get(tenant,published.getMainPolicyId()).getStatus());assertEquals(0L,count("coupon_info"));
 }
 @Test void concurrentSameRequestPublishesOneVersionAndChangedResourceIsPreserved() throws Exception {
  var command=publicationFixture(false);var id=drafts.list("actor").get(0).getId();var currentTenant=tenant;
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{
   var tasks=new ArrayList<Future<com.aryn.cloud.promotion.api.vo.CampaignPublishVO>>();
   for(int i=0;i<2;i++){tasks.add(pool.submit(()->{ArynTenantContextHolder.setTenantId(currentTenant);try{start.await();return publisher.publish("actor",id,command);}finally{ArynTenantContextHolder.removeTenantId();}}));}
   start.countDown();var first=tasks.get(0).get();var second=tasks.get(1).get();assertEquals(first.getId(),second.getId());assertEquals(1L,count("page_design_version"));
  }finally{pool.shutdownNow();}
 }
 private CampaignPublishCommandDTO publicationFixture(boolean coupon){
  var def=definition();var main=new B2bActivitySaveDTO();main.setActivityName("采购满减");main.setActivityType("7");main.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]}");def.setActivity(main);
  if(coupon){var rule=new CampaignCouponDraftDTO();rule.setName("定向采购券");rule.setAmount(new BigDecimal("5"));rule.setThreshold(new BigDecimal("100"));rule.setQuantity(2);def.setCoupon(rule);}
  var draft=drafts.save("actor",null,def);var pageCommand=new CampaignPageCommandDTO();pageCommand.setExpectedRevision(0L);var created=campaignPages.generate("actor",draft.getId(),pageCommand);
  var simulation=new com.aryn.cloud.promotion.api.vo.CampaignSimulationVO();simulation.setId("publication-simulation");simulation.setCampaignDraftId(draft.getId());simulation.setExpectedCampaignRevision(0L);simulation.setCurrent(true);simulation.setExpiresAt(LocalDateTime.now().plusMinutes(10));simulation.setInputDigest("input");simulation.setFactDigest("facts");simulation.setRisks(List.of());when(simulationFixture.requireCurrent("actor","publication-simulation")).thenReturn(simulation);
  draftStore.bindSimulation("actor",draft.getId(),0L,simulation.getId());
  var command=new CampaignPublishCommandDTO();command.setRequestId("publish-request");command.setExpectedRevision(0L);command.setExpectedPageRevision(created.getPageRevision());command.setPageDigest(created.getPageDigest());command.setSimulationId(simulation.getId());command.setInputDigest("input");command.setFactDigest("facts");command.setAcceptOverlap(true);return command;
 }

	@Test void ownerTenantSoftDeletionAndRevisionBoundariesAreEnforcedBySql() {
		var draft=drafts.save("actor",null,definition());var job=service.enqueue("actor",draft.getId(),0L,"scope");
		assertThrows(RuntimeException.class,()->drafts.get("other",draft.getId()));assertThrows(RuntimeException.class,()->jobs.cancel("other",job.getId()));
		ArynTenantContextHolder.setTenantId("another");assertThrows(RuntimeException.class,()->jobs.get("actor",job.getId()));assertThrows(RuntimeException.class,()->drafts.get("actor",draft.getId()));ArynTenantContextHolder.setTenantId(tenant);
		var stale=definition();stale.setExpectedRevision(99L);assertThrows(RuntimeException.class,()->drafts.save("actor",draft.getId(),stale));
		jdbc.update("UPDATE campaign_draft SET del_flag='1' WHERE tenant_id=? AND id=?",tenant,draft.getId());assertThrows(RuntimeException.class,()->drafts.get("actor",draft.getId()));
	}

	@Test void failedTerminalWriteRollsBackActivityAndDraftTogether() {
		var draft=drafts.save("actor",null,definition()); var job=service.enqueue("actor",draft.getId(),0L,"rollback");String token=jobs.claim(job.getId());
		var failing=mock(CampaignGenerationJobMapper.class,invocation -> {
			if(invocation.getMethod().getName().equals("update")) {throw new IllegalStateException("injected terminal persistence failure");}
			try{return invocation.getMethod().invoke(jobMapper,invocation.getArguments());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
		});
		var broken=tx(new CampaignGenerationStore(failing,draftStore,json));
		assertThrows(RuntimeException.class,()->broken.complete(job.getId(),token,proposal(),"{}"));
		assertEquals(0L,count("promotion_activity")); assertEquals(0L,drafts.get("actor",draft.getId()).getRevision()); assertEquals("RUNNING",jobs.get("actor",job.getId()).getStatus());
	}

	private long count(String table) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Long.class,tenant);}
	@SuppressWarnings("unchecked") private <T> T tx(T target) {
		var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();
	}
	private CampaignGenerationParser.Proposal proposal() {try{return parser.parse(validator.prepare(definition()),json.readTree(valid()));}catch(Exception e){throw new RuntimeException(e);}}
	private CampaignDraftSaveDTO definition() {
		var def=new CampaignDraftSaveDTO();def.setIntent("合法采购满减");def.setTitle("隔离测试活动包");def.setPurchaseScene("1");def.setSampleUserId("buyer");def.setSkuIds(List.of("sku"));
		def.setStartTime(LocalDateTime.of(2031,1,1,0,0));def.setEndTime(LocalDateTime.of(2031,3,1,0,0));def.setBudget(new BigDecimal("1000"));def.setMaxDiscountPercent(new BigDecimal("20"));return def;
	}
	private String valid() {return "{\"activityType\":\"7\",\"name\":\"采购满减\",\"rules\":{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]},\"reason\":\"依据服务端预算\"}";}
}
