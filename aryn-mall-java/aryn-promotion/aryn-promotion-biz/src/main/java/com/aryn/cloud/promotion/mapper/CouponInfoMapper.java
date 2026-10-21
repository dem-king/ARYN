
package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.io.Serializable;
import java.util.List;

@Mapper
public interface CouponInfoMapper extends BaseMapper<CouponInfo> {

	/**
	 * 分页查询优惠券
	 * @param page
	 * @param couponInfo
	 * @param couponGoods
	 * @param couponUser
	 * @return
	 */
	IPage<CouponInfo> selectCouponPage(Page page, @Param("query") CouponInfo couponInfo,
			@Param("couponGoods") CouponGoods couponGoods, @Param("couponUser") CouponUser couponUser);

	/**
	 * 按ID批量查询优惠券（C 端口径：领取时间窗/状态/库存或本人已领取，与列表页可见性一致）
	 */
	List<CouponInfo> selectCouponByIds(@Param("ids") List<String> ids, @Param("couponUser") CouponUser couponUser);

	CouponInfo selectCouponById(Serializable id);

	IPage<CouponInfo> selectAdminPage(Page page, @Param("query") CouponInfo couponInfo);

	@Update("""
			UPDATE coupon_info
			SET remain_num = remain_num - 1, assign_count = COALESCE(assign_count, 0) + 1
			WHERE id = #{couponId} AND del_flag = '0' AND remain_num > 0
			""")
	int allocateOne(@Param("couponId") String couponId);

}
