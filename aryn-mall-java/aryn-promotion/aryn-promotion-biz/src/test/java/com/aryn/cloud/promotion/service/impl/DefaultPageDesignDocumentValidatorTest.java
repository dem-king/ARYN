package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPageDesignDocumentValidatorTest {

	private final DefaultPageDesignDocumentValidator validator = new DefaultPageDesignDocumentValidator();

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
	void validateReturnsMessageListForPublishFlow() {
		List<String> errors = validator.validate("{\"schemaVersion\":2}");

		assertEquals(1, errors.size());
		assertTrue(errors.get(0).contains("components"));
	}

}
