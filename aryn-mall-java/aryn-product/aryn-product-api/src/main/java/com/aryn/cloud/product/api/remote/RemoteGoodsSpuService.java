/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.ProductOverviewVO;

import java.util.List;

/**
 * @author 雨滴kian
 */
public interface RemoteGoodsSpuService {

	/**
	 * 获取商品概览统计
	 * @return ProductOverviewVO
	 */
	ProductOverviewVO getProductOverview();

	/**
	 * 按 ID 批量获取 SPU（供跨模块回填商品名等基础信息）
	 * @param ids spuId 集合
	 * @return 存在的 SPU 列表，含名称
	 */
	List<GoodsSpu> getSpuByIds(List<String> ids);

}
