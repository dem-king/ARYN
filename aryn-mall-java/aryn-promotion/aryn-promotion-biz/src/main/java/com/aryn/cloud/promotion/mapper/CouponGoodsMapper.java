
package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponGoodsMapper extends BaseMapper<CouponGoods> {

	List<CouponGoods> selectByCouponId(@Param("couponId") String couponId);

}
