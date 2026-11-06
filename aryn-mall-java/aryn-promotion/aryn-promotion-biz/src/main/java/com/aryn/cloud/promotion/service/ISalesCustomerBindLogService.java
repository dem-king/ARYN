package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBindLog;

/**
 * 客户换绑日志服务（只追加）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesCustomerBindLogService extends IService<SalesCustomerBindLog> {

	/**
	 * 换绑日志分页
	 * @param page 分页参数
	 * @param customerUserId 客户会员ID（可空）
	 * @return 分页结果
	 */
	IPage<SalesCustomerBindLog> selectAdminPage(Page<SalesCustomerBindLog> page, String customerUserId);

}
