
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.core.util.SnowflakeIdUtils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.api.enums.OrderArrivalStatusEnum;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderRefundEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.remote.RemoteRefundService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 商城退款单
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
@Slf4j
@Service
@AllArgsConstructor
public class OrderRefundServiceImpl extends ServiceImpl<OrderRefundMapper, OrderRefund> implements IOrderRefundService {

	private final OrderInfoMapper orderInfoMapper;

	private final OrderItemMapper orderItemMapper;

	@DubboReference
	private final RemoteRefundService remoteRefundService;

	private final IOrderConfigService orderConfigService;

	private final OrderDeliveryMapper orderDeliveryMapper;

	private final com.aryn.cloud.order.service.IDeliveryTaskService deliveryTaskService;

	@Override
	public IPage<OrderRefund> adminPage(Page page, OrderRefund orderRefund) {
		return baseMapper.selectAdminPage(page, orderRefund);
	}

	@Override
	public OrderRefund getRefundById(String id) {
		return baseMapper.selectRefundById(id);
	}

	@Override
	public OrderRefund getUserRefundById(String id, String userId) {
		return baseMapper.selectRefundByIdAndUser(id, userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean refund(OrderRefund request) {
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)) {
			throw new ArynBusinessException("订单配置不存在");
		}
		String id = request.getId();
		String refuseReason = request.getRefuseReason();
		String operateStatus = request.getOperateStatus();
		OrderRefund orderRefund = baseMapper.selectById(id);
		if (ObjectUtil.isNull(orderRefund)) {
			throw new ArynBusinessException("退款单不存在");
		}
		OrderItemEntity orderItemEntity = orderItemMapper.selectById(orderRefund.getOrderItemId());
		if (!orderItemEntity.getStatus().equals(OrderItemStatusEnum.AFTER_SALE_PROCESSING.getCode())) {
			throw new ArynBusinessException("订单状态错误");
		}
		// 查询订单
		OrderInfo orderInfo = orderInfoMapper.selectById(orderRefund.getOrderId());
		if (!CommonConstants.YES.equals(orderInfo.getPayStatus())) {
			throw new ArynBusinessException("订单未支付");
		}
		if (MallOrderConstants.OPERATE_STATUS_REJECT.equals(operateStatus)) {
			// 拒绝
			orderRefund.setRefuseReason(refuseReason);
			orderRefund.setStatus(OrderRefundEnum.REVIEW_REJECTED.getCode());
			// 拒绝修改订单项状态 恢复原状态
			// 第三方快递：以发货单是否存在判断是否已发货
			long count = orderDeliveryMapper.selectCount(
					Wrappers.<OrderDelivery>lambdaQuery().eq(OrderDelivery::getOrderId, orderItemEntity.getOrderId()));
			// 商城配送/公司内部配送：不存在发货单，改由配送任务是否已取货判断，
			// 否则已发货的订单项会被错误回退成「待发货」。
			boolean deliveryPicked = false;
			if (count == 0) {
				com.aryn.cloud.order.api.entity.DeliveryTask deliveryTask = deliveryTaskService.getOne(
						Wrappers.<com.aryn.cloud.order.api.entity.DeliveryTask>lambdaQuery()
								.eq(com.aryn.cloud.order.api.entity.DeliveryTask::getOrderId,
										orderItemEntity.getOrderId()));
				if (deliveryTask != null) {
					deliveryPicked = com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.PICKING.getCode()
							.equals(deliveryTask.getStatus())
							|| com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode()
							.equals(deliveryTask.getStatus())
							|| com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.ARRIVED.getCode()
							.equals(deliveryTask.getStatus())
							|| com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.SIGNED.getCode()
							.equals(deliveryTask.getStatus());
				}
			}
			if (count > 0 || deliveryPicked) {
				// 已发货
				orderItemEntity.setStatus(OrderItemStatusEnum.SHIPPED.getCode());
			}
			else {
				orderItemEntity.setStatus(OrderItemStatusEnum.PAID.getCode());
			}
			orderItemMapper.updateById(orderItemEntity);
			this.updateById(orderRefund);
		}
		else if (MallOrderConstants.OPERATE_STATUS_REFUND.equals(operateStatus)) {
			// 退款
			CreateRefundsReqDTO createRefundsReqDTO = new CreateRefundsReqDTO();
			createRefundsReqDTO.setRefundAmount(orderRefund.getRefundAmount());
			createRefundsReqDTO.setRefundTradeNo(orderRefund.getRefundTradeNo());
			createRefundsReqDTO.setNotifyUrl(orderConfig.getNotifyUrl());
			createRefundsReqDTO.setOutTradeNo(orderInfo.getOrderNo());
			createRefundsReqDTO.setTotalAmount(orderInfo.getPaymentPrice());
			createRefundsReqDTO.setUserId(orderInfo.getUserId());
			JSONObject extraParams = new JSONObject();
			extraParams.put("mqNotifyUrl", RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC);
			createRefundsReqDTO.setExtra(extraParams.toJSONString());
			switch (orderInfo.getPaymentType()) {
				// 无需支付
				case MallOrderConstants.PAYMENT_TYPE_0 -> {
					createRefundsReqDTO.setRefundType(PayConstants.FREE_REFUND);
				}
				// 微信支付 = 微信退款
				case MallOrderConstants.PAYMENT_TYPE_1 -> {
					createRefundsReqDTO.setRefundType(PayConstants.WECHAT_REFUND);
				}
				// 支付宝支付 = 支付宝退款
				case MallOrderConstants.PAYMENT_TYPE_2 -> {
					createRefundsReqDTO.setRefundType(PayConstants.ALIPAY_REFUND);
				}
				default -> throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60005.getMsg());
			}
			// 商城配送(3)与公司内部配送(4)共用配送任务：都是先取货后送达，
			// 只判断 way=3 会让 way=4 的已取货订单绕过「先退回仓库再退款」约束。
			if (MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
					|| MallOrderConstants.DELIVERY_WAY_4.equals(orderInfo.getDeliveryWay())) {
				com.aryn.cloud.order.api.entity.DeliveryTask task = deliveryTaskService.getOne(
						Wrappers.<com.aryn.cloud.order.api.entity.DeliveryTask>lambdaQuery()
								.eq(com.aryn.cloud.order.api.entity.DeliveryTask::getOrderId, orderInfo.getId()));
				if (task != null) {
					String taskStatus = task.getStatus();
					boolean picked = com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.PICKING.getCode().equals(taskStatus)
							|| com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(taskStatus)
							|| com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.ARRIVED.getCode().equals(taskStatus)
							|| com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.EXCEPTION.getCode().equals(taskStatus);
					if (picked && !com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.RETURN_PENDING.getCode().equals(taskStatus)
							&& !com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum.CANCELED.getCode().equals(taskStatus)) {
						throw new ArynBusinessException("配送任务已取货，请先在配送任务中确认商品退回仓库后再退款");
					}
				}
			}
			orderRefund.setStatus(OrderRefundEnum.REFUND_COMPLETED.getCode());
			orderRefund.setArrivalStatus(OrderArrivalStatusEnum.REFUNDING.getCode());
			this.updateById(orderRefund);
			remoteRefundService.refunds(createRefundsReqDTO);
		}
		else {
			throw new ArynBusinessException("状态错误");
		}
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public OrderRefund saveRefund(OrderRefund orderRefund) {
		OrderItemEntity orderItemEntity = orderItemMapper.selectById(orderRefund.getOrderItemId());
		if (ObjectUtil.isNull(orderItemEntity)) {
			throw new ArynBusinessException("子订单不存在");
		}
		OrderInfo ownerOrder = orderInfoMapper.selectOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getId, orderItemEntity.getOrderId())
			.eq(OrderInfo::getUserId, orderRefund.getUserId()));
		if (ownerOrder == null) {
			throw new ArynBusinessException("子订单不存在");
		}
		// 只有已支付订单可以退款
		if (OrderItemStatusEnum.PAID.getCode().equals(orderItemEntity.getStatus())
				|| OrderItemStatusEnum.SHIPPED.getCode().equals(orderItemEntity.getStatus())) {
			orderItemEntity.setStatus(OrderItemStatusEnum.AFTER_SALE_PROCESSING.getCode());
			orderItemMapper.updateById(orderItemEntity);
			orderRefund.setRefundTradeNo(SnowflakeIdUtils.refundOrderNo());
			orderRefund.setRefundAmount(orderItemEntity.getPaymentPrice());
			orderRefund.setArrivalStatus(OrderArrivalStatusEnum.NOT_REFUNDED.getCode());
			orderRefund.setOrderId(orderItemEntity.getOrderId());
			orderRefund.setStatus(OrderRefundEnum.PENDING_REVIEW.getCode());
			baseMapper.insert(orderRefund);
		}
		return orderRefund;
	}

	@Override
	public IPage<OrderRefund> getPage(Page page, OrderRefund orderRefund) {
		return baseMapper.selectAdminPage(page, orderRefund);
	}

	/**
	 * 整单自动退款（系统侧发起，跳过售后审核）。
	 *
	 * <p>当前场景：拼团成团失败对已付款成员退款。复用「申请退款 + 审核通过退款」既有链路：
	 * 逐项创建退款单（saveRefund 把明细置为售后处理中）后立即按退款通过执行（refund 调支付网关）。
	 * 按明细幂等：已有退款单、或不在可退状态（待发货/已发货）的明细跳过，支持人工重试。
	 *
	 * <p>任一项退款失败（如配送任务已取货需先退回仓库）整体抛异常回滚，避免半退状态。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean refundWholeOrder(String orderId, String reason) {
		OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
		if (ObjectUtil.isNull(orderInfo)) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!CommonConstants.YES.equals(orderInfo.getPayStatus())) {
			// 未支付订单无需退款：取消链路自行释放占用
			log.info("整单退款跳过: 订单未支付, orderId={}", orderId);
			return false;
		}
		if (OrderStatusEnum.CANCELED.getCode().equals(orderInfo.getStatus())) {
			log.info("整单退款跳过: 订单已取消, orderId={}", orderId);
			return false;
		}
		List<OrderItemEntity> orderItems = orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
			.eq(OrderItemEntity::getOrderId, orderId));
		boolean refundedAny = false;
		for (OrderItemEntity item : orderItems) {
			boolean refundable = OrderItemStatusEnum.PAID.getCode().equals(item.getStatus())
					|| OrderItemStatusEnum.SHIPPED.getCode().equals(item.getStatus());
			if (!refundable) {
				continue;
			}
			long exists = baseMapper.selectCount(Wrappers.<OrderRefund>lambdaQuery()
				.eq(OrderRefund::getOrderItemId, item.getId()));
			if (exists > 0) {
				log.info("整单退款跳过已有退款单的明细, orderId={}, orderItemId={}", orderId, item.getId());
				continue;
			}
			OrderRefund saveRequest = new OrderRefund();
			saveRequest.setOrderItemId(item.getId());
			saveRequest.setUserId(orderInfo.getUserId());
			saveRequest.setRefundReason(reason);
			OrderRefund saved = saveRefund(saveRequest);
			if (ObjectUtil.isNull(saved) || ObjectUtil.isNull(saved.getId())) {
				continue;
			}
			OrderRefund executeRequest = new OrderRefund();
			executeRequest.setId(saved.getId());
			executeRequest.setOperateStatus(MallOrderConstants.OPERATE_STATUS_REFUND);
			refund(executeRequest);
			refundedAny = true;
		}
		if (!refundedAny) {
			log.info("整单退款无可退明细, orderId={}", orderId);
		}
		return refundedAny;
	}

}
