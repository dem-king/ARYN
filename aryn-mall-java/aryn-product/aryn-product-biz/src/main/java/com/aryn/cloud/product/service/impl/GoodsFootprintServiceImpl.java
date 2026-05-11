
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsFootprint;
import com.aryn.cloud.product.api.vo.GoodsFootprintVO;
import com.aryn.cloud.product.mapper.GoodsFootprintMapper;
import com.aryn.cloud.product.service.IGoodsFootprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户足迹
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Service
@RequiredArgsConstructor
public class GoodsFootprintServiceImpl extends ServiceImpl<GoodsFootprintMapper, GoodsFootprint>
		implements IGoodsFootprintService {

	@Override
	public IPage<GoodsFootprintVO> apiPage(Page page, LambdaQueryWrapper<GoodsFootprint> lambdaQuery) {
		return baseMapper.apiPage(page, lambdaQuery.getEntity());
	}

}
