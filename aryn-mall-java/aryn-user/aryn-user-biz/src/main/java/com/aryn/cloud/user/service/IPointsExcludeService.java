package com.aryn.cloud.user.service;

import com.aryn.cloud.user.api.entity.PointsExclude;

import java.util.List;

/**
 * 积分排除清单（品类/商品黑名单）：命中即不参与积分抵扣与返积分。
 */
public interface IPointsExcludeService {

	/**
	 * 当前租户全部排除规则（按创建时间倒序，量级小不分页）。
	 */
	List<PointsExclude> listAll();

	/**
	 * 新增排除规则（同租户同类型同目标唯一）。
	 */
	boolean addExclude(PointsExclude exclude);

	/**
	 * 按主键移除排除规则（逻辑删除）。
	 */
	boolean removeExclude(String id);

}
