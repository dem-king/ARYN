package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;
import com.aryn.cloud.product.mapper.GoodsCategoryBadgeMapper;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类目角标删除保护契约。
 *
 * <p>角标是类目引用的配置载体：被类目引用的角标一旦物理/逻辑删除，树接口解析不到配置，
 * C 端角标会**静默消失**（接口照常 200）。因此删除前必须检查 {@code goods_category.badge_type}
 * 是否仍有未删除类目引用 {@code goods_category_badge.badge_key}，被引用时返回 false，
 * 由控制器转换为 {@code Result.fail("角标已被类目使用，请先解除后再删除")}。
 */
class GoodsCategoryBadgeServiceImplTest {

	private GoodsCategoryBadgeMapper badgeMapper;

	private GoodsCategoryMapper categoryMapper;

	private GoodsCategoryBadgeServiceImpl service;

	@BeforeEach
	void setUp() {
		badgeMapper = mock(GoodsCategoryBadgeMapper.class);
		categoryMapper = mock(GoodsCategoryMapper.class);
		service = new TestGoodsCategoryBadgeService(badgeMapper, categoryMapper);
	}

	@Test
	void deleteIsRejectedWhileSomeCategoryStillReferencesTheBadge() {
		when(badgeMapper.selectById("badge-1")).thenReturn(badge("badge-1", "badge-1"));
		// 有 4 个未删除类目仍在引用该角标（试点租户「热」的历史引用量级）
		when(categoryMapper.selectCount(any())).thenReturn(4L);

		assertThat(service.removeBadgeById("badge-1")).isFalse();

		// 被引用时不能落删除（含逻辑删除），否则 C 端角标会静默消失
		verify(badgeMapper, never()).deleteById(any(java.io.Serializable.class));
	}

	@Test
	void deleteSucceedsWhenNoCategoryReferencesTheBadge() {
		when(badgeMapper.selectById("badge-2")).thenReturn(badge("badge-2", "badge-2"));
		when(categoryMapper.selectCount(any())).thenReturn(0L);
		when(badgeMapper.deleteById("badge-2")).thenReturn(1);

		assertThat(service.removeBadgeById("badge-2")).isTrue();
	}

	@Test
	void deleteOfMissingBadgeReturnsFalse() {
		when(badgeMapper.selectById("badge-missing")).thenReturn(null);

		assertThat(service.removeBadgeById("badge-missing")).isFalse();
	}

	private GoodsCategoryBadge badge(String id, String badgeKey) {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setId(id);
		badge.setBadgeKey(badgeKey);
		badge.setBadgeText("荐");
		badge.setBgColor("#00B578");
		badge.setTextColor("#FFFFFF");
		badge.setSort(1);
		badge.setStatus("0");
		badge.setDelFlag("0");
		return badge;
	}

	/**
	 * baseMapper 是 ServiceImpl 继承链上的 protected 字段，只能在子类内部赋值
	 * （与 GoodsSpuServiceImplTest / GoodsCategoryServiceImplTest 同一写法）。
	 */
	private static final class TestGoodsCategoryBadgeService extends GoodsCategoryBadgeServiceImpl {

		private TestGoodsCategoryBadgeService(GoodsCategoryBadgeMapper badgeMapper,
				GoodsCategoryMapper categoryMapper) {
			super(categoryMapper);
			this.baseMapper = badgeMapper;
		}

	}

}
