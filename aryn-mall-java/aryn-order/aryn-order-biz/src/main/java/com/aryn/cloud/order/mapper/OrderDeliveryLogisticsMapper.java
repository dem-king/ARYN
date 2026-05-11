
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.OrderDeliveryLogistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发货单物流轨迹
 *
 * @author 雨滴kian
 * @since 2025/4/27
 */
@Mapper
public interface OrderDeliveryLogisticsMapper extends BaseMapper<OrderDeliveryLogistics> {

	List<OrderDeliveryLogistics> selectByOrDeliveryId(@Param("deliveryId") String deliveryId);

}
