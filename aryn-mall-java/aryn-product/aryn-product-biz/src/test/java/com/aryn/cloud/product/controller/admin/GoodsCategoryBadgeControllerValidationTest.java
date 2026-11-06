package com.aryn.cloud.product.controller.admin;

import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角标配置校验口径（文字/图片二选一、图片地址前缀、高度/圆角范围与缺省值）。
 *
 * <p>角标配置会原样下发到 C 端渲染：图片地址会变成 {@code <image src>}，尺寸越界会撑破
 * 分类页左栏。校验一旦放宽，脏值会静默进入 C 端（没有报错、接口照常 200），因此在这里
 * 把边界钉住，而不是只靠联调时目检。
 *
 * @since 2026-11-05 角标图片/尺寸/圆角自定义
 */
class GoodsCategoryBadgeControllerValidationTest {

	private final GoodsCategoryBadgeController controller = new GoodsCategoryBadgeController(null);

	@Test
	void rejectsNullBadge() {
		assertThat(controller.validateAndNormalize(null)).isEqualTo("角标参数不能为空");
	}

	@Test
	void acceptsTextBadgeAndFillsStyleDefaults() {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setBadgeText(" 荐 ");

		assertThat(controller.validateAndNormalize(badge)).isNull();
		assertThat(badge.getBadgeText()).isEqualTo("荐");
		assertThat(badge.getIconUrl()).isEmpty();
		assertThat(badge.getBgColor()).isEqualTo("#00B578");
		assertThat(badge.getTextColor()).isEqualTo("#FFFFFF");
		assertThat(badge.getBadgeHeight()).isEqualTo(28);
		assertThat(badge.getBorderRadius()).isEqualTo(4);
		assertThat(badge.getSort()).isZero();
		assertThat(badge.getStatus()).isEqualTo("0");
	}

	@Test
	void acceptsImageOnlyBadgeWithCustomSizeAndRadius() {
		// 图片角标不展示文字：允许文字为空，高度/圆角按运营配置保留
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setIconUrl("https://cdn.example.com/badge/new.png");
		badge.setBadgeHeight(40);
		badge.setBorderRadius(10);

		assertThat(controller.validateAndNormalize(badge)).isNull();
		assertThat(badge.getBadgeText()).isEmpty();
		assertThat(badge.getIconUrl()).isEqualTo("https://cdn.example.com/badge/new.png");
		assertThat(badge.getBadgeHeight()).isEqualTo(40);
		assertThat(badge.getBorderRadius()).isEqualTo(10);
	}

	@Test
	void acceptsRootRelativeImageUrl() {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setIconUrl(" /boot/file/local/1/badge.png ");
		badge.setBadgeText("荐");

		assertThat(controller.validateAndNormalize(badge)).isNull();
		assertThat(badge.getIconUrl()).isEqualTo("/boot/file/local/1/badge.png");
	}

	@Test
	void rejectsBadgeWithoutTextOrImage() {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setBadgeText("   ");

		assertThat(controller.validateAndNormalize(badge)).isEqualTo("角标文字不能为空");
	}

	@Test
	void rejectsTextLongerThanTwoCodePoints() {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setBadgeText("严选好");

		assertThat(controller.validateAndNormalize(badge)).isEqualTo("角标文字最多 2 个字符");
	}

	@Test
	void countsEmojiAsOneCodePoint() {
		// emoji 是代理对：按 char 长度是 2，按码点算 1，与前端 maxlength 口径一致
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setBadgeText("🔥");

		assertThat(controller.validateAndNormalize(badge)).isNull();
	}

	@Test
	void rejectsUnsafeImageUrlScheme() {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setIconUrl("javascript:alert(1)");
		badge.setBadgeText("荐");

		assertThat(controller.validateAndNormalize(badge))
			.isEqualTo("角标图片地址格式不正确，应以 http://、https:// 或 / 开头");
	}

	@Test
	void rejectsTooLongImageUrl() {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setIconUrl("https://cdn.example.com/" + "a".repeat(500));
		badge.setBadgeText("荐");

		assertThat(controller.validateAndNormalize(badge)).isEqualTo("角标图片地址过长");
	}

	@Test
	void rejectsHeightOutOfRange() {
		GoodsCategoryBadge tooShort = new GoodsCategoryBadge();
		tooShort.setBadgeText("荐");
		tooShort.setBadgeHeight(15);
		assertThat(controller.validateAndNormalize(tooShort)).isEqualTo("角标高度需在 16-96rpx 之间");

		GoodsCategoryBadge tooTall = new GoodsCategoryBadge();
		tooTall.setBadgeText("荐");
		tooTall.setBadgeHeight(97);
		assertThat(controller.validateAndNormalize(tooTall)).isEqualTo("角标高度需在 16-96rpx 之间");
	}

	@Test
	void rejectsRadiusOutOfRange() {
		GoodsCategoryBadge negative = new GoodsCategoryBadge();
		negative.setBadgeText("荐");
		negative.setBorderRadius(-1);
		assertThat(controller.validateAndNormalize(negative)).isEqualTo("角标圆角需在 0-48rpx 之间");

		GoodsCategoryBadge tooRound = new GoodsCategoryBadge();
		tooRound.setBadgeText("荐");
		tooRound.setBorderRadius(49);
		assertThat(controller.validateAndNormalize(tooRound)).isEqualTo("角标圆角需在 0-48rpx 之间");
	}

}
