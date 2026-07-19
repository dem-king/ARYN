
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;

import java.util.List;

/**
 * 购物车
 *
 * @author 雨滴kian
 * @since 2022/3/17 14:56
 */
public interface IShoppingCartService extends IService<ShoppingCart> {

	/**
	 * 购物车列表
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param shoppingCart
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.ShoppingCart>
	 */
	List<ShoppingCart> apiPage(Page page, ShoppingCart shoppingCart);

	/**
	 * 保存
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param shoppingCart
	 * @return: boolean
	 */
	boolean saveShoppingCart(String userId, ShoppingCartCreateDTO request);

	/**
	 * 删除购物车
	 * @param userId 用户id
	 * @param skuIds SKU ID列表
	 * @return
	 */
	boolean clear(String userId, List<String> skuIds);

	/**
	 * 更新购物车
	 * @param userId 用户ID
	 * @param request 修改请求
	 * @return
	 */
	boolean updateShoppingCart(String userId, ShoppingCartUpdateDTO request);

	boolean removeByUserId(String userId, List<String> ids);

}
