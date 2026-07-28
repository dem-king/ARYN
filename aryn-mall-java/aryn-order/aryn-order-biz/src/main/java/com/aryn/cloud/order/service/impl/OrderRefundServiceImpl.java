
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
import com.aryn.cloud.order.delivery.service.DeliveryRefundBoundaryService;
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
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 商城退款单
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
@Service
@AllArgsConstructor
public class OrderRefundServiceImpl extends ServiceImpl<OrderRefundMapper, OrderRefund> implements IOrderRefundService {

	private final OrderInfoMapper orderInfoMapper;

	private final OrderItemMapper orderItemMapper;

	@DubboReference
	private final RemoteRefundService remoteRefundService;

	private final IOrderConfigService orderConfigService;

	private final OrderDeliveryMapper orderDeliveryMapper;

	private final DeliveryRefundBoundaryService deliveryRefundBoundaryService;

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
			// 查询是否有发货单
			long count = orderDeliveryMapper.selectCount(
					Wrappers.<OrderDelivery>lambdaQuery().eq(OrderDelivery::getOrderId, orderItemEntity.getOrderId()));
			if (count > 0) {
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
			deliveryRefundBoundaryService.requireRefundRelease(orderInfo);
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
			deliveryRefundBoundaryService.onRefundRequested(ownerOrder);
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

}
