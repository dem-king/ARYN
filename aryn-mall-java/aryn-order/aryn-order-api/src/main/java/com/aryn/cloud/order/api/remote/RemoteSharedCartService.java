package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.entity.SharedCart;

/**
 * 共享购物车远程接口：供管理端或其他 biz 模块查询整车信息。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface RemoteSharedCartService {

	/**
	 * 按租户查询共享购物车。
	 */
	SharedCart getSharedCart(String tenantId, String cartId);

}
