
package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.vo.CouponUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface CouponUserMapper extends BaseMapper<CouponUser> {

	IPage<CouponUser> selectAdminPage(Page page, @Param("query") CouponUser couponUser);

	IPage<CouponUser> selectApiPage(Page page, @Param("query") CouponUser couponUser);

	List<CouponUser> selectExpireCouponList();

	/**
	 * 根据优惠券id查询优惠券领取数量
	 * @param couponIds
	 * @return
	 */
	List<CouponUserVO> selectCountByCouponIds(@Param("couponIds") String[] couponIds);

	@Insert("""
			INSERT INTO coupon_user
			(id, coupon_id, user_id, status, received_time, validat_time, del_flag, tenant_id, source_type, source_id)
			VALUES
			(#{id}, #{couponId}, #{userId}, #{status}, #{receivedTime}, #{validatTime}, '0', #{tenantId}, #{sourceType}, #{sourceId})
			ON DUPLICATE KEY UPDATE id = id
			""")
	int insertSourceIfAbsent(CouponUser couponUser);

}
