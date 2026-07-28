package com.aryn.cloud.order.delivery.mapper;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 商城配送任务审计日志 Mapper。 */
@Mapper
public interface OrderDeliveryTaskLogMapper extends BaseMapper<OrderDeliveryTaskLog> {
}
