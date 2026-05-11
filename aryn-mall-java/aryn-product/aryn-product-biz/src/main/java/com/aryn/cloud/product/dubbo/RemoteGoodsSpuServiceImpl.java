/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.product.dubbo;

import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.vo.ProductOverviewVO;
import com.aryn.cloud.product.service.IProductStatisticsService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGoodsSpuServiceImpl implements RemoteGoodsSpuService {

	private final IProductStatisticsService productStatisticsService;

	@Override
	public ProductOverviewVO getProductOverview() {
		return productStatisticsService.getProductOverview();
	}

}
