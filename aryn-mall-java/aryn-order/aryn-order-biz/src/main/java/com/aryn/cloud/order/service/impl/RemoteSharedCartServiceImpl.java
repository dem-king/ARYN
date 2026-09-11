package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.remote.RemoteSharedCartService;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/** 共享购物车远程实现：供管理端等其他模块查询整车信息。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSharedCartServiceImpl implements RemoteSharedCartService {

	private final SharedCartMapper sharedCartMapper;

	@Override
	public SharedCart getSharedCart(String tenantId, String cartId) {
		return sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
			.eq(SharedCart::getTenantId, tenantId)
			.eq(SharedCart::getId, cartId));
	}

}
