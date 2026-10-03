/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.product.dubbo;

import cn.hutool.core.collection.CollUtil;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.vo.ProductOverviewVO;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.service.IGoodsSpuService;
import com.aryn.cloud.product.service.IProductStatisticsService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

	private final IGoodsSpuService goodsSpuService;

	private final GoodsCategoryMapper goodsCategoryMapper;

	@Override
	public ProductOverviewVO getProductOverview() {
		return productStatisticsService.getProductOverview();
	}

	@Override
	public List<GoodsSpu> getSpuByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		List<GoodsSpu> spus = goodsSpuService.listByIds(ids);
		fillCategoryName(spus);
		return spus;
	}

	/**
	 * 批量回填类目名（一级/二级，与 {@code IGoodsSpuService#getSpuById} 的拼接口径一致）。
	 *
	 * <p>listByIds 只映射表列，categoryName 是 exist=false 的派生字段，不回填会恒为 null。
	 */
	private void fillCategoryName(List<GoodsSpu> spus) {
		if (CollUtil.isEmpty(spus)) {
			return;
		}
		Set<String> categoryIds = spus.stream()
			.flatMap(spu -> Stream.of(spu.getCategoryFirstId(), spu.getCategorySecondId()))
			.filter(Objects::nonNull)
			.filter(id -> !id.isBlank())
			.collect(Collectors.toSet());
		if (categoryIds.isEmpty()) {
			return;
		}
		Map<String, String> nameById = goodsCategoryMapper.selectBatchIds(categoryIds).stream()
			.collect(Collectors.toMap(GoodsCategory::getId, GoodsCategory::getName, (first, second) -> first));
		spus.forEach(spu -> {
			StringBuilder categoryName = new StringBuilder();
			String firstName = nameById.get(spu.getCategoryFirstId());
			String secondName = nameById.get(spu.getCategorySecondId());
			if (firstName != null) {
				categoryName.append(firstName);
			}
			if (secondName != null) {
				if (categoryName.length() > 0) {
					categoryName.append("/");
				}
				categoryName.append(secondName);
			}
			if (categoryName.length() > 0) {
				spu.setCategoryName(categoryName.toString());
			}
		});
	}

}
