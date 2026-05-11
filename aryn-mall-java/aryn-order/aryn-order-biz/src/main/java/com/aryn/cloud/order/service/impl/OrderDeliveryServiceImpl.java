
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.OrderDelivery;
import com.aryn.cloud.order.api.entity.OrderDeliveryLogistics;
import com.aryn.cloud.order.api.enums.OrderLogisticsStateEnum;
import com.aryn.cloud.order.mapper.OrderDeliveryLogisticsMapper;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.service.IOrderDeliveryService;
import com.kuaidi100.sdk.response.SubscribePushData;
import com.kuaidi100.sdk.response.SubscribePushParamResp;
import com.kuaidi100.sdk.response.SubscribePushResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发货单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
@Service
@RequiredArgsConstructor
public class OrderDeliveryServiceImpl extends ServiceImpl<OrderDeliveryMapper, OrderDelivery>
		implements IOrderDeliveryService {

	private final OrderDeliveryLogisticsMapper orderDeliveryLogisticsMapper;

	@Override
	public void notifyLogistics(OrderDelivery orderDelivery, SubscribePushParamResp subscribePushParamResp) {
		if ("abort".equals(subscribePushParamResp.getStatus())) {
			orderDelivery.setDeliveryStatus(OrderLogisticsStateEnum.STATUS_ERR.getCode());
			orderDelivery.setRemark(subscribePushParamResp.getMessage());
		}
		else {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			SubscribePushResult subscribePushResult = subscribePushParamResp.getLastResult();
			orderDelivery.setDeliveryStatus(subscribePushResult.getState());
			orderDelivery.setOrderId(subscribePushResult.getIscheck());
			orderDelivery.setIsCheck(subscribePushResult.getIscheck());
			List<OrderDeliveryLogistics> orderDeliveryLogisticsList = orderDeliveryLogisticsMapper
				.selectList(Wrappers.<OrderDeliveryLogistics>lambdaQuery()
					.eq(OrderDeliveryLogistics::getDeliveryId, orderDelivery.getId()));
			Set<String> existingTrackKeys = new HashSet<>();
			if (!CollectionUtils.isEmpty(orderDeliveryLogisticsList)) {
				// 将已有轨迹的时间或内容+时间做成集合
				existingTrackKeys = orderDeliveryLogisticsList.stream()
					.map(track -> track.getLogisticsContext() + "_" + track.getLogisticsTime().format(formatter))
					.collect(Collectors.toSet());
			}

			for (SubscribePushData subscribePushData : subscribePushResult.getData()) {
				LocalDateTime dataTime = LocalDateTime.parse(subscribePushData.getFtime(), formatter);
				String key = subscribePushData.getContext() + "_" + dataTime.format(formatter);
				if (!existingTrackKeys.contains(key)) {
					// 新轨迹保存
					OrderDeliveryLogistics orderDeliveryLogistics = new OrderDeliveryLogistics();
					orderDeliveryLogistics.setDeliveryId(orderDelivery.getId());
					orderDeliveryLogistics.setLogisticsContext(subscribePushData.getContext());
					orderDeliveryLogistics.setLogisticsTime(dataTime);
					orderDeliveryLogisticsMapper.insert(orderDeliveryLogistics);
				}

			}
		}
		this.updateById(orderDelivery);

	}

	@Override
	public OrderDelivery getByOrderId(String orderId) {
		return this.baseMapper.selectByOrderId(orderId);
	}

}
