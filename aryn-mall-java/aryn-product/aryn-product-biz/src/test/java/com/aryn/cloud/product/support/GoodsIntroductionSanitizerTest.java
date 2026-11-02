package com.aryn.cloud.product.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsIntroductionSanitizerTest {

	@Test
	void blankStaysNullSoPartialUpdatesKeepStoredIntroduction() {
		assertThat(GoodsIntroductionSanitizer.sanitize(null)).isNull();
		assertThat(GoodsIntroductionSanitizer.sanitize("   ")).isNull();
	}

	@Test
	void keepsParagraphsAndInlineFormatting() {
		String html = "<p>你就和巨峰夏黑玩去吧，<span style=\"color: rgb(255, 0, 0); font-weight: bold\">"
				+ "脆甜提子</span>（哭）</p>";

		String sanitized = GoodsIntroductionSanitizer.sanitize(html);

		assertThat(sanitized).isEqualTo("<p>你就和巨峰夏黑玩去吧，"
				+ "<span style=\"color: rgb(255, 0, 0);font-weight: bold\">脆甜提子</span>（哭）</p>");
	}

	@Test
	void unwrapsTagsOutsideWhitelistButKeepsTheirText() {
		String sanitized = GoodsIntroductionSanitizer.sanitize(
				"<h2>标题</h2><ul><li>第一点</li></ul><a href=\"https://evil.test\">链接</a>");

		assertThat(sanitized).isEqualTo("标题第一点链接");
	}

	@Test
	void dropsMediaAndStyleBlocksEntirely() {
		String sanitized = GoodsIntroductionSanitizer.sanitize(
				"正文<img src=\"https://cdn.test/a.png\"><style>p{color:red}</style>"
						+ "<script>alert(1)</script>结尾");

		assertThat(sanitized).isEqualTo("正文结尾");
	}

	@Test
	void truncatesAtUnclosedDangerousTag() {
		String sanitized = GoodsIntroductionSanitizer.sanitize("正文<script>alert(1)");

		assertThat(sanitized).isEqualTo("正文");
	}

	@Test
	void keepsOnlyLayoutSafeStyleDeclarations() {
		String sanitized = GoodsIntroductionSanitizer.sanitize(
				"<span style=\"background-color: #ff0000; position: fixed; color: #c00; "
						+ "font-size: 36px; line-height: 3\">重色</span>");

		assertThat(sanitized).isEqualTo(
				"<span style=\"color: #c00;font-size: 36px\">重色</span>");
	}

	@Test
	void rejectsScriptishStyleValues() {
		String sanitized = GoodsIntroductionSanitizer.sanitize(
				"<span style=\"color: expression(alert(1)); font-size: url(x)\">文字</span>");

		assertThat(sanitized).isEqualTo("<span>文字</span>");
	}

	@Test
	void emptyRenderingBecomesNull() {
		assertThat(GoodsIntroductionSanitizer.sanitize("<p><br/></p>")).isNull();
		assertThat(GoodsIntroductionSanitizer.sanitize("<!-- 注释 -->")).isNull();
	}

	@Test
	void acceptsPlainTextWithoutMarkup() {
		assertThat(GoodsIntroductionSanitizer.sanitize("冷藏后风味更佳"))
				.isEqualTo("冷藏后风味更佳");
	}

	@Test
	void rejectsIntroductionLongerThanVisibleLimit() {
		String tooLong = "脆".repeat(GoodsIntroductionSanitizer.MAX_VISIBLE_LENGTH + 1);

		assertThatThrownBy(() -> GoodsIntroductionSanitizer.sanitize(tooLong))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg())
						.contains("不能超过 120 个字"));
	}

	@Test
	void visibleLengthIgnoresMarkupAndEntities() {
		String html = "<p><span style=\"color: #c00\">甜</span>&nbsp;脆</p>";

		assertThat(GoodsIntroductionSanitizer.sanitize(html))
				.isEqualTo("<p><span style=\"color: #c00\">甜</span>&nbsp;脆</p>");
	}
}
