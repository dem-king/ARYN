package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.mapper.DistributionOrderMapper;
import com.aryn.cloud.promotion.service.IDistributionOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 分销订单服务实现
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionOrderServiceImpl extends ServiceImpl<DistributionOrderMapper, DistributionOrder>
	implements IDistributionOrderService {

	private final DistributionOrderMapper distributionOrderMapper;

	@Override
	public IPage<DistributionOrder> selectAdminPage(Page<DistributionOrder> page, DistributionOrder query) {
		return distributionOrderMapper.selectAdminPage(page, query);
	}

}
