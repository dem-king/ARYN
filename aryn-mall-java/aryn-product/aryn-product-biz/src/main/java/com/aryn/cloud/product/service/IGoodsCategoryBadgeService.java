package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;

import java.util.List;

/**
 * 类目角标配置
 *
 * @since 2026-11-05 类目角标配置化
 */
public interface IGoodsCategoryBadgeService extends IService<GoodsCategoryBadge> {

	/**
	 * 新增角标：{@code badgeKey} 由后端生成（初值=雪花 id），忽略入参中的 id/badgeKey。
	 *
	 * @param badge 角标配置（文字/底色/字色/排序/状态）
	 * @return 是否保存成功
	 */
	boolean saveBadge(GoodsCategoryBadge badge);

	/**
	 * 修改角标：{@code badgeKey} 创建后不可修改，忽略入参中的 badgeKey。
	 *
	 * @param badge 角标配置（必须带 id）
	 * @return 是否修改成功
	 */
	boolean updateBadge(GoodsCategoryBadge badge);

	/**
	 * 删除角标：被任何未删除类目引用时拒绝删除。
	 *
	 * <p>被引用时返回 false 而不是抛异常，由管理端控制器转换为
	 * {@code Result.fail("角标已被类目使用，请先解除后再删除")}，与商品品牌删除口径一致。
	 *
	 * @param id 角标主键
	 * @return true 删除成功；false 被类目引用（或角标不存在）
	 */
	boolean removeBadgeById(String id);

	/**
	 * 当前租户启用中的角标列表（{@code status='0'}，按 sort 升序），供类目树接口做 key → 配置解析。
	 *
	 * @return 启用角标；无配置时返回空列表而非 null
	 */
	List<GoodsCategoryBadge> listEnabledBadges();

}
