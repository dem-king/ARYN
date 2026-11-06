package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPackItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 券包关联的优惠券模板
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Mapper
public interface DistributionCouponPackItemMapper extends BaseMapper<DistributionCouponPackItem> {

	/**
	 * 恢复一条曾被逻辑删除的券包明细（唯一键 tenant_id + pack_id + coupon_info_id 仍占用原行，
	 * 无法直接 insert，只能把 del_flag 改回 '0' 并覆盖数量）。
	 * @param tenantId 租户ID
	 * @param packId 券包ID
	 * @param couponInfoId 优惠券模板ID
	 * @param quantity 数量
	 * @return 恢复成功返回 1，无历史行返回 0
	 */
	@Update("""
			UPDATE distribution_coupon_pack_item
			SET del_flag = '0', quantity = #{quantity}
			WHERE tenant_id = #{tenantId}
			  AND pack_id = #{packId}
			  AND coupon_info_id = #{couponInfoId}
			""")
	int restoreDeletedItem(@Param("tenantId") String tenantId, @Param("packId") String packId,
			@Param("couponInfoId") String couponInfoId, @Param("quantity") Integer quantity);

}
