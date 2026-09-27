package com.aryn.cloud.product.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 类目树接口对 C 端分类页的字段承诺。
 *
 * <p>分类页左栏的角标（「荐」/「热」）靠树接口透出 `badgeType`：
 * hutool 的 TreeNode extra 会被拍平进节点 JSON，这个映射一旦漏掉，
 * 管理后台配好的角标在 C 端**静默不显示**（没有报错、接口照常 200），
 * 类型检查也拦不住。因此在这里把字段契约钉住。
 */
class GoodsCategoryServiceImplTest {

	private GoodsCategoryMapper categoryMapper;

	private GoodsCategoryServiceImpl service;

	@BeforeEach
	void setUp() {
		categoryMapper = mock(GoodsCategoryMapper.class);
		service = new TestGoodsCategoryService(categoryMapper);
	}

	@Test
	void treeCarriesBadgeTypeSoTheCategoryPageCanRenderBadges() {
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "0", 1, "1"),
				category("cat-2", "肉禽蛋", "0", 2, "2"),
				category("cat-3", "海鲜水产", "0", 3, null)));

		List<Tree<String>> tree = service.getGoodsCategoryTreeList();

		assertThat(tree).hasSize(3);
		assertThat(badgeOf(tree, "cat-1")).isEqualTo("1");
		assertThat(badgeOf(tree, "cat-2")).isEqualTo("2");
		// 未配置角标时透出 null，由前端判定为「不渲染」而不是渲染空角标
		assertThat(badgeOf(tree, "cat-3")).isNull();
	}

	@Test
	void existingFieldsStayAvailableToTheAdminTree() {
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "img.png", 1, "0")));

		// hutool 的 Tree 继承 LinkedHashMap：TreeNode extra 的键会被**拍平到节点顶层**，
		// 不存在名为 "extra" 的键（这也是 categoryPic 在接口 JSON 里是平铺的原因）。
		// Tree 同时实现 Map 与 Comparable，断言要显式声明 Map 类型消除歧义。
		Map<String, Object> node = service.getGoodsCategoryTreeList().get(0);

		// 管理端类目树/表单依赖这些字段，加角标不能顺手删掉
		assertThat(node).containsKeys("categoryPic", "description", "status", "sort", "badgeType");
	}

	private Object badgeOf(List<Tree<String>> tree, String id) {
		// 不能用 Optional.map 取 badgeType：角标未配置时值为 null，
		// 而 Optional.map 遇 null 会返回空 Optional，与「节点不存在」无法区分。
		return tree.stream()
			.filter(node -> id.equals(node.getId()))
			.findFirst()
			.orElseThrow()
			.get("badgeType");
	}

	private GoodsCategory category(String id, String name, String pic, int sort, String badgeType) {
		GoodsCategory category = new GoodsCategory();
		category.setId(id);
		category.setName(name);
		category.setParentId("0");
		category.setCategoryPic(pic);
		category.setStatus("0");
		category.setSort(sort);
		category.setBadgeType(badgeType);
		return category;
	}

	/**
	 * baseMapper 是 ServiceImpl 的 protected 字段，只能在子类内部赋值
	 * （与 GoodsSpuServiceImplTest 同一写法）。
	 */
	private static final class TestGoodsCategoryService extends GoodsCategoryServiceImpl {

		private TestGoodsCategoryService(GoodsCategoryMapper categoryMapper) {
			this.baseMapper = categoryMapper;
		}

	}

}
