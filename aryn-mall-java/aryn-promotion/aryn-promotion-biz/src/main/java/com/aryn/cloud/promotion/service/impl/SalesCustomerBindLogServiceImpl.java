package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBindLog;
import com.aryn.cloud.promotion.mapper.SalesCustomerBindLogMapper;
import com.aryn.cloud.promotion.service.ISalesCustomerBindLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 客户换绑日志服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesCustomerBindLogServiceImpl extends ServiceImpl<SalesCustomerBindLogMapper, SalesCustomerBindLog>
	implements ISalesCustomerBindLogService {

	@Override
	public IPage<SalesCustomerBindLog> selectAdminPage(Page<SalesCustomerBindLog> page, String customerUserId) {
		return this.page(page, Wrappers.<SalesCustomerBindLog>lambdaQuery()
			.eq(customerUserId != null && !customerUserId.isBlank(), SalesCustomerBindLog::getCustomerUserId,
					customerUserId)
			.orderByDesc(SalesCustomerBindLog::getCreateTime));
	}

}
