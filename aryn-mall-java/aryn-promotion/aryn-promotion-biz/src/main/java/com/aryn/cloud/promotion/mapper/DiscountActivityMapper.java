package com.aryn.cloud.promotion.mapper;

import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiscountActivityMapper extends BaseMapper<DiscountActivity> {

	IPage<DiscountActivity> selectAdminPage(Page page, @Param("query") DiscountActivity activity);

	/**
	 * 全场型活动级限量原子占用：sold_count + qty 不超过 stock 才更新成功。
	 * 行级锁保证并发不超卖；返回 0 表示库存不足或未配置限量。
	 */
	@Update("""
		UPDATE discount_activity
		SET sold_count = sold_count + #{quantity}
		WHERE id = #{id} AND del_flag = '0'
		  AND stock IS NOT NULL AND sold_count + #{quantity} <= stock
		""")
	int tryReserveStock(@Param("id") String id, @Param("quantity") int quantity);

	/** 回补活动级库存（取消/退款），GREATEST 防止负值。 */
	@Update("""
		UPDATE discount_activity
		SET sold_count = GREATEST(sold_count - #{quantity}, 0)
		WHERE id = #{id}
		""")
	int restoreStock(@Param("id") String id, @Param("quantity") int quantity);

}