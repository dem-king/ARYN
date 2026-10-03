package com.aryn.cloud.promotion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 主题颜色衍生口径契约。
 * <p>
 * 管理端 schema/theme-presets.ts 与 C 端画布/实机依赖同一衍生口径（混白 35%），
 * 三端任一改动都必须同步，否则「设为默认主题」后画布与实机颜色漂移。
 */
class ThemeColorUtilsTest {

	@Test
	void deriveSecondaryMixesThirtyFivePercentTowardWhite() {
		// G: 34 + 221*0.35 = 111.35 → 111 = 0x6F；B: 55 + 200*0.35 = 125 = 0x7D
		assertEquals("#FF6F7D", ThemeColorUtils.deriveSecondary("#FF2237"));
		assertEquals("#FFFFFF", ThemeColorUtils.deriveSecondary("#FFFFFF"));
	}

	@Test
	void deriveSecondaryReturnsNullForInvalidColor() {
		assertNull(ThemeColorUtils.deriveSecondary(null));
		assertNull(ThemeColorUtils.deriveSecondary(""));
		assertNull(ThemeColorUtils.deriveSecondary("red"));
		assertNull(ThemeColorUtils.deriveSecondary("#12345"));
	}

}
