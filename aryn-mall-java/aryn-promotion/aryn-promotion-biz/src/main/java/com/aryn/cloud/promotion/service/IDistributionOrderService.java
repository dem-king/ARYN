package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;

/**
 * 分销订单服务
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
public interface IDistributionOrderService extends IService<DistributionOrder> {

	/**
	 * 管理端分页查询分销订单
	 *
	 * @param page  分页参数
	 * @param query 查询条件
	 * @return 分页结果
	 */
	IPage<DistributionOrder> selectAdminPage(Page<DistributionOrder> page, DistributionOrder query);

}
