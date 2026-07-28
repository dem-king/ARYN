package com.aryn.cloud.order.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商城配送范围 Mapper。
 */
@Mapper
public interface OrderDeliveryAreaMapper extends BaseMapper<OrderDeliveryArea> {

	OrderDeliveryArea selectEnabledByScope(@Param("scopeLevel") String scopeLevel,
			@Param("areaCode") String areaCode);

}
