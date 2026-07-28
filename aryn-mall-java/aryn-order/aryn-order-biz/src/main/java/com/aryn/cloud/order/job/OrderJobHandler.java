
package com.aryn.cloud.order.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 订单相关定时任务
 *
 * @author 雨滴kian
 * @date 2022/11/02
 */
@Component
@RequiredArgsConstructor
public class OrderJobHandler {

	private final IOrderInfoService orderInfoService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	private final IOrderConfigService orderConfigService;

	private final OrderInfoMapper orderInfoMapper;

	private final OrderDeliveryTaskMapper deliveryTaskMapper;

	/**
	 * 扫描超时未支付订单
	 *
	 * @author 雨滴kian
	 * @date 2022/11/02
	 * @return: void
	 */
	@XxlJob("orderCancelJobHandler")
	public void orderCancelJobHandler() throws Exception {
		XxlJobHelper.log("扫描超时未支付订单, orderCancelJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					List<OrderInfo> orderList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
						.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
						.eq(OrderInfo::getPayStatus, CommonConstants.NO)
						.lt(OrderInfo::getCreateTime, LocalDateTime.now().minusMinutes(30)));
					orderList.forEach(orderInfoService::cancelOrder);
				}
				finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
		// default success
	}

	/**
	 * 扫描超时未确认收货订单
	 *
	 * @author 雨滴kian
	 * @date 2022/11/02
	 * @return: void
	 */
	@XxlJob("orderReceiverJobHandler")
	public void orderReceiverJobHandler() throws Exception {
		XxlJobHelper.log("扫描超时未确认收货订单, orderReceiverJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					OrderConfig orderConfig = orderConfigService.getConfig();
					if (Objects.isNull(orderConfig) || Objects.isNull(orderConfig.getOrderAutoConfirmDays())) {
						return;
					}
					LocalDateTime deadline = LocalDateTime.now().minusDays(orderConfig.getOrderAutoConfirmDays());
					List<OrderInfo> expressOrders = orderInfoMapper
						.selectExpressAutoConfirmOrders(sysTenant.getId(), deadline);
					List<OrderInfo> mallDeliveryOrders = deliveryTaskMapper
						.selectMallDeliveryAutoConfirmOrders(sysTenant.getId(), deadline);
					if (!CollectionUtils.isEmpty(expressOrders)) {
						expressOrders.forEach(orderInfoService::receiveOrder);
					}
					if (!CollectionUtils.isEmpty(mallDeliveryOrders)) {
						mallDeliveryOrders.forEach(orderInfoService::receiveOrder);
					}
				}
				finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
	}

	/**
	 * 扫描超时未评价订单
	 *
	 * @author 雨滴kian
	 * @date 2022/11/02
	 * @return: void
	 */
	@XxlJob("orderAppraiseJobHandler")
	public void orderAppraiseJobHandler() throws Exception {
		XxlJobHelper.log("扫描超时未评价订单, orderAppraiseJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					OrderConfig orderConfig = orderConfigService.getConfig();
					if (Objects.isNull(orderConfig) || Objects.isNull(orderConfig.getOrderAutoCommentDays())) {
						return;
					}
					List<OrderInfo> orderList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
						.eq(OrderInfo::getStatus, OrderStatusEnum.COMPLETED.getCode())
						.eq(OrderInfo::getAppraiseStatus, CommonConstants.NO)
						.lt(OrderInfo::getReceiverTime,
								LocalDateTime.now().minusDays(orderConfig.getOrderAutoCommentDays())));
					orderList.forEach(orderInfoService::autoAppraiseOrder);
				}
				finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
	}

}
