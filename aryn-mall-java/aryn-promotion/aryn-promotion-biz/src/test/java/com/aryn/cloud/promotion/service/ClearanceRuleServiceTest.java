package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.entity.ClearanceRule;
import com.aryn.cloud.promotion.mapper.ClearanceRuleMapper;
import com.aryn.cloud.promotion.service.impl.ClearanceRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * 出清时间分层：命中与取最深一档的口径。
 */
class ClearanceRuleServiceTest {

	private final ClearanceRuleService service = new ClearanceRuleService(mock(ClearanceRuleMapper.class));

	@Test
	@DisplayName("距到期越近命中越深折扣，且只取最深一档不连乘")
	void resolvesDeepestTier() {
		List<ClearanceRule> rules = List.of(rule(48, "0.8000"), rule(24, "0.6000"));
		LocalDateTime now = LocalDateTime.now();

		assertEquals(0, new BigDecimal("0.6")
			.compareTo(service.resolveTierRate(rules, now.plusHours(12), now)));
		assertEquals(0, new BigDecimal("0.8")
			.compareTo(service.resolveTierRate(rules, now.plusHours(30), now)));
		assertNull(service.resolveTierRate(rules, now.plusHours(72), now));
	}

	@Test
	@DisplayName("无法命中或已到期/停用规则不生效")
	void ignoresUnmatchedAndDisabledRules() {
		LocalDateTime now = LocalDateTime.now();
		ClearanceRule disabled = rule(48, "0.5000");
		disabled.setEnabled("0");

		assertNull(service.resolveTierRate(List.of(disabled), now.plusHours(12), now));
		assertNull(service.resolveTierRate(List.of(rule(24, "0.6000")), null, now));
		assertNull(service.resolveTierRate(List.of(rule(24, "0.6000")), now.minusHours(1), now));
	}

	private ClearanceRule rule(int hours, String rate) {
		ClearanceRule rule = new ClearanceRule();
		rule.setRuleName("规则" + hours);
		rule.setHoursBeforeExpire(hours);
		rule.setDiscountRate(new BigDecimal(rate));
		rule.setEnabled("1");
		return rule;
	}

}
