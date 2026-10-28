package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultPageDesignDocumentValidatorTest {

	/** 活动 Tab 数据源校验用的活动表 Mapper（其余校验不触库）。 */
	private final PromotionActivityMapper activityMapper = mock(PromotionActivityMapper.class);

	private final DefaultPageDesignDocumentValidator validator = new DefaultPageDesignDocumentValidator(activityMapper);

	@Test
	void acceptsEmptyV2Document() {
		PageDesignValidationVO result = validator.validateStructured("{\"schemaVersion\":2,\"components\":[]}");

		assertFalse(result.hasErrors());
		assertEquals(0, result.getPerformance().getComponentCount());
	}

	@Test
	void acceptsV3DocumentWithSections() {
		String content = """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"section-1","type":"default","components":[
				    {"id":"c1","type":"gap","version":1,"props":{"height":10}}]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors());
		assertEquals(1, result.getPerformance().getComponentCount());
	}

	@Test
	void rejectsUnknownSchemaVersion() {
		PageDesignValidationVO result = validator.validateStructured("{\"schemaVersion\":1,\"components\":[]}");

		assertTrue(result.hasErrors());
		assertEquals("SCHEMA_VERSION", result.getErrors().get(0).getCode());
		assertTrue(result.getErrors().get(0).getMessage().contains("schemaVersion"));
	}

	@Test
	void rejectsInvalidJson() {
		PageDesignValidationVO result = validator.validateStructured("not-json");

		assertEquals("CONTENT_INVALID", result.getErrors().get(0).getCode());
	}

	@Test
	void rejectsUnknownComponentType() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"flash-sale-pro","version":1,"props":{}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertEquals("COMPONENT_UNKNOWN", result.getErrors().get(0).getCode());
		assertEquals("c1", result.getErrors().get(0).getComponentId());
	}

	@Test
	void rejectsComponentMissingIdOrProps() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"type":"gap","version":1,"props":{}},
				  {"id":"c2","type":"gap","version":1}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertEquals(2, result.getErrors().size());
		assertEquals("COMPONENT_INVALID", result.getErrors().get(0).getCode());
		assertEquals("PROPS_INVALID", result.getErrors().get(1).getCode());
	}

	@Test
	void rejectsGoodsLinkWithoutTargetAndCollectsValidTargets() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"image-ad","version":1,"props":{"link":{"type":"goods","path":"","params":{},"targetId":""}}},
				  {"id":"c2","type":"marketing-entry","version":1,"props":{"entries":[
				    {"id":"e1","title":"券","iconUrl":"","link":{"type":"coupon","path":"","params":{},"targetId":"coupon-1"}}]}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertEquals("LINK_INVALID", result.getErrors().get(0).getCode());
		assertEquals("components[0].props.link.targetId", result.getErrors().get(0).getField());
		assertTrue(result.getReferences().getCouponIds().contains("coupon-1"));
	}

	@Test
	void rejectsManualEmptyDataSourceOnDataDrivenComponent() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"goods-group","version":1,"props":{"dataSource":{"mode":"manual","targetIds":[]}}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_EMPTY".equals(issue.getCode()) && "c1".equals(issue.getComponentId())));
	}

	@Test
	void collectsManualGoodsTargetsIntoReferences() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"goods-group","version":1,"props":{"dataSource":{"mode":"manual","targetIds":["spu-1","spu-2"]}}},
				  {"id":"c2","type":"limited-activity","version":1,"props":{"dataSource":{"mode":"manual","targetIds":["act-1"]}}},
				  {"id":"c3","type":"goods-ranking","version":1,"props":{"dataSource":{"mode":"ranking","metric":"sales"}}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors());
		assertEquals(List.of("spu-1", "spu-2"), List.copyOf(result.getReferences().getGoodsIds()));
		assertEquals(List.of("act-1"), List.copyOf(result.getReferences().getActivityIds()));
		assertEquals(1, result.getPerformance().getRequestCount());
	}

	@Test
	void rejectsEmptyManualDataSourceInsideGoodsTabs() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"goods-tabs","version":1,"props":{"tabs":[
				    {"id":"t1","title":"会员专区","dataSource":{"mode":"manual","targetIds":[]}}]}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_EMPTY".equals(issue.getCode())
					&& "props.tabs[0].dataSource.targetIds".equals(issue.getField())));
	}

	@Test
	void collectsGoodsTabsManualTargetsAndCountsAutomaticRequests() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"goods-tabs","version":1,"props":{"tabs":[
				    {"id":"t1","title":"猜你喜欢","dataSource":{"mode":"automatic","sort":"sales"}},
				    {"id":"t2","title":"会员专区","dataSource":{"mode":"manual","targetIds":["spu-1","spu-2"]}}]}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors());
		assertEquals(List.of("spu-1", "spu-2"), List.copyOf(result.getReferences().getGoodsIds()));
		assertEquals(1, result.getPerformance().getRequestCount());
	}

	@Test
	void countsImagesAcrossNestedProps() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"swiper-banner","version":1,"props":{"items":[
				    {"id":"i1","picUrl":"https://cdn.example.com/a.png"},
				    {"id":"i2","picUrl":"https://cdn.example.com/b.webp"}]}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertEquals(2, result.getPerformance().getImageCount());
		assertFalse(result.hasErrors());
	}

	@Test
	void warnsWhenComponentBudgetExceeded() {
		String components = java.util.stream.IntStream.rangeClosed(1, 51)
			.mapToObj(index -> "{\"id\":\"c" + index + "\",\"type\":\"gap\",\"version\":1,\"props\":{}}")
			.collect(Collectors.joining(","));
		String content = "{\"schemaVersion\":2,\"components\":[" + components + "]}";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors());
		assertTrue(result.getWarnings().stream().anyMatch(issue -> "BUDGET_COMPONENTS".equals(issue.getCode())));
		assertEquals(51, result.getPerformance().getComponentCount());
	}

	@Test
	void rejectsV3SectionMissingId() {
		String content = """
				{"schemaVersion":3,"page":{},"sections":[{"components":[]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "SECTION_INVALID".equals(issue.getCode())));
	}

	@Test
	void rejectsSectionConditionWithEmptyMemberLevelIds() {
		// 线上事故回归：首页唯一区块挂着 memberLevelIds: []，发布后 C 端整页只剩导航条白屏
		String content = """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"section-1","type":"default","style":{
				    "condition":{"logic":"and","rules":[{"type":"memberLevel","memberLevelIds":[]}]}},
				  "components":[{"id":"c1","type":"gap","version":1,"props":{"height":10}}]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream()
			.anyMatch(issue -> "CONDITION_RULE_EMPTY".equals(issue.getCode())), "空会员等级规则应当阻断发布");
		assertTrue(result.getErrors().stream()
			.filter(issue -> "CONDITION_RULE_EMPTY".equals(issue.getCode()))
			.allMatch(issue -> issue.getMessage().contains("会员等级")));
	}

	@Test
	void rejectsSectionConditionWithEmptyUserTagIds() {
		String content = """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"section-1","type":"default","style":{
				    "condition":{"logic":"or","rules":[{"type":"userTag","userTagIds":[]}]}},
				  "components":[{"id":"c1","type":"gap","version":1,"props":{"height":10}}]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream()
			.filter(issue -> "CONDITION_RULE_EMPTY".equals(issue.getCode()))
			.allMatch(issue -> issue.getMessage().contains("用户标签")));
	}

	@Test
	void acceptsConfiguredSectionConditions() {
		// 配置完整的组合条件、字符串简写条件与空 rules 数组都不拦截（空组合由 C 端 fail-open）
		String content = """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"section-1","type":"default","style":{
				    "condition":{"logic":"and","rules":[
				      {"type":"memberLevel","memberLevelIds":["lv1"]},
				      {"type":"userTag","userTagIds":["tag-1"]},
				      {"type":"timeRange","startTime":"09:00","endTime":"21:00"}]}},
				  "components":[{"id":"c1","type":"gap","version":1,"props":{"height":10}}]},
				  {"id":"section-2","type":"default","style":{"condition":"login"},"components":[]},
				  {"id":"section-3","type":"default","style":{"condition":{"logic":"or","rules":[]}},"components":[]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors());
	}

	@Test
	void rejectsDuplicatedSingletonComponentWithItsOwnLabel() {
		// 新增第二个单例组件（补给单卡片）后，报错文案必须点出是哪个组件重复，
		// 否则运营配了两个补给单也只会看到「船舶工作台」而找不到问题。
		String content = """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"section-1","type":"default","components":[
				    {"id":"c1","type":"replenish-card","version":1,"props":{}},
				    {"id":"c2","type":"replenish-card","version":1,"props":{}}]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		boolean duplicated = result.getErrors().stream()
				.anyMatch(issue -> "COMPONENT_DUPLICATED".equals(issue.getCode()));
		assertTrue(duplicated, "同一页面配两个补给单卡片应当被阻断");
		assertTrue(result.getErrors().stream()
				.filter(issue -> "COMPONENT_DUPLICATED".equals(issue.getCode()))
				.allMatch(issue -> issue.getMessage().contains("补给单")));
	}

	@Test
	void keepsShipWorkbenchLabelForItsOwnDuplicate() {
		// 回归：船舶工作台重复时的文案不能被新增单例组件的映射带偏
		String content = """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"section-1","type":"default","components":[
				    {"id":"c1","type":"ship-workbench","version":1,"props":{}},
				    {"id":"c2","type":"ship-workbench","version":1,"props":{}}]}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream()
				.filter(issue -> "COMPONENT_DUPLICATED".equals(issue.getCode()))
				.allMatch(issue -> issue.getMessage().contains("船舶工作台")));
	}

	@Test
	void validateReturnsMessageListForPublishFlow() {
		List<String> errors = validator.validate("{\"schemaVersion\":2}");

		assertEquals(1, errors.size());
		assertTrue(errors.get(0).contains("components"));
	}

	@Test
	void acceptsCustomHtmlWithSafeTags() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<div class='promo'><p style='color:red'>全场满减</p><a href='https://example.com/a'>去看看</a><img src='https://cdn.example.com/b.png' alt='banner'/></div>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors(), "合法展示型HTML不应被阻断: " + result.getErrors());
	}

	@Test
	void rejectsCustomHtmlContainingScriptTag() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<div><script>alert(1)</script></div>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())));
		assertTrue(result.getErrors().get(0).getMessage().contains("script"));
	}

	@Test
	void rejectsCustomHtmlContainingJavascriptProtocolLink() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<a href='javascript:alert(document.cookie)'>点我</a>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())));
	}

	@Test
	void rejectsCustomHtmlContainingEventHandlerAttribute() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<img src='https://cdn.example.com/b.png' onerror='alert(1)'/>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())));
	}

	@Test
	void allowsBlankCustomHtmlWithoutContent() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertFalse(result.hasErrors());
	}

	@Test
	void rejectsCustomHtmlWithEntityEncodedScriptTag() {
		// 浏览器会把 &lt; 还原成 <，纯子串匹配挡不住
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"&lt;script&gt;alert(1)&lt;/script&gt;"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())),
				"实体编码的 script 标签必须被阻断: " + result.getErrors());
	}

	@Test
	void rejectsCustomHtmlWithNumericEntityEncodedJavascriptProtocol() {
		// &#106; 即 'j'，浏览器解析 href 时会还原
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<a href='&#106;avascript:alert(1)'>点我</a>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())),
				"数字实体编码的 javascript: 必须被阻断: " + result.getErrors());
	}

	@Test
	void rejectsCustomHtmlWithTemplateTag() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<template><img src='x' onerror='alert(1)'></template>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())));
	}

	@Test
	void rejectsCustomHtmlWithWhitespaceObfuscatedEventHandler() {
		// 事件属性名里插换行，浏览器仍解析为 onerror=
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"custom-html","version":1,"props":{"html":"<img src='x' on\nerror='alert(1)'>"}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content);

		assertTrue(result.getErrors().stream().anyMatch(issue -> "CUSTOM_HTML_UNSAFE".equals(issue.getCode())),
				"空白混淆的事件处理器必须被阻断: " + result.getErrors());
	}

	@Test
	void rejectsComponentNotAllowedOnDetailPage() {
		// bottom-nav 是页面级组件，商品详情页白名单里没有它
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"bottom-nav","version":1,"props":{"items":[]}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content, "2");

		assertTrue(result.getErrors().stream().anyMatch(issue -> "COMPONENT_NOT_ALLOWED".equals(issue.getCode())),
				"商详页放置底部导航必须被阻断: " + result.getErrors());
		assertTrue(result.getErrors().stream()
			.filter(issue -> "COMPONENT_NOT_ALLOWED".equals(issue.getCode()))
			.anyMatch(issue -> issue.getMessage().contains("商品详情页")));
	}

	@Test
	void allowsComponentWhitelistedForDetailPage() {
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"goods","version":1,"props":{}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content, "2");

		assertFalse(result.getErrors().stream().anyMatch(issue -> "COMPONENT_NOT_ALLOWED".equals(issue.getCode())));
	}

	@Test
	void rejectsGoodsComponentOnUserCenterPage() {
		// 个人中心页不展示商品流，商品类组件不在白名单内
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"goods","version":1,"props":{}}]}
				""";

		PageDesignValidationVO result = validator.validateStructured(content, "4");

		assertTrue(result.getErrors().stream().anyMatch(issue -> "COMPONENT_NOT_ALLOWED".equals(issue.getCode())));
	}

	@Test
	void doesNotRestrictMicroOrHomePageComponents() {
		// 微页面(0)与首页(1)是全功能页面，组件不受白名单限制
		String content = """
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"bottom-nav","version":1,"props":{"items":[]}}]}
				""";

		assertFalse(validator.validateStructured(content, "0").getErrors().stream()
			.anyMatch(issue -> "COMPONENT_NOT_ALLOWED".equals(issue.getCode())));
		assertFalse(validator.validateStructured(content, "1").getErrors().stream()
			.anyMatch(issue -> "COMPONENT_NOT_ALLOWED".equals(issue.getCode())));
	}

	// ==== F1：自动 Tab 排序白名单与折扣率区间 ====

	private PageDesignValidationVO validateGoodsTabs(String tabsJson) {
		String content = "{\"schemaVersion\":2,\"components\":[{\"id\":\"c1\",\"type\":\"goods-tabs\",\"version\":1,"
				+ "\"props\":{\"tabs\":[" + tabsJson + "]}}]}";
		return validator.validateStructured(content);
	}

	@Test
	void acceptsAllWhitelistedAutomaticTabSorts() {
		PageDesignValidationVO result = validateGoodsTabs("""
				{"id":"t1","title":"猜你喜欢","dataSource":{"mode":"automatic","sort":"sales"}},
				{"id":"t2","title":"新品","dataSource":{"mode":"automatic","sort":"create_time"}},
				{"id":"t3","title":"价格","dataSource":{"mode":"automatic","sort":"sales_price"}},
				{"id":"t4","title":"捡漏","dataSource":{"mode":"automatic","sort":"bargain","bargainRate":1.15}}""");

		assertFalse(result.hasErrors(), "白名单排序不应报错: " + result.getErrors());
		// 4 个自动 Tab 各计一次请求预算
		assertEquals(4, result.getPerformance().getRequestCount());
	}

	@Test
	void acceptsBargainTabWithoutRateAndWithBoundaryRate() {
		// bargainRate 可空（缺省 1.15）；边界 9.99 合法、1.01 合法
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"捡漏\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"bargain\"}}")
			.hasErrors());
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"捡漏\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"bargain\",\"bargainRate\":9.99}}")
			.hasErrors());
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"捡漏\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"bargain\",\"bargainRate\":1.01}}")
			.hasErrors());
	}

	@Test
	void rejectsUnknownAutomaticTabSort() {
		PageDesignValidationVO result = validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"猜你喜欢\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"price_desc\"}}");

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].dataSource.sort".equals(issue.getField())));
	}

	@Test
	void rejectsBargainRateOutOfRange() {
		for (String rate : List.of("1.00", "0.99", "10", "9.991")) {
			PageDesignValidationVO result = validateGoodsTabs(
					"{\"id\":\"t1\",\"title\":\"捡漏\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"bargain\",\"bargainRate\":"
							+ rate + "}}");
			assertTrue(result.getErrors()
				.stream()
				.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
						&& "props.tabs[0].dataSource.bargainRate".equals(issue.getField())),
					"折扣率 " + rate + " 超出 (1.00, 9.99] 必须报错");
		}
	}

	@Test
	void ignoresBargainRateWhenSortIsNotBargain() {
		// 阈值只在折扣力度排序下生效：其余排序残留的 bargainRate 不参与校验
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"销量\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\",\"bargainRate\":0.5}}")
			.hasErrors());
		// 手选 Tab 不校验排序语义（含非法 sort）
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"手选\",\"dataSource\":{\"mode\":\"manual\",\"sort\":\"anything\",\"targetIds\":[\"spu-1\"]}}")
			.hasErrors());
	}

	// ==== F2：Tab 级会员权益条 ====

	@Test
	void acceptsMemberBannerWithBoundaryCopy() {
		String copy = "会".repeat(30);
		PageDesignValidationVO result = validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"},"
						+ "\"memberBanner\":{\"enabled\":true,\"memberCopy\":\"" + copy + "\",\"guestCopy\":\"" + copy
						+ "\"}}");

		assertFalse(result.hasErrors(), "30 字文案不应报错: " + result.getErrors());
	}

	@Test
	void acceptsMemberBannerWithEmptyOrDisabledCopy() {
		// 开启但文案留空：走 C 端内置模板
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"},"
						+ "\"memberBanner\":{\"enabled\":true}}")
			.hasErrors());
		// 关闭时超长文案不阻断（草稿态允许保留）
		assertFalse(validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"},"
						+ "\"memberBanner\":{\"enabled\":false,\"memberCopy\":\"" + "会".repeat(31) + "\"}}")
			.hasErrors());
	}

	@Test
	void rejectsOverlongMemberBannerCopies() {
		String tooLong = "会".repeat(31);
		PageDesignValidationVO memberResult = validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"},"
						+ "\"memberBanner\":{\"enabled\":true,\"memberCopy\":\"" + tooLong + "\"}}");
		assertTrue(memberResult.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].memberBanner.memberCopy".equals(issue.getField())),
				"会员文案超过 30 字必须报错");

		PageDesignValidationVO guestResult = validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"},"
						+ "\"memberBanner\":{\"enabled\":true,\"guestCopy\":\"" + tooLong + "\"}}");
		assertTrue(guestResult.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].memberBanner.guestCopy".equals(issue.getField())),
				"游客文案超过 30 字必须报错");
	}

	@Test
	void rejectsMalformedMemberBanner() {
		PageDesignValidationVO result = validateGoodsTabs(
				"{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"},"
						+ "\"memberBanner\":\"on\"}");

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].memberBanner".equals(issue.getField())));
	}

	// ==== 会员专区活动数据源（activity 模式） ====

	private PromotionActivity promotionActivity(String id, String type) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId(id);
		activity.setActivityName("活动-" + id);
		activity.setActivityType(type);
		return activity;
	}

	private String activityTab(String extraDataSourceFields) {
		return "{\"id\":\"t1\",\"title\":\"会员专区\",\"dataSource\":{\"mode\":\"activity\""
				+ extraDataSourceFields + "}}";
	}

	@Test
	void acceptsActivityTabWithoutActivityIdAndCountsTwoRequests() {
		// activityId 留空 = 运行时跟随当前进行中的会员价活动，无需 targetIds/sort
		PageDesignValidationVO result = validateGoodsTabs(activityTab(""));

		assertFalse(result.hasErrors(), "无 activityId 的 activity Tab 应放行: " + result.getErrors());
		assertEquals(2, result.getPerformance().getRequestCount(), "活动商品接口 + 商品列表接口 = 2 次请求");
	}

	@Test
	void acceptsActivityTabWithValidMemberPriceActivity() {
		when(activityMapper.selectOne(any()))
			.thenReturn(promotionActivity("act-10", PromotionActivity.TYPE_MEMBER_PRICE));
		// 切换数据源后残留的 sort/categoryId/targetIds 一律忽略：不校验、不进引用清单
		PageDesignValidationVO result = validateGoodsTabs(activityTab(
				",\"activityId\":\"act-10\",\"sort\":\"bogus-sort\",\"categoryId\":\"cat-1\","
						+ "\"targetIds\":[\"spu-stale\"]"));

		assertFalse(result.hasErrors(), "合法会员价活动应放行: " + result.getErrors());
		assertEquals(2, result.getPerformance().getRequestCount());
		assertTrue(result.getReferences().getGoodsIds().isEmpty(), "activity 模式残留 targetIds 不进引用清单");
	}

	@Test
	void rejectsActivityTabWithMissingActivity() {
		when(activityMapper.selectOne(any())).thenReturn(null);
		PageDesignValidationVO result = validateGoodsTabs(activityTab(",\"activityId\":\"act-missing\""));

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].dataSource.activityId".equals(issue.getField())),
				"activityId 指向不存在的活动必须报错");
	}

	@Test
	void rejectsActivityTabWithNonMemberPriceActivity() {
		when(activityMapper.selectOne(any()))
			.thenReturn(promotionActivity("act-4", PromotionActivity.TYPE_LADDER_PRICE));
		PageDesignValidationVO result = validateGoodsTabs(activityTab(",\"activityId\":\"act-4\""));

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].dataSource.activityId".equals(issue.getField())),
				"非会员价类型活动必须报错（否则专区永远空）");
	}

	@Test
	void failsClosedWhenActivityLookupThrows() {
		when(activityMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
		PageDesignValidationVO result = validateGoodsTabs(activityTab(",\"activityId\":\"act-10\""));

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(issue -> "DATASOURCE_INVALID".equals(issue.getCode())
					&& "props.tabs[0].dataSource.activityId".equals(issue.getField())),
				"活动查询异常按校验失败处理（fail-closed），不抛异常");
	}

	@Test
	void countsActivityAndAutomaticTabsRequestBudgetSeparately() {
		when(activityMapper.selectOne(any()))
			.thenReturn(promotionActivity("act-10", PromotionActivity.TYPE_MEMBER_PRICE));
		PageDesignValidationVO result = validateGoodsTabs(activityTab(",\"activityId\":\"act-10\"") + ","
				+ "{\"id\":\"t2\",\"title\":\"猜你喜欢\",\"dataSource\":{\"mode\":\"automatic\",\"sort\":\"sales\"}}");

		assertFalse(result.hasErrors(), "activity + automatic Tab 应放行: " + result.getErrors());
		// activity 模式 2 次 + automatic 1 次
		assertEquals(3, result.getPerformance().getRequestCount());
	}

}
