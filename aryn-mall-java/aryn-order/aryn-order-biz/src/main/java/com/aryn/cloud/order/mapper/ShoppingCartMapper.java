
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 购物车
 *
 * @author 雨滴kian
 * @since 2022/3/17 14:44
 */
@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

	/**
	 * 购物车列表
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param shoppingCart
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.ShoppingCart>
	 */
	IPage<ShoppingCart> selectApiPage(Page page, @Param("query") ShoppingCart shoppingCart);

	@Update("""
		UPDATE shopping_cart
		SET quantity = quantity + #{quantity}, update_time = NOW()
		WHERE user_id = #{userId} AND sku_id = #{skuId} AND del_flag = '0'
		""")
	int incrementQuantity(@Param("userId") String userId, @Param("skuId") String skuId,
			@Param("quantity") int quantity);

}
