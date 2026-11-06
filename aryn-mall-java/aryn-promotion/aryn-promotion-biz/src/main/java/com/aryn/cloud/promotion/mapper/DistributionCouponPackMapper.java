package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 分销佣金兑换券包
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Mapper
public interface DistributionCouponPackMapper extends BaseMapper<DistributionCouponPack> {

	/**
	 * 原子扣减有限库存（stock 为 NULL 表示不限，不参与扣减）。
	 * @param packId 券包ID
	 * @return 扣减成功返回 1，库存不足或券包不存在返回 0
	 */
	@Update("""
			UPDATE distribution_coupon_pack
			SET stock = stock - 1
			WHERE id = #{packId}
			  AND del_flag = '0'
			  AND stock IS NOT NULL
			  AND stock > 0
			""")
	int deductStock(@Param("packId") String packId);

}
