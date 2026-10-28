
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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
		WHERE id = #{id} AND user_id = #{userId} AND del_flag = '0'
		""")
	int incrementQuantityById(@Param("userId") String userId, @Param("id") String id,
			@Param("quantity") int quantity);

	/**
	 * 一批 SKU 在购物车中的去重人数（秒杀会场「N 人在抢」）。
	 *
	 * <p>与 {@link com.aryn.cloud.order.api.remote.RemoteShoppingCartService#countDistinctSkuUsersSince}
	 * 同口径：租户内、未删除、加购时间不早于 since 的去重 user_id；只读。
	 * SQL 在 XML（countDistinctSkuUsersSince），skuIds 为空时强制 1=0，不产生全表统计。
	 */
	int countDistinctSkuUsersSince(@Param("tenantId") String tenantId, @Param("skuIds") List<String> skuIds,
			@Param("since") LocalDateTime since);

}
