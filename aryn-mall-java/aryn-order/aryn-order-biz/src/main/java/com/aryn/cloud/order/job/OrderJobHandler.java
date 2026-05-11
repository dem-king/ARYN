
package com.aryn.cloud.order.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
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
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				// 查询待支付状态并且已超时的订单
				List<OrderInfo> orderList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
					.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
					.lt(OrderInfo::getCreateTime, LocalDateTime.now().minusMinutes(30)));
				orderList.forEach(orderInfoService::cancelOrder);
				ArynTenantContextHolder.removeTenantId();
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
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				// 查询订单配置
				OrderConfig orderConfig = orderConfigService.getConfig();
				if (Objects.isNull(orderConfig)) {
					return;
				}
				if (Objects.isNull(orderConfig.getOrderAutoConfirmDays())) {
					return;
				}
				// 查询未确认收货订单
				List<OrderInfo> orderList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
					.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
					.lt(OrderInfo::getDeliverTime,
							LocalDateTime.now().minusDays(orderConfig.getOrderAutoConfirmDays())));
				orderList.forEach(orderInfoService::receiveOrder);
				ArynTenantContextHolder.removeTenantId();
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
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				// 查询订单配置
				OrderConfig orderConfig = orderConfigService.getConfig();
				if (Objects.isNull(orderConfig)) {
					return;
				}
				if (Objects.isNull(orderConfig.getOrderAutoCommentDays())) {
					return;
				}
				// 查询未确认收货订单
				List<OrderInfo> orderList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
					.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
					.lt(OrderInfo::getReceiverTime,
							LocalDateTime.now().minusDays(orderConfig.getOrderAutoCommentDays())));
				orderList.forEach(orderInfoService::autoAppraiseOrder);
				ArynTenantContextHolder.removeTenantId();
			});
		}
	}

}
