
package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.vo.CouponUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

}
