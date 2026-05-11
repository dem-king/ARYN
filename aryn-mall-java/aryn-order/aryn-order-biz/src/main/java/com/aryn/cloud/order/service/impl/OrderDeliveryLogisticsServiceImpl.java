
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.OrderDeliveryLogistics;
import com.aryn.cloud.order.mapper.OrderDeliveryLogisticsMapper;
import com.aryn.cloud.order.service.IOrderDeliveryLogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 发货单物流轨迹
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
@Service
@RequiredArgsConstructor
public class OrderDeliveryLogisticsServiceImpl extends ServiceImpl<OrderDeliveryLogisticsMapper, OrderDeliveryLogistics>
		implements IOrderDeliveryLogisticsService {

}
