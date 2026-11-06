package com.aryn.cloud.product.service.impl;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.service.IGoodsCategoryBadgeService;
import com.aryn.cloud.product.service.IGoodsCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品类目
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Service
@RequiredArgsConstructor
public class GoodsCategoryServiceImpl extends ServiceImpl<GoodsCategoryMapper, GoodsCategory>
		implements IGoodsCategoryService {

	/** 无角标（未配置 / 已停用 / 已删除 / key 非法）时下发的 badgeType 值 */
	private static final String NO_BADGE_KEY = "0";

	/** 角标高度缺省值（rpx，与 goods_category_badge.badge_height 列默认值一致） */
	private static final int DEFAULT_BADGE_HEIGHT = 28;

	/** 角标圆角缺省值（rpx，与 goods_category_badge.border_radius 列默认值一致） */
	private static final int DEFAULT_BORDER_RADIUS = 4;

	private final IGoodsCategoryBadgeService goodsCategoryBadgeService;

	@Override
	public List<Tree<String>> getGoodsCategoryTreeList() {
		// 管理端口径：badgeType 原样回传，便于类目列表/表单回显「引用了停用角标」的类目
		return buildTree(true);
	}

	@Override
	public List<Tree<String>> getAppGoodsCategoryTreeList() {
		// App 口径：未命中启用角标归一为 '0'，C 端只渲染配置命中的角标
		return buildTree(false);
	}

	/**
	 * 构建类目树（管理端与 App 共用，角标归一规则由 {@code keepRawBadgeType} 区分）。
	 *
	 * <p>{@code extra.badge} 键在两种口径下都必须始终写入（值可为 null）：C 端据此区分
	 * 「新后端明确无角标」与「旧后端未下发」；管理端同时用它判断角标是否仍启用。
	 *
	 * @param keepRawBadgeType true=管理端口径，badgeType 保留数据库原始值（含停用/未知 key）；
	 *                         false=App 口径，未命中启用角标时 badgeType 归一为 {@code '0'}
	 * @return 类目树
	 */
	private List<Tree<String>> buildTree(boolean keepRawBadgeType) {
		List<GoodsCategory> goodsCategories = baseMapper.selectList(Wrappers.emptyWrapper());
		// 先取当前租户启用中的角标配置（停用/已删除由服务层过滤），构建 badgeKey → 配置映射。
		// 一次查询 + 内存映射，避免每个类目节点各查一次配置（N+1）。
		Map<String, GoodsCategoryBadge> enabledBadges = toBadgeMap(goodsCategoryBadgeService.listEnabledBadges());
		List<TreeNode<String>> treeNodes = goodsCategories.stream()
			.sorted(Comparator.comparingInt(GoodsCategory::getSort))
			.map(goodsCategory -> {
				TreeNode<String> treeNode = new TreeNode();
				treeNode.setId(goodsCategory.getId());
				treeNode.setParentId(goodsCategory.getParentId());
				treeNode.setName(goodsCategory.getName());
				treeNode.setWeight(goodsCategory.getSort());
				String badgeType = goodsCategory.getBadgeType();
				GoodsCategoryBadge badge = badgeType == null ? null : enabledBadges.get(badgeType);
				Map<String, Object> extra = new HashMap<>();
				extra.put("categoryPic", goodsCategory.getCategoryPic());
				extra.put("createTime", goodsCategory.getCreateTime());
				extra.put("description", goodsCategory.getDescription());
				extra.put("status", goodsCategory.getStatus());
				extra.put("sort", goodsCategory.getSort());
				// 管理端原样保留 badgeType（含停用/未知 key，供回显）；App 端命中启用角标才保留原始
				// key，否则统一回落 '0'（C 端只认命中配置的 key）。
				extra.put("badgeType", keepRawBadgeType || badge != null ? badgeType : NO_BADGE_KEY);
				extra.put("badge", badge == null ? null : toBadgePayload(badge));
				treeNode.setExtra(extra);
				return treeNode;
			})
			.collect(Collectors.toList());
		return TreeUtil.build(treeNodes, CommonConstants.PARENT_ID);
	}

	/**
	 * 启用角标列表转 badgeKey → 配置映射。
	 *
	 * @param badges 启用角标（可能为 null/空）
	 * @return 可安全 get 的映射；key 为空的行忽略，重复 key 保留先出现的那条
	 */
	private Map<String, GoodsCategoryBadge> toBadgeMap(List<GoodsCategoryBadge> badges) {
		if (badges == null || badges.isEmpty()) {
			return Map.of();
		}
		return badges.stream()
			.filter(badge -> badge.getBadgeKey() != null && !badge.getBadgeKey().isEmpty())
			.collect(Collectors.toMap(GoodsCategoryBadge::getBadgeKey, badge -> badge, (exists, ignored) -> exists));
	}

	/**
	 * 树接口下发给 C 端的角标展示数据（键名与契约一致：text/bgColor/color/iconUrl/height/borderRadius）。
	 *
	 * <p>C 端按服务端下发样式直接渲染，不再内置「荐绿/热红」映射；顺序固定便于联调比对。
	 * 高度/圆角允许运营自定义（图片角标也复用同一套尺寸字段），实体值为 null 时兜底成
	 * 列默认值，保证 C 端永远拿到可直接使用的数值，不用在渲染层再猜。
	 *
	 * @param badge 命中的启用角标
	 * @return 展示数据
	 */
	private Map<String, Object> toBadgePayload(GoodsCategoryBadge badge) {
		Map<String, Object> payload = new LinkedHashMap<>(6);
		payload.put("text", badge.getBadgeText());
		payload.put("bgColor", badge.getBgColor());
		payload.put("color", badge.getTextColor());
		payload.put("iconUrl", badge.getIconUrl() == null ? "" : badge.getIconUrl());
		payload.put("height", badge.getBadgeHeight() == null ? DEFAULT_BADGE_HEIGHT : badge.getBadgeHeight());
		payload.put("borderRadius",
			badge.getBorderRadius() == null ? DEFAULT_BORDER_RADIUS : badge.getBorderRadius());
		return payload;
	}

}
