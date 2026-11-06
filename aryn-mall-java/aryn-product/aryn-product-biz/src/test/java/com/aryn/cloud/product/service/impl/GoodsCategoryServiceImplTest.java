package com.aryn.cloud.product.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.service.IGoodsCategoryBadgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 类目树接口对管理端与 C 端的字段承诺（角标配置化后两端口径不同）。
 *
 * <p>类目引用 {@code goods_category.badge_type}，树接口把它解析成配置好的
 * {@code {text,bgColor,color,iconUrl,height,borderRadius}} 随节点下发：
 * <ul>
 *   <li><b>管理端</b>（{@code /goodscategory/tree}，{@link GoodsCategoryServiceImpl#getGoodsCategoryTreeList()}）：
 *       {@code extra.badgeType} 保留数据库原始值（未配置为 null、停用/未知 key 原样），
 *       供类目列表/表单回显「引用了停用角标」的类目；{@code extra.badge} 仅在启用时为对象；</li>
 *   <li><b>App 端</b>（{@code /app/goodscategory/tree}，
 *       {@link GoodsCategoryServiceImpl#getAppGoodsCategoryTreeList()}）：命中启用角标才保留原始 key，
 *       否则归一 {@code badgeType='0'} + {@code badge=null}，C 端只渲染配置命中的角标。</li>
 * </ul>
 * 两种口径下 {@code badge} 键都必须始终写入（值可为 null）：C 端据此区分「新后端明确无角标」
 * 与「旧后端未下发」。hutool 的 TreeNode extra 会被拍平进节点 JSON，映射一旦漏掉，管理后台
 * 配好的角标在 C 端**静默不显示**（没有报错、接口照常 200），类型检查也拦不住，故在此钉住契约。
 */
class GoodsCategoryServiceImplTest {

	private GoodsCategoryMapper categoryMapper;

	private IGoodsCategoryBadgeService goodsCategoryBadgeService;

	private GoodsCategoryServiceImpl service;

	@BeforeEach
	void setUp() {
		categoryMapper = mock(GoodsCategoryMapper.class);
		goodsCategoryBadgeService = mock(IGoodsCategoryBadgeService.class);
		service = new TestGoodsCategoryService(categoryMapper, goodsCategoryBadgeService);
	}

	@Test
	void appTreeResolvesEnabledBadgeAndNormalizesUnmatchedKey() {
		when(goodsCategoryBadgeService.listEnabledBadges()).thenReturn(List.of(
				badge("1", "荐", "#00B578", "#FFFFFF", 1),
				badge("2", "热", "#FF4D2D", "#FFFFFF", 2)));
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "0", 1, "1"),
				category("cat-2", "肉禽蛋", "0", 2, "2"),
				category("cat-3", "海鲜水产", "0", 3, null),
				category("cat-4", "历史脏值", "0", 4, "legacy-key")));

		List<Tree<String>> tree = service.getAppGoodsCategoryTreeList();

		assertThat(tree).hasSize(4);
		// 命中启用角标：保留原始 key，并下发配置好的文字与双色
		assertThat(nodeOf(tree, "cat-1")).containsEntry("badgeType", "1");
		assertThat(badgeOf(tree, "cat-1")).isEqualTo(payload("荐", "#00B578", "#FFFFFF"));
		assertThat(nodeOf(tree, "cat-2")).containsEntry("badgeType", "2");
		assertThat(badgeOf(tree, "cat-2")).isEqualTo(payload("热", "#FF4D2D", "#FFFFFF"));
		// 未配置 / 未知 key：归一 '0'，badge 显式为 null（键必须存在）
		assertThat(nodeOf(tree, "cat-3")).containsEntry("badgeType", "0");
		assertThat(badgeOf(tree, "cat-3")).isNull();
		assertThat(nodeOf(tree, "cat-4")).containsEntry("badgeType", "0");
		assertThat(badgeOf(tree, "cat-4")).isNull();
	}

	@Test
	void appTreeFallsBackToNoBadgeWhenReferencedBadgeIsDisabled() {
		// 服务层只返回启用角标：停用/已删除的配置不会出现在这里，引用它的类目按无角标处理
		when(goodsCategoryBadgeService.listEnabledBadges()).thenReturn(List.of());
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "img.png", 1, "1")));

		Map<String, Object> node = service.getAppGoodsCategoryTreeList().get(0);

		assertThat(node).containsEntry("badgeType", "0");
		assertThat(node).containsKey("badge");
		assertThat(node.get("badge")).isNull();
	}

	@Test
	void adminTreeKeepsRawBadgeTypeSoDisabledOrUnknownKeyCanBeEchoed() {
		// 管理端只把启用角标解析成展示对象；停用/未知 key 必须原样回传，否则类目列表无法回显
		when(goodsCategoryBadgeService.listEnabledBadges()).thenReturn(List.of(
				badge("1", "荐", "#00B578", "#FFFFFF", 1)));
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "0", 1, "1"),
				category("cat-2", "肉禽蛋", "0", 2, "2"),
				category("cat-3", "海鲜水产", "0", 3, null),
				category("cat-4", "历史脏值", "0", 4, "legacy-key")));

		List<Tree<String>> tree = service.getGoodsCategoryTreeList();

		// 启用角标：原始 key + 展示对象
		assertThat(nodeOf(tree, "cat-1")).containsEntry("badgeType", "1");
		assertThat(badgeOf(tree, "cat-1")).isEqualTo(payload("荐", "#00B578", "#FFFFFF"));
		// 停用角标：原始 key 保留，badge 为 null（前端据此提示「已停用」）
		assertThat(nodeOf(tree, "cat-2")).containsEntry("badgeType", "2");
		assertThat(badgeOf(tree, "cat-2")).isNull();
		// 未配置：原样回传 null（不要归一成 '0'）
		assertThat(nodeOf(tree, "cat-3")).containsKey("badgeType");
		assertThat(nodeOf(tree, "cat-3").get("badgeType")).isNull();
		assertThat(badgeOf(tree, "cat-3")).isNull();
		// 历史脏值/未知 key：原样回传，便于表单追加「已失效角标」选项并保持可保存
		assertThat(nodeOf(tree, "cat-4")).containsEntry("badgeType", "legacy-key");
		assertThat(badgeOf(tree, "cat-4")).isNull();
	}

	@Test
	void appTreeCarriesImageBadgeWithCustomSizeAndRadius() {
		// 图片角标：文字可为空（C 端只渲染图片），高度/圆角由运营自定义并随树下发
		GoodsCategoryBadge imageBadge = badge("img-key", "", "#00B578", "#FFFFFF", 3);
		imageBadge.setIconUrl("https://cdn.example.com/badge/new.png");
		imageBadge.setBadgeHeight(40);
		imageBadge.setBorderRadius(10);
		when(goodsCategoryBadgeService.listEnabledBadges()).thenReturn(List.of(imageBadge));
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "0", 1, "img-key")));

		Map<String, Object> node = service.getAppGoodsCategoryTreeList().get(0);

		assertThat(node).containsEntry("badgeType", "img-key");
		assertThat(badgeOf(service.getAppGoodsCategoryTreeList(), "cat-1")).isEqualTo(Map.of(
				"text", "", "bgColor", "#00B578", "color", "#FFFFFF",
				"iconUrl", "https://cdn.example.com/badge/new.png",
				"height", 40, "borderRadius", 10));
	}

	@Test
	void existingFieldsStayAvailableToTheAdminTree() {
		when(goodsCategoryBadgeService.listEnabledBadges()).thenReturn(List.of());
		when(categoryMapper.selectList(any())).thenReturn(List.of(
				category("cat-1", "蔬菜", "img.png", 1, "0")));

		// hutool 的 Tree 继承 LinkedHashMap：TreeNode extra 的键会被**拍平到节点顶层**，
		// 不存在名为 "extra" 的键（这也是 categoryPic 在接口 JSON 里是平铺的原因）。
		// Tree 同时实现 Map 与 Comparable，断言要显式声明 Map 类型消除歧义。
		Map<String, Object> node = service.getGoodsCategoryTreeList().get(0);

		// 管理端类目树/表单依赖这些字段，加角标不能顺手删掉；badge 键也必须始终存在
		assertThat(node).containsKeys("categoryPic", "description", "status", "sort", "badgeType", "badge");
	}

	private Map<String, Object> nodeOf(List<Tree<String>> tree, String id) {
		return tree.stream()
			.filter(node -> id.equals(node.getId()))
			.findFirst()
			.orElseThrow();
	}

	private Object badgeOf(List<Tree<String>> tree, String id) {
		// 不能用 Optional.map 取 badge：角标未配置时值为 null，
		// 而 Optional.map 遇 null 会返回空 Optional，与「节点不存在」无法区分。
		return nodeOf(tree, id).get("badge");
	}

	/**
	 * 文字角标的默认展示数据：历史行/未配置新字段时高度、圆角兜底成列默认值，图片为空串。
	 */
	private Map<String, Object> payload(String text, String bgColor, String color) {
		return Map.of(
				"text", text,
				"bgColor", bgColor,
				"color", color,
				"iconUrl", "",
				"height", 28,
				"borderRadius", 4);
	}

	private GoodsCategoryBadge badge(String badgeKey, String badgeText, String bgColor, String textColor, int sort) {
		GoodsCategoryBadge badge = new GoodsCategoryBadge();
		badge.setBadgeKey(badgeKey);
		badge.setBadgeText(badgeText);
		badge.setBgColor(bgColor);
		badge.setTextColor(textColor);
		badge.setSort(sort);
		badge.setStatus("0");
		return badge;
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

		private TestGoodsCategoryService(GoodsCategoryMapper categoryMapper,
				IGoodsCategoryBadgeService goodsCategoryBadgeService) {
			super(goodsCategoryBadgeService);
			this.baseMapper = categoryMapper;
		}

	}

}
