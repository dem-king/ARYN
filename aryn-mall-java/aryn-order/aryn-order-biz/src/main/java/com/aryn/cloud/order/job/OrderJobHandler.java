
package com.aryn.cloud.order.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.support.OrderCancelTimeoutHelper;
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

	private final com.aryn.cloud.order.service.IDeliveryTaskService deliveryTaskService;

	private final com.aryn.cloud.order.service.impl.OrderPriceComputeService orderPriceComputeService;

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
					// 与下单时的延迟取消消息共用同一级别口径：MQ 发送失败的单据由本扫描按相同时限兜底
					int timeoutMinutes = OrderCancelTimeoutHelper.delayLevelToMinutes(
							OrderCancelTimeoutHelper.resolveDelayLevel(orderConfigService.getConfig()));
					List<OrderInfo> orderList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
						.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
						.eq(OrderInfo::getPayStatus, CommonConstants.NO)
						.lt(OrderInfo::getCreateTime, LocalDateTime.now().minusMinutes(timeoutMinutes)));
					orderList.forEach(order -> orderInfoService.cancelOrder(order,
							com.aryn.cloud.order.service.OrderStaffNotifyService.CancelSource.TIMEOUT));
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
					LocalDateTime cutoff = LocalDateTime.now().minusDays(orderConfig.getOrderAutoConfirmDays());
					// 第三方快递等非任务驱动配送：按 order_info.deliver_time 起算
					List<OrderInfo> normalList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
						.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())
						.notIn(OrderInfo::getDeliveryWay, List.of(
								com.aryn.cloud.order.api.constant.MallOrderConstants.DELIVERY_WAY_3,
								com.aryn.cloud.order.api.constant.MallOrderConstants.DELIVERY_WAY_4))
						.lt(OrderInfo::getDeliverTime, cutoff));
					normalList.forEach(orderInfoService::receiveOrder);
					// 商城配送与公司内部配送：按 delivery_task.arrive_time（送达时间）起算
					List<OrderInfo> deliveryList = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
						.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())
						.in(OrderInfo::getDeliveryWay, List.of(
								com.aryn.cloud.order.api.constant.MallOrderConstants.DELIVERY_WAY_3,
								com.aryn.cloud.order.api.constant.MallOrderConstants.DELIVERY_WAY_4)));
					for (OrderInfo order : deliveryList) {
						com.aryn.cloud.order.api.entity.DeliveryTask task = deliveryTaskService.getOne(
								Wrappers.<com.aryn.cloud.order.api.entity.DeliveryTask>lambdaQuery()
										.eq(com.aryn.cloud.order.api.entity.DeliveryTask::getOrderId, order.getId()));
						if (task != null && task.getArriveTime() != null && task.getArriveTime().isBefore(cutoff)) {
							orderInfoService.receiveOrder(order);
						}
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

	/**
	 * 营销锁释放补偿：取消/超时关单时释放失败仅留日志，
	 * 本任务扫描近 7 天已取消订单幂等补释放（release 本身幂等，无锁订单空转），
	 * 防止新人价等在途锁泄漏冻住用户营销资格。
	 */
	@XxlJob("promotionLockCompensateJobHandler")
	public void promotionLockCompensateJobHandler() throws Exception {
		XxlJobHelper.log("扫描已取消订单补偿释放营销锁, promotionLockCompensateJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					List<OrderInfo> canceled = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
						.eq(OrderInfo::getStatus, OrderStatusEnum.CANCELED.getCode())
						.ge(OrderInfo::getCancelTime, LocalDateTime.now().minusDays(7))
						.last("LIMIT 200"));
					for (OrderInfo order : canceled) {
						try {
							orderPriceComputeService.releasePromotion(
									org.springframework.util.StringUtils.hasText(order.getTenantId())
											? order.getTenantId() : sysTenant.getId(),
									order.getId(), "COMPENSATE");
						}
						catch (Exception ex) {
							XxlJobHelper.log("订单[" + order.getId() + "]营销锁补偿释放失败，下次扫描重试: "
									+ ex.getMessage());
						}
					}
				}
				finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
	}

}
