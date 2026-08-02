
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.core.util.SnowflakeIdUtils;
import com.aryn.cloud.common.logistics.util.Kuaidi100Utils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.*;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderDelivery;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderLogisticsStateEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.api.vo.OrderStatisticsVO;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
@Service
@RequiredArgsConstructor
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {

	private final IOrderConfigService orderConfigService;

	private final Kuaidi100Utils kuaidi100Utils;

	private final OrderItemMapper orderItemMapper;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	private final IOrderItemService orderItemService;

	private final IShoppingCartService shoppingCartService;

	@DubboReference
	private final RemotePayService remotePayService;

	private final ApplicationEventPublisher applicationEventPublisher;

	private final OrderPriceComputeService orderPriceComputeService;

	private final OrderWxDeliveryService orderWxDeliveryService;

	private final OrderStatisticsQueryService orderStatisticsQueryService;

	private final OrderAppraiseService orderAppraiseService;

	@DubboReference
	private final RemoteUserAddressService remoteUserAddressService;

	private final OrderDeliveryMapper orderDeliveryMapper;

	private final OrderRefundMapper orderRefundMapper;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	private final RocketMQTemplate rocketMQTemplate;

	private final CallbackPrefixProperties callbackPrefixProperties;

	private final com.aryn.cloud.order.service.IDeliveryTaskService deliveryTaskService;

	private final com.aryn.cloud.order.service.IDeliveryAreaService deliveryAreaService;

	@Override
	public IPage<OrderInfo> adminPage(Page page, OrderInfo orderInfo) {
		return baseMapper.selectAdminPage(page, orderInfo);
	}

	@Override
	public OrderInfo getOrderById(String id) {
		OrderInfo orderInfo = baseMapper.selectOrderById(id);
		if (Objects.isNull(orderInfo)) {
			return null;
		}
		if (!CollectionUtils.isEmpty(orderInfo.getOrderItemList())) {
			orderInfo.getOrderItemList().forEach(orderItem -> {
				if (!OrderItemStatusEnum.SHIPPED.getCode().equals(orderItem.getStatus())
						&& !OrderItemStatusEnum.PAID.getCode().equals(orderItem.getStatus())) {
					// 查询最近一笔退款单
					orderItem.setOrderRefund(orderRefundMapper.selectByOrderItemId(orderItem.getId()));
				}
			});
		}
		return orderInfo;
	}

	@Override
	public OrderInfo getUserOrderById(String id, String userId) {
		OrderInfo orderInfo = baseMapper.selectOrderByIdAndUser(id, userId);
		return enrichOrderRefund(orderInfo);
	}

	@Override
	public OrderInfo getUserOrderByOrderNo(String orderNo, String userId) {
		return getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getOrderNo, orderNo)
			.eq(OrderInfo::getUserId, userId));
	}

	private OrderInfo enrichOrderRefund(OrderInfo orderInfo) {
		if (Objects.isNull(orderInfo)) {
			return null;
		}
		if (!CollectionUtils.isEmpty(orderInfo.getOrderItemList())) {
			orderInfo.getOrderItemList().forEach(orderItem -> {
				if (!OrderItemStatusEnum.SHIPPED.getCode().equals(orderItem.getStatus())
						&& !OrderItemStatusEnum.PAID.getCode().equals(orderItem.getStatus())) {
					orderItem.setOrderRefund(orderRefundMapper.selectByOrderItemId(orderItem.getId()));
				}
			});
		}
		return orderInfo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deliverOrder(OrderDeliveryDTO request) {
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)) {
			throw new ArynBusinessException("订单配置不存在");
		}
		OrderInfo orderInfo = baseMapper.selectById(request.getOrderId());
		if (ObjectUtil.isNull(orderInfo)) {
			throw new ArynBusinessException("订单不存在");
		}
		List<OrderItemEntity> orderItemEntityList = orderItemService
			.list(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, orderInfo.getId()));
		if (CollectionUtils.isEmpty(orderItemEntityList)) {
			throw new ArynBusinessException("订单商品不存在");
		}
		orderItemEntityList.forEach(orderItem -> {
			if (!orderItem.getStatus().equals(OrderItemStatusEnum.PAID.getCode())) {
				throw new ArynBusinessException("状态错误");
			}
			orderItem.setStatus(OrderItemStatusEnum.SHIPPED.getCode());
		});
		// 创建发货单
		OrderDelivery orderDelivery = new OrderDelivery();
		orderDelivery.setDeliveryNo(IdUtil.getSnowflakeNextIdStr());
		orderDelivery.setDeliverTime(LocalDateTime.now());
		orderDelivery.setOrderId(orderInfo.getId());
		orderDelivery.setDeliveryStatus(OrderLogisticsStateEnum.STATUS_1.getCode());
		orderDelivery.setDeliverTime(LocalDateTime.now());
		orderDelivery.setLogisticsCompanyCode(request.getLogisticsCompanyCode());
		orderDelivery.setLogisticsCompanyName(request.getLogisticsCompanyName());
		orderDelivery.setLogisticsNo(request.getLogisticsNo());
		orderDelivery.setRemark("订单已发货，物流单号：" + request.getLogisticsNo());
		if (orderDeliveryMapper.insert(orderDelivery) <= 0) {
			throw new ArynBusinessException("创建发货单失败");
		}
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode());
		orderInfo.setDeliverTime(LocalDateTime.now());
		// 更新订单
		if (baseMapper.updateById(orderInfo) <= 0) {
			throw new ArynBusinessException("更新订单状态失败");
		}
		// 更新订单项状态
		if (!orderItemService.updateBatchById(orderItemEntityList)) {
			throw new ArynBusinessException("更新订单状态失败");
		}
		String logisticsUrl = String.format(MallOrderConstants.NOTIFY_LOGISTICS_URL,
				callbackPrefixProperties.getLogistics(), orderDelivery.getId(), orderDelivery.getTenantId());

		kuaidi100Utils.poll(orderDelivery.getLogisticsCompanyCode(), orderDelivery.getLogisticsNo(),
				orderInfo.getRecipientProvince() + orderInfo.getRecipientCity() + orderInfo.getRecipientArea(),
				orderConfig.getKuaidi100AppKey(), orderConfig.getNotifyUrl() + logisticsUrl,
				orderInfo.getRecipientPhone(), orderDelivery.getDeliveryNo());
		orderWxDeliveryService.uploadDeliveryInfoOnDeliver(orderInfo, orderItemEntityList);

		return true;
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public String cancelOrder(OrderInfo orderInfo) {
		int updated = baseMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.set(OrderInfo::getStatus, OrderStatusEnum.CANCELED.getCode())
			.set(OrderInfo::getCancelTime, LocalDateTime.now()));
		if (updated == 0) {
			throw new ArynBusinessException("订单状态已变化，无法取消");
		}

		if (StringUtils.hasText(orderInfo.getCouponUserId())) {
			if (!remoteCouponUserService.releaseCoupon(orderInfo.getCouponUserId(), orderInfo.getId())) {
				throw new ArynBusinessException("订单优惠券释放失败");
			}
		}
		List<OrderItemEntity> orderItemEntityList = orderItemMapper
			.selectList(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, orderInfo.getId()));
		List<GoodsSkuStockReqDTO> stockRequests = orderItemEntityList.stream().map(orderItem -> {
			GoodsSkuStockReqDTO request = new GoodsSkuStockReqDTO();
			request.setStockNum(orderItem.getBuyQuantity());
			request.setSkuId(orderItem.getSkuId());
			request.setSpuId(orderItem.getSpuId());
			return request;
		}).toList();
		remoteGoodsSkuService.rollbackStock(stockRequests);
		return orderInfo.getId();
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public String cancelUserOrder(String id, String userId) {
		OrderInfo orderInfo = getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getId, id)
			.eq(OrderInfo::getUserId, userId));
		if (orderInfo == null) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60003.getCode(),
					MallErrorCodeEnum.ERROR_60003.getMsg());
		}
		return cancelOrder(orderInfo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteUserOrder(String id, String userId) {
		OrderInfo orderInfo = getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getId, id)
			.eq(OrderInfo::getUserId, userId));
		if (orderInfo == null) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60003.getCode(),
					MallErrorCodeEnum.ERROR_60003.getMsg());
		}
		if (!OrderStatusEnum.CANCELED.getCode().equals(orderInfo.getStatus())
				|| !CommonConstants.NO.equals(orderInfo.getPayStatus())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60006.getCode(),
					MallErrorCodeEnum.ERROR_60006.getMsg());
		}
		orderItemService.remove(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, id));
		return remove(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getId, id)
			.eq(OrderInfo::getUserId, userId)
			.eq(OrderInfo::getStatus, OrderStatusEnum.CANCELED.getCode())
			.eq(OrderInfo::getPayStatus, CommonConstants.NO));
	}

	@Override
	public BigDecimal getPaySumStatistics(OrderInfoDTO orderInfoDTO) {
		return orderStatisticsQueryService.getPaySumStatistics(orderInfoDTO);
	}

	@Override
	public IPage<OrderInfo> apiPage(Page page, OrderInfo orderInfo) {
		return baseMapper.selectApiPage(page, orderInfo);
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public OrderInfo createOrder(CreateOrderDTO createOrderDTO) {
		if (!StringUtils.hasText(createOrderDTO.getRequestId())) {
			createOrderDTO.setRequestId(UUID.randomUUID().toString());
		}
		OrderInfo existingOrder = getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getUserId, createOrderDTO.getUserId())
			.eq(OrderInfo::getRequestId, createOrderDTO.getRequestId()));
		if (existingOrder != null) {
			return existingOrder;
		}

		// 查询用户信息
		UserInfoVO userInfo = remoteMallUserService.getUserById(createOrderDTO.getUserId());
		if (Objects.isNull(userInfo)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50001.getMsg());
		}
		List<String> skuIds = createOrderDTO.getSkuReqList()
			.stream()
			.map(CreateOrderSkuReqDTO::getSkuId)
			.distinct()
			.toList();
		if (skuIds.size() != createOrderDTO.getSkuReqList().size()) {
			throw new ArynBusinessException("订单商品不能包含重复SKU");
		}
		// 查询购买商品
		List<GoodsSku> goodsSkuList = remoteGoodsSkuService.getBySkuIds(skuIds);
		if (CollectionUtils.isEmpty(goodsSkuList) || goodsSkuList.size() != skuIds.size()) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}

		// 生成订单
		OrderInfo orderInfo = generateOrder(createOrderDTO);
		// 生成订单商品
		List<OrderItemEntity> orderItemEntityList = orderPriceComputeService.generateOrderItems(goodsSkuList, createOrderDTO.getSkuReqList());

		orderPriceComputeService.computeOrderPrice(orderInfo, orderItemEntityList);
		MemberBenefitsVO memberBenefits = remoteMallUserService.getMemberBenefits(createOrderDTO.getUserId());
		orderPriceComputeService.orderMemberBenefitHandler(orderInfo, orderItemEntityList, memberBenefits);
		orderPriceComputeService.orderCouponHandler(orderInfo, orderItemEntityList);

		// 5. 物流运费计算（普通快递和商城配送均需收货地址与运费）
		if (MallOrderConstants.DELIVERY_WAY_1.equals(orderInfo.getDeliveryWay())
				|| MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())) {
			if (!StringUtils.hasText(createOrderDTO.getUserAddressId())) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50002.getCode(),
						MallErrorCodeEnum.ERROR_50002.getMsg());
			}
			// 查询用户收货地址
			UserAddress userAddress = remoteUserAddressService.getById(createOrderDTO.getUserAddressId(),
					createOrderDTO.getUserId());
			if (ObjectUtil.isNull(userAddress)) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50002.getCode(),
						MallErrorCodeEnum.ERROR_50002.getMsg());
			}
			orderInfo.setRecipientName(userAddress.getRecipientName());
			orderInfo.setRecipientPhone(userAddress.getTelephone());
			orderInfo.setRecipientProvince(userAddress.getProvinceName());
			orderInfo.setRecipientCity(userAddress.getCityName());
			orderInfo.setRecipientArea(userAddress.getAreaName());
			orderInfo.setRecipientProvinceCode(userAddress.getProvinceCode());
			orderInfo.setRecipientCityCode(userAddress.getCityCode());
			orderInfo.setRecipientAreaCode(userAddress.getAreaCode());
			orderInfo.setRecipientAddress(userAddress.getDetailAddress());
			orderPriceComputeService.orderFreightHandler(orderInfo, orderItemEntityList, goodsSkuList,
					memberBenefits != null && memberBenefits.isFreeShipping());
			if (MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
					&& !deliveryAreaService.isAddressInDeliveryArea(userAddress.getProvinceCode(),
							userAddress.getCityCode(), userAddress.getAreaCode())) {
				throw new ArynBusinessException("当前收货地址不在商城配送范围内");
			}
		}
		// 创建订单
		try {
			if (!super.save(orderInfo)) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60007.getCode(),
						MallErrorCodeEnum.ERROR_60007.getMsg());
			}
		}
		catch (DuplicateKeyException exception) {
			OrderInfo duplicateOrder = getOne(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getUserId, createOrderDTO.getUserId())
				.eq(OrderInfo::getRequestId, createOrderDTO.getRequestId()));
			if (duplicateOrder != null) {
				return duplicateOrder;
			}
			throw exception;
		}
		if (StringUtils.hasText(orderInfo.getCouponUserId())
				&& !remoteCouponUserService.reserveCoupon(orderInfo.getCouponUserId(), orderInfo.getUserId(),
						orderInfo.getId())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60061.getCode(),
					MallErrorCodeEnum.ERROR_60061.getMsg());
		}
		orderPriceComputeService.orderStockHandler(goodsSkuList, orderItemEntityList);
		orderItemEntityList.forEach(orderItem -> orderItem.setOrderId(orderInfo.getId()));
		if (!orderItemService.saveBatch(orderItemEntityList)) {
			throw new ArynBusinessException("订单商品保存失败");
		}
		if (MallOrderConstants.ORDER_CREATE_WAY_1.equals(createOrderDTO.getCreateWay())) {
			shoppingCartService.clear(orderInfo.getUserId(), orderItemEntityList.stream()
				.map(OrderItemEntity::getSkuId)
				.distinct()
				.toList());
		}
		applicationEventPublisher.publishEvent(
				new ArynOrderCreateAfterEvent(this, orderInfo, orderItemEntityList, createOrderDTO.getCreateWay()));
		return orderInfo;
	}

	private OrderInfo generateOrder(CreateOrderDTO createOrderDTO) {
		OrderInfo orderInfo = new OrderInfo();
		BeanUtil.copyProperties(createOrderDTO, orderInfo);
		orderInfo.setId(IdUtil.getSnowflakeNextIdStr());
		orderInfo.setAppraiseStatus(CommonConstants.NO);
		orderInfo.setOrderNo(SnowflakeIdUtils.orderNo());
		orderInfo.setPaymentPrice(BigDecimal.ZERO);
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_PAYMENT.getCode());
		orderInfo.setTotalPrice(BigDecimal.ZERO);
		orderInfo.setFreightPrice(BigDecimal.ZERO);
		orderInfo.setCouponPrice(BigDecimal.ZERO);
		orderInfo.setMemberDiscountPrice(BigDecimal.ZERO);
		orderInfo.setPointsMultiplier(BigDecimal.ONE);
		orderInfo.setPayStatus(CommonConstants.NO);
		orderInfo.setCouponUserId(createOrderDTO.getCouponUserId());
		orderInfo.setOpenId(createOrderDTO.getOpenId());
		orderInfo.setRequestId(createOrderDTO.getRequestId());
		return orderInfo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean receiveOrder(OrderInfo orderInfo) {
		LocalDateTime receiverTime = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())
			.eq(OrderInfo::getPayStatus, CommonConstants.YES)
			.set(OrderInfo::getReceiverTime, receiverTime)
			.set(OrderInfo::getStatus, OrderStatusEnum.COMPLETED.getCode()));
		if (updated == 0) {
			throw new ArynBusinessException("订单状态已变化，无法确认收货");
		}
		orderInfo.setReceiverTime(receiverTime);
		orderInfo.setStatus(OrderStatusEnum.COMPLETED.getCode());
		List<OrderItemEntity> orderItemEntityList = orderItemMapper.selectByOrderId(orderInfo.getId());
		orderItemEntityList.forEach(orderItem -> {
			if (orderItem.getStatus().equals(OrderItemStatusEnum.SHIPPED.getCode())) {
				orderItem.setStatus(OrderItemStatusEnum.COMPLETED.getCode());
			}
		});
		if (!orderItemService.updateBatchById(orderItemEntityList)) {
			throw new ArynBusinessException("订单商品状态更新失败");
		}

		// 订单完成事件
		OrderCompleteEvent orderPaySuccessEvent = new OrderCompleteEvent();
		orderPaySuccessEvent.setOrderId(orderInfo.getId());
		orderPaySuccessEvent.setTenantId(orderInfo.getTenantId());
		orderPaySuccessEvent.setUserId(orderInfo.getUserId());
		orderPaySuccessEvent.setOrderNo(orderInfo.getOrderNo());
		orderPaySuccessEvent.setGoodsPaymentAmount(orderInfo.getPaymentPrice().subtract(orderInfo.getFreightPrice()));
		orderPaySuccessEvent.setPointsMultiplier(orderInfo.getPointsMultiplier() == null
				? BigDecimal.ONE : orderInfo.getPointsMultiplier());

		TransactionalMqUtils.sendAfterCommit(() -> {
			orderWxDeliveryService.uploadDeliveryInfoOnReceive(orderInfo, orderItemEntityList);
			rocketMQTemplate.syncSend(RocketMqConstants.ORDER_COMPLETE_NOTIFY_TOPIC,
					new GenericMessage<>(orderPaySuccessEvent), RocketMqConstants.TIME_OUT);
		});

		// 商城配送签收联动：更新对应配送任务为已签收
		try {
			deliveryTaskService.signOnReceive(orderInfo.getId());
		}
		catch (Exception e) {
			log.error("订单签收联动失败: " + orderInfo.getId(), e);
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean receiveUserOrder(String id, String userId) {
		OrderInfo orderInfo = getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getId, id)
			.eq(OrderInfo::getUserId, userId));
		if (orderInfo == null) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60003.getCode(),
					MallErrorCodeEnum.ERROR_60003.getMsg());
		}
		return receiveOrder(orderInfo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Result<Object> prepay(PrepayDTO prepayDTO) {
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)) {
			throw new ArynBusinessException("订单配置不存在");
		}
		String tradeType = prepayDTO.getTradeType();
		String payType = prepayDTO.getPaymentType();
		String returnUrl = prepayDTO.getReturnUrl();
		String quitUrl = prepayDTO.getQuitUrl();
		if (StrUtil.isBlank(tradeType)) {
			return Result.fail(MallErrorCodeEnum.ERROR_60001.getCode(), MallErrorCodeEnum.ERROR_60001.getMsg());
		}
		if (StrUtil.isBlank(payType)) {
			return Result.fail(MallErrorCodeEnum.ERROR_60002.getCode(), MallErrorCodeEnum.ERROR_60002.getMsg());
		}
		if (StrUtil.isBlank(orderConfig.getNotifyUrl())) {
			return Result.fail(MallErrorCodeEnum.ERROR_90001.getCode(), MallErrorCodeEnum.ERROR_90001.getMsg());
		}

		OrderInfo orderInfo = this.getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getOrderNo, prepayDTO.getOrderNo())
			.eq(OrderInfo::getUserId, prepayDTO.getUserId()));
		if (orderInfo == null) {
			return Result.fail(MallErrorCodeEnum.ERROR_60003.getCode(), MallErrorCodeEnum.ERROR_60003.getMsg());
		}
		// 只有未支付的详单能发起支付
		if (CommonConstants.YES.equals(orderInfo.getPayStatus())) {
			return Result.fail(MallErrorCodeEnum.ERROR_60004.getCode(), MallErrorCodeEnum.ERROR_60004.getMsg());
		}
		if (!OrderStatusEnum.WAITING_FOR_PAYMENT.getCode().equals(orderInfo.getStatus())) {
			return Result.fail(MallErrorCodeEnum.ERROR_60003.getCode(), MallErrorCodeEnum.ERROR_60003.getMsg());
		}
		CreateOrderReqDTO payDTO = new CreateOrderReqDTO();
		if (orderInfo.getPaymentPrice().compareTo(BigDecimal.ZERO) == 0) {
			tradeType = PayTradeTypeEnum.FREE_PAY.getName();
			payType = PayConstants.PAY_TYPE_0;
		}
		else {
			payDTO.setTradeType(prepayDTO.getTradeType());
		}

		String body = "商城购物";
		CreateOrderReqDTO createOrderReqDTO = new CreateOrderReqDTO();
		createOrderReqDTO.setTradeType(tradeType);
		createOrderReqDTO.setSubject(body);
		createOrderReqDTO.setBuyerId(SecurityUtils.getUser().getOpenId());
		createOrderReqDTO.setTotalAmount(String.valueOf(orderInfo.getPaymentPrice()));
		createOrderReqDTO.setNotifyUrl(orderConfig.getNotifyUrl());
		createOrderReqDTO.setOutTradeNo(orderInfo.getOrderNo());
		createOrderReqDTO.setQuitUrl(quitUrl);
		createOrderReqDTO.setReturnUrl(returnUrl);
		createOrderReqDTO.setUserId(orderInfo.getUserId());
		JSONObject extraParams = new JSONObject();
		extraParams.put(PayConstants.EXTRA_PARAMS_PAY_TYPE, payType);
		extraParams.put("mqNotifyUrl", RocketMqConstants.PAY_NOTIFY_TOPIC);
		createOrderReqDTO.setExtra(extraParams.toJSONString());
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("orderNo", orderInfo.getOrderNo());
		resultMap.put("payParams", remotePayService.createOrder(createOrderReqDTO));
		return Result.success(resultMap);
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public boolean appraiseOrder(String id, String userId, List<OrderAppraiseDTO> orderAppraiseList) {
		return orderAppraiseService.appraiseOrder(id, userId, orderAppraiseList);
	}

	@Override
	public List<Map<String, Object>> statistics() {
		return orderStatisticsQueryService.statistics();
	}

	@Override
	public OrderInfo settlementOrder(SettlementOrderDTO settlementOrderDTO) {
		// 查询用户信息
		UserInfoVO userInfo = remoteMallUserService.getUserById(settlementOrderDTO.getUserId());
		if (Objects.isNull(userInfo)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50001.getMsg());
		}
		List<String> skuIds = settlementOrderDTO.getSkuReqList()
			.stream()
			.map(CreateOrderSkuReqDTO::getSkuId)
			.distinct()
			.toList();
		if (skuIds.size() != settlementOrderDTO.getSkuReqList().size()) {
			throw new ArynBusinessException("订单商品不能包含重复SKU");
		}
		// 查询购买商品
		List<GoodsSku> goodsSkuList = remoteGoodsSkuService.getBySkuIds(skuIds);
		if (CollectionUtils.isEmpty(goodsSkuList) || goodsSkuList.size() != skuIds.size()) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}

		// 生成订单
		OrderInfo orderInfo = new OrderInfo();
		BeanUtil.copyProperties(settlementOrderDTO, orderInfo);
		orderInfo.setTotalPrice(BigDecimal.ZERO);
		orderInfo.setFreightPrice(BigDecimal.ZERO);
		orderInfo.setCouponPrice(BigDecimal.ZERO);
		orderInfo.setPayStatus(CommonConstants.NO);
		orderInfo.setCouponUserId(settlementOrderDTO.getCouponUserId());

		// 生成订单商品
		List<OrderItemEntity> orderItemEntityList = orderPriceComputeService.generateOrderItems(goodsSkuList,
				settlementOrderDTO.getSkuReqList());

		orderPriceComputeService.computeOrderPrice(orderInfo, orderItemEntityList);
		MemberBenefitsVO memberBenefits = remoteMallUserService.getMemberBenefits(settlementOrderDTO.getUserId());
		orderPriceComputeService.orderMemberBenefitHandler(orderInfo, orderItemEntityList, memberBenefits);
		orderPriceComputeService.orderCouponHandler(orderInfo, orderItemEntityList);
		// 5.计算运费（普通快递和商城配送均需收货地址与运费）
		if (MallOrderConstants.DELIVERY_WAY_1.equals(orderInfo.getDeliveryWay())
				|| MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())) {
			if (!StringUtils.hasText(settlementOrderDTO.getUserAddressId())) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50002.getCode(),
						MallErrorCodeEnum.ERROR_50002.getMsg());
			}
			// 查询用户收货地址
			UserAddress userAddress = remoteUserAddressService.getById(settlementOrderDTO.getUserAddressId(),
					settlementOrderDTO.getUserId());
			if (ObjectUtil.isNull(userAddress)) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50002.getCode(),
						MallErrorCodeEnum.ERROR_50002.getMsg());
			}
			orderInfo.setRecipientName(userAddress.getRecipientName());
			orderInfo.setRecipientPhone(userAddress.getTelephone());
			orderInfo.setRecipientProvince(userAddress.getProvinceName());
			orderInfo.setRecipientCity(userAddress.getCityName());
			orderInfo.setRecipientArea(userAddress.getAreaName());
			orderInfo.setRecipientProvinceCode(userAddress.getProvinceCode());
			orderInfo.setRecipientCityCode(userAddress.getCityCode());
			orderInfo.setRecipientAreaCode(userAddress.getAreaCode());
			orderInfo.setRecipientAddress(userAddress.getDetailAddress());

			orderPriceComputeService.orderFreightHandler(orderInfo, orderItemEntityList, goodsSkuList,
					memberBenefits != null && memberBenefits.isFreeShipping());
			if (MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
					&& !deliveryAreaService.isAddressInDeliveryArea(userAddress.getProvinceCode(),
							userAddress.getCityCode(), userAddress.getAreaCode())) {
				throw new ArynBusinessException("当前收货地址不在商城配送范围内");
			}
		}
		orderInfo.setOrderItemList(orderItemEntityList);
		return orderInfo;
	}

	@Override
	public Map<String, Object> merchantStatistics(OrderStatisticsDTO request) {
		return orderStatisticsQueryService.merchantStatistics(request);
	}

	@Override
	public List<OrderStatisticsVO> payTypeStatistics(OrderStatisticsDTO orderStatisticsDTO) {
		return orderStatisticsQueryService.payTypeStatistics(orderStatisticsDTO);
	}

	@Override
	public List<OrderStatisticsVO> channelTypeStatistics(OrderStatisticsDTO orderStatisticsDTO) {
		return orderStatisticsQueryService.channelTypeStatistics(orderStatisticsDTO);
	}

	@Override
	public boolean autoAppraiseOrder(OrderInfo orderInfo) {
		return orderAppraiseService.autoAppraiseOrder(orderInfo);
	}

}
