package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiscountGoodsMapper extends BaseMapper<DiscountGoods> {

	/**
	 * 原子占用库存：sold_count + qty 不超过 stock 才会更新成功。
	 * 行级锁保证并发下不超卖；返回 0 表示库存不足或该商品未配置限量。
	 */
	@Update("""
		UPDATE discount_goods
		SET sold_count = sold_count + #{quantity}
		WHERE id = #{id} AND del_flag = '0'
		  AND stock IS NOT NULL AND sold_count + #{quantity} <= stock
		""")
	int tryReserveStock(@Param("id") String id, @Param("quantity") int quantity);

	/** 回补库存：取消/退款时把占用数量退回，GREATEST 防止脏数据把已售减成负数。 */
	@Update("""
		UPDATE discount_goods
		SET sold_count = GREATEST(sold_count - #{quantity}, 0)
		WHERE id = #{id}
		""")
	int restoreStock(@Param("id") String id, @Param("quantity") int quantity);

}