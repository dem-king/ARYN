package com.aryn.cloud.product.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsAttrTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsAttrTagNormalizerTest {

	@Test
	void nullStaysNullSoPartialUpdatesKeepStoredTags() {
		assertThat(GoodsAttrTagNormalizer.normalize(null)).isNull();
	}

	@Test
	void trimsValuesAndDropsBlankPlaceholderRows() {
		List<GoodsAttrTag> tags = new ArrayList<>();
		tags.add(new GoodsAttrTag());
		tags.add(tag("  地标  ", "  系列  "));

		List<GoodsAttrTag> normalized = GoodsAttrTagNormalizer.normalize(tags);

		assertThat(normalized).hasSize(1);
		assertThat(normalized.get(0).getValue()).isEqualTo("地标");
		assertThat(normalized.get(0).getLabel()).isEqualTo("系列");
	}

	@Test
	void rejectsMoreThanSixTags() {
		List<GoodsAttrTag> tags = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			tags.add(tag("值" + i, "名" + i));
		}

		assertThatThrownBy(() -> GoodsAttrTagNormalizer.normalize(tags))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("最多配置 6 个"));
	}

	@Test
	void requiresValueAndLabelTogether() {
		assertThatThrownBy(() -> GoodsAttrTagNormalizer.normalize(List.of(tag("地标", ""))))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("同时填写"));
	}

	@Test
	void defaultsUnknownToneAndLinkType() {
		GoodsAttrTag source = tag("地标", "系列");
		source.setTone("purple");
		source.setLinkType("external");

		GoodsAttrTag normalized = GoodsAttrTagNormalizer.normalize(List.of(source)).get(0);

		assertThat(normalized.getTone()).isEqualTo("gray");
		assertThat(normalized.getLinkType()).isEqualTo("none");
	}

	@Test
	void popupRequiresContentAndFallsBackToValueAsTitle() {
		GoodsAttrTag missingContent = tag("冷藏", "储存条件");
		missingContent.setLinkType("popup");

		assertThatThrownBy(() -> GoodsAttrTagNormalizer.normalize(List.of(missingContent)))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("弹窗内容不能为空"));

		GoodsAttrTag valid = tag("冷藏", "储存条件");
		valid.setLinkType("popup");
		valid.setPopupContent("  0~4℃ 冷藏保存  ");
		GoodsAttrTag normalized = GoodsAttrTagNormalizer.normalize(List.of(valid)).get(0);

		assertThat(normalized.getPopupTitle()).isEqualTo("冷藏");
		assertThat(normalized.getPopupContent()).isEqualTo("0~4℃ 冷藏保存");
	}

	@Test
	void pageLinkOnlyAllowsInternalPaths() {
		GoodsAttrTag external = tag("榜单", "排行榜");
		external.setLinkType("page");
		external.setLinkUrl("https://example.com/rank");

		assertThatThrownBy(() -> GoodsAttrTagNormalizer.normalize(List.of(external)))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg())
						.contains("/pages/ 或 /sub-pages/"));

		GoodsAttrTag internal = tag("榜单", "排行榜");
		internal.setLinkType("page");
		internal.setLinkUrl("/sub-pages/product/goods-list/index");
		GoodsAttrTag normalized = GoodsAttrTagNormalizer.normalize(List.of(internal)).get(0);

		assertThat(normalized.getLinkUrl()).isEqualTo("/sub-pages/product/goods-list/index");
	}

	@Test
	void keepsWhitelistedIconAndDropsUnknownValues() {
		GoodsAttrTag location = tag("地标", "系列");
		location.setIcon("location");
		GoodsAttrTag malicious = tag("地标2", "系列2");
		malicious.setIcon("javascript:alert(1)");

		List<GoodsAttrTag> normalized = GoodsAttrTagNormalizer.normalize(List.of(location, malicious));

		assertThat(normalized.get(0).getIcon()).isEqualTo("location");
		assertThat(normalized.get(1).getIcon()).isNull();
	}

	@Test
	void generatesKeysInDisplayOrder() {
		List<GoodsAttrTag> normalized = GoodsAttrTagNormalizer.normalize(List.of(
				tag("地标", "系列"),
				tag("冷藏", "储存条件")));

		assertThat(normalized).extracting(GoodsAttrTag::getKey)
				.containsExactly("attr-1", "attr-2");
	}

	private GoodsAttrTag tag(String value, String label) {
		GoodsAttrTag tag = new GoodsAttrTag();
		tag.setValue(value);
		tag.setLabel(label);
		return tag;
	}
}
