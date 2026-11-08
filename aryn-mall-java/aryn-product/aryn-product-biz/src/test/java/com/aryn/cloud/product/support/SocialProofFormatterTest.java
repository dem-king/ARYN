package com.aryn.cloud.product.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 社证文案门槛/取整口径钉死。文案为行业中性语法（月售/回头客/回购率），
 * 与盒马系「月N人已下单」句式脱钩；类型（kind）与文案一一对应，前端据此配图标。
 *
 * <p>两条硬规则：向下取整不可夸大（合规），低于门槛宁可不显示（新品/滞销品
 * 绝不暴露难看的小数字）。口径一旦对外展示即用户可感知承诺，改动须走需求评审。
 */
@DisplayName("卡面社证文案格式化")
class SocialProofFormatterTest {

	private static final int MIN_CNT = 1000;

	private static final int MIN_RATE = 30;

	@Test
	@DisplayName("计数取整：999 低于门槛不下发，1000 起步，千位/万位向下取整")
	void formatCountRoundsDown() {
		assertThat(SocialProofFormatter.formatCount(999, MIN_CNT)).isNull();
		assertThat(SocialProofFormatter.formatCount(1000, MIN_CNT)).isEqualTo("1000+");
		assertThat(SocialProofFormatter.formatCount(4321, MIN_CNT)).isEqualTo("4000+");
		assertThat(SocialProofFormatter.formatCount(9999, MIN_CNT)).isEqualTo("9000+");
		assertThat(SocialProofFormatter.formatCount(10000, MIN_CNT)).isEqualTo("1万+");
		assertThat(SocialProofFormatter.formatCount(61234, MIN_CNT)).isEqualTo("6万+");
	}

	@Test
	@DisplayName("空值/负数一律不下发")
	void formatCountNullSafe() {
		assertThat(SocialProofFormatter.formatCount(null, MIN_CNT)).isNull();
		assertThat(SocialProofFormatter.formatCount(0, MIN_CNT)).isNull();
		assertThat(SocialProofFormatter.formatCount(-5, MIN_CNT)).isNull();
	}

	@Test
	@DisplayName("优先级：回购率 → 回头客 → 下单人数，单卡单条，kind 与文案一一对应")
	void priorityRateThenRepeatThenBuyer() {
		// 全达标：取回购率
		SocialProofFormatter.Proof repurchase = SocialProofFormatter.socialProof(
				4000, 1600, new BigDecimal("40.00"), MIN_CNT, MIN_RATE);
		assertThat(repurchase.text()).isEqualTo("40% 的人回购");
		assertThat(repurchase.kind()).isEqualTo(SocialProofFormatter.KIND_REPURCHASE);
		// 回购率不足（29.5 < 30）：回落回头客
		SocialProofFormatter.Proof repeat = SocialProofFormatter.socialProof(
				4000, 4321, new BigDecimal("29.50"), MIN_CNT, MIN_RATE);
		assertThat(repeat.text()).isEqualTo("月 4000+ 位回头客");
		assertThat(repeat.kind()).isEqualTo(SocialProofFormatter.KIND_REPEAT);
		// 回头客也不足：回落下单人数
		SocialProofFormatter.Proof buyer = SocialProofFormatter.socialProof(
				4000, 800, new BigDecimal("20.00"), MIN_CNT, MIN_RATE);
		assertThat(buyer.text()).isEqualTo("月 4000+ 人已下单");
		assertThat(buyer.kind()).isEqualTo(SocialProofFormatter.KIND_BUYER);
	}

	@Test
	@DisplayName("回购率取整向下：34.7% 显示 34%")
	void rateFloorsDown() {
		assertThat(SocialProofFormatter.socialProof(4000, 1600, new BigDecimal("34.70"), MIN_CNT, MIN_RATE).text())
			.isEqualTo("34% 的人回购");
	}

	@Test
	@DisplayName("买家数未达门槛时回购率再高也不下发")
	void rateRequiresMinBuyer() {
		assertThat(SocialProofFormatter.socialProof(500, 400, new BigDecimal("80.00"), MIN_CNT, MIN_RATE))
			.isNull();
	}

	@Test
	@DisplayName("全部未达门槛返回 null")
	void allBelowThresholdReturnsNull() {
		assertThat(SocialProofFormatter.socialProof(999, 20, new BigDecimal("10.00"), MIN_CNT, MIN_RATE))
			.isNull();
		assertThat(SocialProofFormatter.socialProof(null, null, null, MIN_CNT, MIN_RATE)).isNull();
	}

}
