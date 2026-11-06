package com.aryn.cloud.product.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;
import com.aryn.cloud.product.mapper.GoodsCategoryBadgeMapper;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.service.IGoodsCategoryBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 类目角标配置
 *
 * @since 2026-11-05 类目角标配置化
 */
@Service
@RequiredArgsConstructor
public class GoodsCategoryBadgeServiceImpl extends ServiceImpl<GoodsCategoryBadgeMapper, GoodsCategoryBadge>
		implements IGoodsCategoryBadgeService {

	private final GoodsCategoryMapper goodsCategoryMapper;

	@Override
	public boolean saveBadge(GoodsCategoryBadge badge) {
		// badge_key 是稳定键，只能由后端生成：忽略前端可能回传的 id/badgeKey，
		// 两者统一取雪花 id（初值=id），保证新建后 key 与主键一致、不会与历史枚举值混淆。
		String badgeKey = IdUtil.getSnowflakeNextIdStr();
		badge.setId(badgeKey);
		badge.setBadgeKey(badgeKey);
		return save(badge);
	}

	@Override
	public boolean updateBadge(GoodsCategoryBadge badge) {
		// badge_key 创建后不可修改：显式置空，update-strategy=not_null 会跳过该列；
		// 同时避免前端漏传 key 时把库里的稳定键覆盖成 null。
		badge.setBadgeKey(null);
		return updateById(badge);
	}

	@Override
	public boolean removeBadgeById(String id) {
		GoodsCategoryBadge badge = getById(id);
		if (badge == null) {
			return false;
		}
		// 逻辑删除的类目不再引用角标，按 MyBatis-Plus 逻辑删除语义只统计未删除类目
		Long referenced = goodsCategoryMapper.selectCount(Wrappers.<GoodsCategory>lambdaQuery()
			.eq(GoodsCategory::getBadgeType, badge.getBadgeKey()));
		if (referenced != null && referenced > 0) {
			return false;
		}
		return removeById(id);
	}

	@Override
	public List<GoodsCategoryBadge> listEnabledBadges() {
		// 树接口按 key → 配置解析角标；停用/已删除不应下发，删除由 @TableLogic 自动过滤
		return lambdaQuery()
			.eq(GoodsCategoryBadge::getStatus, "0")
			.orderByAsc(GoodsCategoryBadge::getSort)
			.orderByAsc(GoodsCategoryBadge::getCreateTime)
			.list();
	}

}
