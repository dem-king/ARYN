
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
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
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
import com.aryn.cloud.order.event.ArynOrderCreateBeforeEvent;
import com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.order.service.OrderStaffNotifyService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemoteGroupBuyService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemotePointsService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;

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
	private final RemoteGoodsSpuService remoteGoodsSpuService;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	@DubboReference
	private final RemotePointsService remotePointsService;

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

	@DubboReference
	private final RemoteSeckillService remoteSeckillService;

	@DubboReference
	private final RemoteGroupBuyService remoteGroupBuyService;

	private final RocketMQTemplate rocketMQTemplate;

	private final CallbackPrefixProperties callbackPrefixProperties;

	private final com.aryn.cloud.order.service.IDeliveryTaskService deliveryTaskService;

	private final com.aryn.cloud.order.service.IOrderDeliveryStateService orderDeliveryStateService;

	private final com.aryn.cloud.order.service.IDeliveryAreaService deliveryAreaService;

	private final com.aryn.cloud.order.mapper.PromotionSnapshotMapper promotionSnapshotMapper;

	private final com.aryn.cloud.order.validator.PurchaseSceneValidator purchaseSceneValidator;

	private final com.aryn.cloud.order.validator.DeliveryContextValidator deliveryContextValidator;

	private final OrderPaySuccessNotifier orderPaySuccessNotifier;

	@DubboReference
	private final RemotePromotionEngine promotionEngine;

	@DubboReference
	private final com.aryn.cloud.upms.api.remote.RemoteMaterialService remoteMaterialService;

	/**
	 * 共享购物车归档服务（延迟解析）。
	 *
	 * <p>必须用 {@link ObjectProvider} 而非直接注入：SharedCartServiceImpl 依赖 IOrderInfoService
	 * 创建整船订单，订单签收又要回调它归档购物车，直接注入会形成构造器循环依赖。
	 *
	 * <p>注意不能用 Lombok 字段上的 {@code @Lazy}——{@code @RequiredArgsConstructor}
	 * 不会把字段注解复制到构造参数上，循环依赖依然存在（已在部署时验证过）。
	 * ObjectProvider 注入的是提供者本身，调用 {@code getObject()} 时才解析目标 Bean。
	 */
	private final ObjectProvider<ISharedCartService> sharedCartServiceProvider;

	/** 订单事件工作人员通知（新单/取消/退款申请），失败只告警不阻断业务 */
	private final OrderStaffNotifyService orderStaffNotifyService;

	@Override
	public IPage<OrderInfo> adminPage(Page page, OrderInfo orderInfo) {
		IPage<OrderInfo> result = baseMapper.selectAdminPage(page, orderInfo);
		if (!CollectionUtils.isEmpty(result.getRecords())) {
			// 管理端列表内嵌商品行按分类分组展示，需要统一回填分类名
			fillCategoryNames(result.getRecords().stream()
				.filter(order -> !CollectionUtils.isEmpty(order.getOrderItemList()))
				.flatMap(order -> order.getOrderItemList().stream())
				.collect(Collectors.toList()));
			// 商城配送/内部配送订单回填当前配送任务，列表才能区分「未派单」与「已派单取货中」，
			// 否则派单后订单仍显示待发货，运营会误以为没有点过发货
			fillDeliveryTasks(result.getRecords());
			// 下发「是否已送达」供管理端判断能否确认收款/代确认收货（依赖上面的配送任务回填）
			result.getRecords().forEach(order -> order.setDelivered(orderDeliveryStateService.isDelivered(order)));
		}
		return result;
	}

	private void fillDeliveryTasks(List<OrderInfo> orders) {
		List<String> orderIds = orders.stream()
			.filter(order -> OrderDeliveryStateService.isTaskDrivenDeliveryWay(order.getDeliveryWay()))
			.map(OrderInfo::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(orderIds)) {
			return;
		}
		Map<String, com.aryn.cloud.order.api.entity.DeliveryTask> taskMap = deliveryTaskService.mapByOrderIds(orderIds);
		if (CollectionUtils.isEmpty(taskMap)) {
			return;
		}
		orders.forEach(order -> order.setDeliveryTask(taskMap.get(order.getId())));
	}

	@Override
	public List<OrderInfo> listForExport(OrderInfo orderInfo) {
		int total = baseMapper.countExportList(orderInfo);
		if (total > MallOrderConstants.EXPORT_MAX_ORDERS) {
			throw new ArynBusinessException("符合条件的订单超过 " + MallOrderConstants.EXPORT_MAX_ORDERS
					+ " 单，请缩小筛选范围后导出");
		}
		List<OrderInfo> orders = baseMapper.selectExportList(orderInfo);
		if (CollectionUtils.isEmpty(orders)) {
			return orders;
		}
		Map<String, List<OrderItemEntity>> itemsByOrderId = orderItemMapper
			.selectByOrderIds(orders.stream().map(OrderInfo::getId).collect(Collectors.toList()))
			.stream()
			.collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
		orders.forEach(order -> order.setOrderItemList(
			itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList())));
		fillCategoryNames(orders.stream()
			.flatMap(order -> order.getOrderItemList().stream())
			.collect(Collectors.toList()));
		return orders;
	}

	@Override
	public OrderInfo getOrderById(String id) {
		OrderInfo orderInfo = baseMapper.selectOrderById(id);
		if (Objects.isNull(orderInfo)) {
			return null;
		}
		if (OrderDeliveryStateService.isTaskDrivenDeliveryWay(orderInfo.getDeliveryWay())) {
			// 管理端发货窗口需要展示派单进度，快递订单不回填
			orderInfo.setDeliveryTask(deliveryTaskService.getTaskByOrderId(orderInfo.getId()));
		}
		// 详情页需要判断能否确认收款 / 代确认收货
		orderInfo.setDelivered(orderDeliveryStateService.isDelivered(orderInfo));
		if (!CollectionUtils.isEmpty(orderInfo.getOrderItemList())) {
			fillCategoryNames(orderInfo.getOrderItemList());
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
		if (Objects.nonNull(orderInfo)) {
			// C 端详情需要展示配送进度与「确认收货」按钮，回填配送任务与送达标记
			if (OrderDeliveryStateService.isTaskDrivenDeliveryWay(orderInfo.getDeliveryWay())) {
				orderInfo.setDeliveryTask(deliveryTaskService.getTaskByOrderId(orderInfo.getId()));
			}
			orderInfo.setDelivered(orderDeliveryStateService.isDelivered(orderInfo));
		}
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

	/**
	 * 批量回填订单商品行的分类名（一级/二级拼接，口径同商品域 {@code RemoteGoodsSpuService#getSpuByIds}）。
	 *
	 * <p>order_item 不落分类快照，分类经 Dubbo 从商品域实时读取；回填失败只降级为
	 * 无分类展示（前端/导出按「未分类」归组），不阻断订单主流程。
	 */
	private void fillCategoryNames(List<OrderItemEntity> orderItems) {
		if (CollectionUtils.isEmpty(orderItems)) {
			return;
		}
		List<String> spuIds = orderItems.stream()
			.map(OrderItemEntity::getSpuId)
			.filter(Objects::nonNull)
			.filter(spuId -> !spuId.isBlank())
			.distinct()
			.collect(Collectors.toList());
		if (spuIds.isEmpty()) {
			return;
		}
		try {
			Map<String, String> categoryNameBySpuId = remoteGoodsSpuService.getSpuByIds(spuIds).stream()
				.filter(spu -> StrUtil.isNotBlank(spu.getCategoryName()))
				.collect(Collectors.toMap(GoodsSpu::getId, GoodsSpu::getCategoryName, (first, second) -> first));
			if (categoryNameBySpuId.isEmpty()) {
				return;
			}
			orderItems.forEach(orderItem -> orderItem
				.setCategoryName(categoryNameBySpuId.get(orderItem.getSpuId())));
		}
		catch (Exception ex) {
			log.warn("订单商品分类名回填失败，按未分类展示: " + ex.getMessage());
		}
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
		if (OrderDeliveryStateService.isTaskDrivenDeliveryWay(orderInfo.getDeliveryWay())) {
			// 商城配送/内部配送由配送任务状态机驱动，快递发货单会造成任务与订单双轨不一致
			throw new ArynBusinessException("公司配送订单无需物流单号，请直接派单给司机");
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
	@Transactional(rollbackFor = Exception.class)
	public boolean deliverAndAssignOrder(OrderDeliverAssignDTO request) {
		OrderInfo orderInfo = baseMapper.selectById(request.getOrderId());
		if (ObjectUtil.isNull(orderInfo)) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!OrderDeliveryStateService.isTaskDrivenDeliveryWay(orderInfo.getDeliveryWay())) {
			throw new ArynBusinessException("快递配送订单请填写物流信息发货");
		}
		if (!OrderStatusEnum.WAITING_FOR_DELIVERY.getCode().equals(orderInfo.getStatus())) {
			throw new ArynBusinessException("订单不是待发货状态，无法派单");
		}
		List<OrderItemEntity> orderItemEntityList = orderItemService
			.list(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, orderInfo.getId()));
		if (CollectionUtils.isEmpty(orderItemEntityList)) {
			throw new ArynBusinessException("订单商品不存在");
		}
		orderItemEntityList.forEach(orderItem -> {
			if (!OrderItemStatusEnum.PAID.getCode().equals(orderItem.getStatus())) {
				throw new ArynBusinessException("订单商品状态已变化，无法派单");
			}
		});
		// 幂等兜底：历史订单可能缺配送任务（服务内按订单幂等，已有任务直接复用）
		deliveryTaskService.createTaskOnPay(orderInfo, orderItemEntityList);
		// 派单后订单仍为待发货，司机取货出发时由 OrderDeliveryStateService 推转为待收货
		deliveryTaskService.assignByOrderId(orderInfo.getId(), request.getStaffId());
		return true;
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public String cancelOrder(OrderInfo orderInfo) {
		return cancelOrder(orderInfo, OrderStaffNotifyService.CancelSource.OTHER);
	}

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public String cancelOrder(OrderInfo orderInfo, OrderStaffNotifyService.CancelSource source) {
		String canceledOrderId = doCancelOrder(orderInfo);
		// 工作人员通知挂事务提交后：回滚时不留「订单没取消却收到消息」的误报；
		// 通知失败只告警，不影响取消事实（幂等由站内信来源唯一键兜底）
		OrderStaffNotifyService.CancelSource cancelSource = source == null ? OrderStaffNotifyService.CancelSource.OTHER
				: source;
		TransactionalMqUtils.sendAfterCommit(() -> {
			try {
				orderStaffNotifyService.notifyOrderCanceled(orderInfo, cancelSource);
			}
			catch (RuntimeException exception) {
				log.warn("订单[" + orderInfo.getId() + "]取消通知发送失败: " + exception.getMessage());
			}
		});
		return canceledOrderId;
	}

	private String doCancelOrder(OrderInfo orderInfo) {
		// 货到付款单在待发货阶段可取消：先关待派单任务再取消订单，任务已派单/配送中直接拒绝
		boolean cashOnDeliveryCancel = MallOrderConstants.PAYMENT_TYPE_3.equals(orderInfo.getPaymentType())
				&& OrderStatusEnum.WAITING_FOR_DELIVERY.getCode().equals(orderInfo.getStatus())
				&& CommonConstants.NO.equals(orderInfo.getPayStatus());
		if (cashOnDeliveryCancel) {
			deliveryTaskService.cancelWaitingAssignByOrderId(orderInfo.getId());
		}
		int updated = baseMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getStatus, cashOnDeliveryCancel
					? OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()
					: OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
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
		// 释放营销锁定（取消/超时共用本方法，幂等）
		try {
			String releaseTenant = StringUtils.hasText(orderInfo.getTenantId()) ? orderInfo.getTenantId()
					: ArynTenantContextHolder.getTenantId();
			orderPriceComputeService.releasePromotion(releaseTenant, orderInfo.getId(), "CANCEL");
		}
		catch (Exception ex) {
			log.warn("订单[" + orderInfo.getId() + "]营销优惠释放失败，等待补偿: " + ex.getMessage());
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
		// 释放秒杀预扣：取消/超时订单不再占用秒杀限量名额。
		// 与超时任务互为兜底（先到者生效，秒杀服务内幂等），失败只告警不阻断取消
		try {
			remoteSeckillService.rollbackStock(orderInfo.getId());
		}
		catch (Exception ex) {
			log.warn("订单[" + orderInfo.getId() + "]秒杀预扣释放失败，等待超时任务兜底: " + ex.getMessage());
		}
		// 释放拼团占坑：清空成员的订单关联并回到待付款，用户可重新下单
		// （商品库存已随上方 rollbackStock 回滚；团超时后由超时任务统一收尾），失败只告警
		try {
			remoteGroupBuyService.releaseOrder(orderInfo.getId());
		}
		catch (Exception ex) {
			log.warn("订单[" + orderInfo.getId() + "]拼团占坑释放失败，等待超时任务兜底: " + ex.getMessage());
		}
		// 返还积分放在取消链路最后：券释放/库存回滚等前置步骤失败时整单先回滚，
		// 避免 cloud 模式（积分库独立提交）出现「订单取消失败但积分已退回」
		if (orderInfo.getPointsUsed() != null && orderInfo.getPointsUsed() > 0) {
			remotePointsService.refundPoints(orderInfo.getUserId(), orderInfo.getPointsUsed(),
					"ORDER_REFUND:" + orderInfo.getOrderNo(), "订单取消返还积分：" + orderInfo.getOrderNo());
		}
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
		return cancelOrder(orderInfo, OrderStaffNotifyService.CancelSource.BUYER);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean confirmOfflinePayment(String id, PayConfirmDTO payConfirmDTO) {
		if (payConfirmDTO == null || payConfirmDTO.getActualPayPrice() == null) {
			throw new ArynBusinessException("请填写实收金额");
		}
		OrderInfo orderInfo = getById(id);
		if (orderInfo == null || !MallOrderConstants.PAYMENT_TYPE_3.equals(orderInfo.getPaymentType())) {
			throw new ArynBusinessException("仅货到付款订单支持确认收款");
		}
		// 货到付款为签收付款：货物已送达（买家已收货，或配送任务已送达/签收）才可能收到钱。
		// 不以「订单已完成」为门槛——客户常当面付款却不在小程序点确认收货，
		// 若只认已完成，收款入口会被拖到超时自动收货之后（默认 7 天）。
		if (!orderDeliveryStateService.isDelivered(orderInfo)) {
			throw new ArynBusinessException("订单尚未送达，无法确认收款");
		}
		BigDecimal receivable = orderInfo.getPaymentPrice() != null ? orderInfo.getPaymentPrice()
				: orderInfo.getTotalPrice();
		if (receivable != null && payConfirmDTO.getActualPayPrice().compareTo(receivable) > 0) {
			throw new ArynBusinessException("实收金额不能大于应收金额" + receivable + "元");
		}
		String payVouchers = buildPayVoucherSnapshot(payConfirmDTO.getVoucherMaterialIds());
		LocalDateTime paymentTime = LocalDateTime.now();
		// 允许「待收货但已送达」与「已完成」两种状态下登记收款；
		// 条件同时校验 pay_status=0 与状态，防并发重复收款。
		int updated = baseMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, id)
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.in(OrderInfo::getStatus, List.of(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
					OrderStatusEnum.COMPLETED.getCode()))
			.set(OrderInfo::getPayStatus, CommonConstants.YES)
			.set(OrderInfo::getPaymentTime, paymentTime)
			.set(OrderInfo::getActualPayPrice, payConfirmDTO.getActualPayPrice())
			.set(OrderInfo::getPayVouchers, payVouchers));
		if (updated == 0) {
			throw new ArynBusinessException("订单状态已变化，无法确认收款");
		}
		orderInfo.setPayStatus(CommonConstants.YES);
		orderInfo.setPaymentTime(paymentTime);
		orderInfo.setActualPayPrice(payConfirmDTO.getActualPayPrice());
		orderInfo.setPayVouchers(payVouchers);
		List<OrderItemEntity> orderItemEntityList = orderItemService
			.list(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, id));
		// 营销优惠确认（锁定→确认），失败仅告警不阻断收款，与在线支付回调口径一致
		try {
			promotionEngine.confirm(orderInfo.getTenantId(), orderInfo.getId());
		}
		catch (Exception ex) {
			log.error("货到付款订单[" + orderInfo.getId() + "]营销优惠确认失败", ex);
		}
		// 销量/优惠券/用户通知：确认收款视同支付成功
		TransactionalMqUtils.sendAfterCommit(() -> orderPaySuccessNotifier.notify(orderInfo, orderItemEntityList));
		return Boolean.TRUE;
	}

	/**
	 * 付款凭证素材 ID → 访问 URL 快照，确认收款后素材删除不影响回显；
	 * 素材 ID 无效（不存在或已删除）时拒绝提交，避免凭证静默丢失。
	 */
	private String buildPayVoucherSnapshot(List<String> voucherMaterialIds) {
		if (CollectionUtils.isEmpty(voucherMaterialIds)) {
			return null;
		}
		List<String> materialIds = voucherMaterialIds.stream()
			.filter(StrUtil::isNotBlank)
			.distinct()
			.toList();
		if (materialIds.isEmpty()) {
			return null;
		}
		Map<String, String> urlMap = remoteMaterialService.mapUrlByIds(materialIds);
		if (urlMap == null || urlMap.size() != materialIds.size()) {
			throw new ArynBusinessException("付款凭证素材不存在或已删除，请重新上传");
		}
		List<Map<String, String>> vouchers = materialIds.stream()
			.map(materialId -> Map.of("materialId", materialId, "materialUrl", urlMap.get(materialId)))
			.toList();
		return com.alibaba.fastjson2.JSON.toJSONString(vouchers);
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
		IPage<OrderInfo> result = baseMapper.selectApiPage(page, orderInfo);
		if (!CollectionUtils.isEmpty(result.getRecords())) {
			// 回填「货物是否已送达」，C 端据此决定是否展示「确认收货」按钮
			// （商城配送/内部配送必须等司机点已送达，否则按钮不出现）
			fillDeliveredForReceive(result.getRecords());
		}
		return result;
	}

	/**
	 * 批量回填 C 端的「是否已送达」。
	 *
	 * <p>仅任务驱动的配送方式（way=3/4）需要查配送任务；快递与自提的送达口径
	 * 不依赖配送任务，避免无谓查询。回填前先批量取任务，避免逐单 N+1。
	 */
	private void fillDeliveredForReceive(List<OrderInfo> orders) {
		List<String> taskDrivenOrderIds = orders.stream()
			.filter(order -> OrderDeliveryStateService.isTaskDrivenDeliveryWay(order.getDeliveryWay()))
			.map(OrderInfo::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		Map<String, com.aryn.cloud.order.api.entity.DeliveryTask> taskMap = taskDrivenOrderIds.isEmpty()
				? Collections.emptyMap() : deliveryTaskService.mapByOrderIds(taskDrivenOrderIds);
		orders.forEach(order -> {
			if (taskMap.containsKey(order.getId())) {
				order.setDeliveryTask(taskMap.get(order.getId()));
			}
			order.setDelivered(orderDeliveryStateService.isDelivered(order));
		});
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
		// 支付类型入口校验：下单时仅允许声明货到付款；微信/支付宝由支付回调回填
		if (StringUtils.hasText(createOrderDTO.getPaymentType())
				&& !MallOrderConstants.PAYMENT_TYPE_3.equals(createOrderDTO.getPaymentType())) {
			throw new ArynBusinessException("支付类型不合法");
		}
		if (MallOrderConstants.PAYMENT_TYPE_3.equals(createOrderDTO.getPaymentType())
				&& !MallOrderConstants.DELIVERY_WAY_3.equals(createOrderDTO.getDeliveryWay())
				&& !MallOrderConstants.DELIVERY_WAY_4.equals(createOrderDTO.getDeliveryWay())) {
			throw new ArynBusinessException("该配送方式不支持货到付款");
		}
		// 拼团单必须在线支付：成团判定依赖支付回调，货到付款无支付回调会导致团永远无法成团
		if (StringUtils.hasText(createOrderDTO.getGroupBuyRecordId())
				&& MallOrderConstants.PAYMENT_TYPE_3.equals(createOrderDTO.getPaymentType())) {
			throw new ArynBusinessException("拼团商品不支持货到付款，请选择在线支付");
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
		validateAndApplyDeliveryContext(createOrderDTO.getPurchaseScene(), createOrderDTO.getUserId(), orderInfo);
		// 生成订单商品
		List<OrderItemEntity> orderItemEntityList = orderPriceComputeService.generateOrderItems(goodsSkuList, createOrderDTO.getSkuReqList());
		// 拼团价预取（拼团单）：校验拼团记录有效并取活动上下文（拼团单价 + 叠加策略），
		// 拼团价优先级最高，须在会员折扣与优惠券之前落到成交基价
		GroupBuyOrderContextVO groupBuyContext = resolveGroupBuyContext(createOrderDTO.getGroupBuyRecordId(),
				createOrderDTO.getUserId(), orderItemEntityList);
		// 活动价预取：整船优惠需排除已享拼团/秒杀/限时折扣的 SKU，券/积分叠加策略也取自该计划
		OrderPriceComputeService.PromotionPricePlan promotionPricePlan = orderPriceComputeService
			.resolvePromotionPricePlan(orderItemEntityList, groupBuyContext);
		// 营销阶梯价改基价（会员/券之前）
		PromotionCalculationVO promoCalculation = orderPriceComputeService.applyPromotionLadder(orderInfo,
				orderItemEntityList, promotionPricePlan);
		// 新人价命中（含叠加策略）补入计划：新人价由引擎落价，预取阶段未知
		orderPriceComputeService.mergeStackPolicyHits(promotionPricePlan, promoCalculation);
		// 新人价限购硬校验：命中新人价的 SKU 整单合计数量必须为 1，超量拒绝下单
		orderPriceComputeService.assertNewbieQuantityLimit(promoCalculation, orderItemEntityList);
		// 促销价（拼团 > 秒杀 > 新人价 > 限时折扣 > 原价）须在会员折扣与优惠券之前落到成交基价，
		// 否则券与会员折扣按原价基数抵扣；历史实现藏在运费分支内，自提/内配恒不生效
		orderPriceComputeService.applyPromotionPricePlan(orderItemEntityList,
				orderPriceComputeService.newbiePriceSkuIds(promoCalculation), promotionPricePlan);

		orderPriceComputeService.computeOrderPrice(orderInfo, orderItemEntityList);
		MemberBenefitsVO memberBenefits = remoteMallUserService.getMemberBenefits(createOrderDTO.getUserId());
		orderPriceComputeService.orderMemberBenefitHandler(orderInfo, orderItemEntityList, memberBenefits,
				promotionPricePlan);
		// 严格模式：不可叠活动商品仍带券下单直接拒绝（防绕过确认页）
		orderPriceComputeService.orderCouponHandler(orderInfo, orderItemEntityList, promotionPricePlan, true);
		// 整船/整单优惠分摊（券之后；活动价商品不参与）
		orderPriceComputeService.applyShipWholeDiscount(orderInfo, orderItemEntityList, promoCalculation,
				promotionPricePlan);

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
				throw new ArynBusinessException("当前收货地址不在公司配送（岸上）范围内");
			}
		}
		// 积分抵现：基数与优惠顺序固定为「会员折扣 -> 优惠券 -> 积分抵扣 -> 运费」，须在金额校验前完成；
		// 叠加策略下积分基数只统计允许积分的明细，不可叠仍请求积分则拒绝
		PointsDeductContextVO pointsContext = remotePointsService.getDeductContext(createOrderDTO.getUserId());
		// 积分黑名单：由用户域上下文（类目/商品）与商品快照解析命中 SPU，
		// 抵扣基数过滤、明细 points_excluded 落库共用同一口径（返积分按该标记剔除）
		Set<String> pointsExcludedSpuIds = orderPriceComputeService.resolvePointsExcludedSpuIds(goodsSkuList,
				pointsContext);
		orderPriceComputeService.orderPointsDeductHandler(orderInfo, orderItemEntityList,
				createOrderDTO.getUsePoints(), Boolean.TRUE.equals(createOrderDTO.getUseMaxPoints()), pointsContext,
				true, promotionPricePlan, pointsExcludedSpuIds);
		// 确认页金额校验：优惠额度可能在结算后变化，价格不一致要求用户重新确认，禁止静默涨价
		if (createOrderDTO.getExpectedPaymentPrice() != null
				&& (orderInfo.getPaymentPrice() == null || orderInfo.getPaymentPrice()
					.compareTo(createOrderDTO.getExpectedPaymentPrice()) != 0)) {
			throw new ArynBusinessException("订单金额已变动，请返回确认页核对最新价格后重新提交");
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
		// 拼团绑定：把订单号写入拼团成员（成团判定与失败退款依赖该关联）。
		// 乐观抢占失败说明占坑已被并发订单占用，阻断本单，避免同一资格下重复成交
		if (StringUtils.hasText(createOrderDTO.getGroupBuyRecordId())
				&& !remoteGroupBuyService.bindOrder(createOrderDTO.getGroupBuyRecordId(),
						createOrderDTO.getUserId(), orderInfo.getId())) {
			throw new ArynBusinessException("拼团资格已被占用，请重新开团或参团");
		}
		// 明细ID提前固定：折扣库存按明细占用，退款时要能按 order_item_id 精确回补；
		// 原先是 MyBatis 在 insert 时才生成，占用阶段拿不到。
		orderItemEntityList.forEach(item -> {
			if (!StringUtils.hasText(item.getId())) {
				item.setId(IdUtil.getSnowflakeNextIdStr());
			}
		});
		// 营销锁定（须在库存扣减与明细落库前完成：买赠赠品要追加为 0 元明细并参与扣减）
		PromotionCalculationVO reserved = orderPriceComputeService.reservePromotion(orderInfo, orderItemEntityList,
				goodsSkuList, promoCalculation, promotionPricePlan);
		// 赠品按真实库存过滤：库存不足不赠（确认页同口径已不展示），不阻断付费商品
		List<PromotionCalculationVO.GiftItem> availableGifts = orderPriceComputeService.availableGifts(reserved);
		if (!availableGifts.isEmpty()) {
			appendGiftItems(orderInfo, availableGifts, orderItemEntityList, goodsSkuList);
		}
		orderPriceComputeService.orderStockHandler(goodsSkuList, orderItemEntityList);
		// 秒杀预扣：Redis 秒杀库存扣减 + 单人限购校验，库存不足/超限购阻断整单。
		// 必须在普通库存扣减之后：普通库存不足已能快速失败，避免先占秒杀名额再回滚
		applicationEventPublisher.publishEvent(
				new ArynOrderCreateBeforeEvent(this, orderInfo, orderItemEntityList));
		boolean cashOnDelivery = MallOrderConstants.PAYMENT_TYPE_3.equals(orderInfo.getPaymentType());
		orderItemEntityList.forEach(orderItem -> {
			orderItem.setOrderId(orderInfo.getId());
			orderItem.setPurchaseScene(orderInfo.getPurchaseScene());
			if (cashOnDelivery) {
				// 货到付款无支付回调：明细直接置为待发货，保证仓库配货/管理端发货校验通过
				orderItem.setStatus(OrderItemStatusEnum.PAID.getCode());
			}
		});
		if (!orderItemService.saveBatch(orderItemEntityList)) {
			throw new ArynBusinessException("订单商品保存失败");
		}
		// 营销快照（订单+活动维度；历史金额以快照为准）
		if (reserved != null && reserved.getDetails() != null) {
			for (PromotionCalculationVO.ActivityDetail detail : reserved.getDetails()) {
				com.aryn.cloud.order.api.entity.PromotionSnapshot snapshot = new com.aryn.cloud.order.api.entity.PromotionSnapshot();
				snapshot.setOrderId(orderInfo.getId());
				snapshot.setActivityId(detail.getActivityId());
				snapshot.setActivityType(detail.getActivityType());
				snapshot.setPromotionName(detail.getActivityName());
				snapshot.setRuleSnapshot(detail.getRuleSnapshot());
				snapshot.setDiscountAmount(detail.getDiscountAmount());
				snapshot.setPurchaseScene(orderInfo.getPurchaseScene());
				snapshot.setTenantId(orderInfo.getTenantId());
				snapshot.setCreateTime(LocalDateTime.now());
				snapshot.setDelFlag("0");
				promotionSnapshotMapper.insert(snapshot);
			}
		}
		// 货到付款：跳过支付回调，商城配送/内部配送的配送任务在下单时直接创建（服务内幂等）
		if (cashOnDelivery) {
			deliveryTaskService.createTaskOnPay(orderInfo, orderItemEntityList);
		}
		if (MallOrderConstants.ORDER_CREATE_WAY_1.equals(createOrderDTO.getCreateWay())) {
			shoppingCartService.clear(orderInfo.getUserId(), orderItemEntityList.stream()
				.map(OrderItemEntity::getSkuId)
				.distinct()
				.toList());
		}
		applicationEventPublisher.publishEvent(
				new ArynOrderCreateAfterEvent(this, orderInfo, orderItemEntityList, createOrderDTO.getCreateWay()));
		// 积分扣减放在下单链路最后一步：此前券/营销/库存/明细任一失败都会回滚订单，
		// 提前扣减会在 cloud 模式（积分库独立提交）留下「订单没建成却扣了积分」的缺口。
		// 事件监听为 AFTER_COMMIT，不会在本事务内抛错；扣减失败则整单回滚（余额已被并发消耗）。
		if (orderInfo.getPointsUsed() != null && orderInfo.getPointsUsed() > 0) {
			remotePointsService.deductPoints(orderInfo.getUserId(), orderInfo.getPointsUsed(),
					"ORDER_DEDUCT:" + orderInfo.getOrderNo(), "订单积分抵现：" + orderInfo.getOrderNo());
		}
		return orderInfo;
	}

	/**
	 * 拼团单取价：校验拼团资格并返回下单上下文（含拼团单价与券/积分叠加策略）；非拼团单返回 null。
	 *
	 * <p>下单商品必须与活动 SKU 一致（活动为单 SKU 拼团），否则抛业务异常，
	 * 避免用户拿 A 商品的开团资格以拼团价购买 B 商品。
	 */
	private GroupBuyOrderContextVO resolveGroupBuyContext(String groupBuyRecordId, String userId,
			List<OrderItemEntity> orderItemEntityList) {
		if (!StringUtils.hasText(groupBuyRecordId)) {
			return null;
		}
		GroupBuyOrderContextVO context = remoteGroupBuyService.getOrderContext(groupBuyRecordId, userId);
		if (context == null) {
			throw new ArynBusinessException("拼团信息不存在，请重新开团或参团");
		}
		boolean skuMatched = orderItemEntityList.stream()
			.anyMatch(item -> item.getSkuId().equals(context.getSkuId()));
		if (!skuMatched) {
			throw new ArynBusinessException("下单商品与拼团活动商品不一致，无法按拼团价下单");
		}
		return context;
	}

	private OrderInfo generateOrder(CreateOrderDTO createOrderDTO) {
		OrderInfo orderInfo = new OrderInfo();
		BeanUtil.copyProperties(createOrderDTO, orderInfo);
		orderInfo.setId(IdUtil.getSnowflakeNextIdStr());
		orderInfo.setAppraiseStatus(CommonConstants.NO);
		orderInfo.setOrderNo(SnowflakeIdUtils.orderNo());
		orderInfo.setPaymentPrice(BigDecimal.ZERO);
		// 货到付款：下单即进入待发货，跳过待付款（入口已校验仅商城配送/内部配送可用）
		orderInfo.setStatus(MallOrderConstants.PAYMENT_TYPE_3.equals(createOrderDTO.getPaymentType())
				? OrderStatusEnum.WAITING_FOR_DELIVERY.getCode() : OrderStatusEnum.WAITING_FOR_PAYMENT.getCode());
		orderInfo.setTotalPrice(BigDecimal.ZERO);
		orderInfo.setFreightPrice(BigDecimal.ZERO);
		orderInfo.setCouponPrice(BigDecimal.ZERO);
		orderInfo.setMemberDiscountPrice(BigDecimal.ZERO);
		orderInfo.setPointsMultiplier(BigDecimal.ONE);
		orderInfo.setPointsUsed(0);
		orderInfo.setPointsDeductPrice(BigDecimal.ZERO);
		orderInfo.setPayStatus(CommonConstants.NO);
		orderInfo.setCouponUserId(createOrderDTO.getCouponUserId());
		orderInfo.setOpenId(createOrderDTO.getOpenId());
		orderInfo.setRequestId(createOrderDTO.getRequestId());
		return orderInfo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean receiveOrder(OrderInfo orderInfo) {
		// 送达守卫：商城配送/内部配送必须等司机点「已送达」后才能确认收货。
		// 订单在「司机出发」时就已进入待收货，若不加此守卫，司机未送达买家便能确认收货。
		// 快递（无内部配送任务）与自提（到店即交付）不受此限。
		if (!orderDeliveryStateService.isReadyToReceive(orderInfo)) {
			throw new ArynBusinessException("货物尚未送达，暂不能确认收货");
		}
		LocalDateTime receiverTime = LocalDateTime.now();
		// 已支付，或货到付款单（货已送达款项线下结算，收款由管理端确认）：均可确认收货
		int updated = baseMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())
			.and(wrapper -> wrapper.eq(OrderInfo::getPayStatus, CommonConstants.YES)
				.or()
				.eq(OrderInfo::getPaymentType, MallOrderConstants.PAYMENT_TYPE_3))
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
		// 返积分基数：按明细剔除积分黑名单（points_excluded=1）后的商品实付，事件中下发到用户域，
		// 消费累计（growth/goodsPaymentAmount）仍走全口径——成长值不受黑名单影响，只有积分发放剔除
		orderPaySuccessEvent.setGrantBaseAmount(resolveGrantBaseAmount(orderItemEntityList));
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

		// 共享购物车归档：整船订单送达后置为「已完成」，让船员看到「本次采购已送达」。
		// fail-open：归档失败不影响签收主流程，购物车状态可由运营后台或重试修正。
		try {
			ISharedCartService sharedCartService = sharedCartServiceProvider.getIfAvailable();
			if (sharedCartService != null) {
				sharedCartService.archiveOnOrderSigned(orderInfo.getId());
			}
		}
		catch (Exception e) {
			log.error("共享购物车签收归档失败: " + orderInfo.getId(), e);
		}
		return Boolean.TRUE;
	}

	/**
	 * 订单完成返积分基数：剔除命中积分黑名单（{@code points_excluded=1}）明细的商品实付金额。
	 *
	 * <p>明细商品实付 = {@code paymentPrice - freightPrice}（下限 0），与订单级
	 * {@code goodsPaymentAmount} 同构（仅按明细粒度拆分）；赠品为 0 元明细自然不产生基数。
	 * 存量订单（列上线前）points_excluded 为空，等价于全额计入，保持向后兼容。
	 */
	private BigDecimal resolveGrantBaseAmount(List<OrderItemEntity> orderItemEntityList) {
		BigDecimal grantBaseAmount = BigDecimal.ZERO;
		for (OrderItemEntity item : orderItemEntityList) {
			if (CommonConstants.YES.equals(item.getPointsExcluded())) {
				continue;
			}
			BigDecimal paymentPrice = item.getPaymentPrice() == null ? BigDecimal.ZERO : item.getPaymentPrice();
			BigDecimal freightPrice = item.getFreightPrice() == null ? BigDecimal.ZERO : item.getFreightPrice();
			grantBaseAmount = grantBaseAmount.add(paymentPrice.subtract(freightPrice).max(BigDecimal.ZERO));
		}
		return grantBaseAmount;
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
		validateAndApplyDeliveryContext(settlementOrderDTO.getPurchaseScene(), settlementOrderDTO.getUserId(),
				orderInfo);

		// 生成订单商品
		List<OrderItemEntity> orderItemEntityList = orderPriceComputeService.generateOrderItems(goodsSkuList,
				settlementOrderDTO.getSkuReqList());
		// 拼团价预取（拼团单）：与下单同口径，保证确认页展示金额与提交后一致
		GroupBuyOrderContextVO groupBuyContext = resolveGroupBuyContext(settlementOrderDTO.getGroupBuyRecordId(),
				settlementOrderDTO.getUserId(), orderItemEntityList);
		// 活动价预取：与下单同口径，整船优惠排除已享拼团/秒杀/限时折扣的 SKU，叠加策略取自该计划
		OrderPriceComputeService.PromotionPricePlan promotionPricePlan = orderPriceComputeService
			.resolvePromotionPricePlan(orderItemEntityList, groupBuyContext);
		// 营销阶梯价改基价（会员/券之前）
		PromotionCalculationVO promoCalculation = orderPriceComputeService.applyPromotionLadder(orderInfo,
				orderItemEntityList, promotionPricePlan);
		// 新人价命中（含叠加策略）补入计划：新人价由引擎落价，预取阶段未知
		orderPriceComputeService.mergeStackPolicyHits(promotionPricePlan, promoCalculation);
		// 促销价（拼团 > 秒杀 > 新人价 > 限时折扣 > 原价）与下单口径一致，须在会员折扣与优惠券之前应用，
		// 否则确认页展示金额与提交后的实际应付不一致；限购拦截只在提交订单时做，结算页不提前拦
		orderPriceComputeService.applyPromotionPricePlan(orderItemEntityList,
				orderPriceComputeService.newbiePriceSkuIds(promoCalculation), promotionPricePlan);

		orderPriceComputeService.computeOrderPrice(orderInfo, orderItemEntityList);
		MemberBenefitsVO memberBenefits = remoteMallUserService.getMemberBenefits(settlementOrderDTO.getUserId());
		orderPriceComputeService.orderMemberBenefitHandler(orderInfo, orderItemEntityList, memberBenefits,
				promotionPricePlan);
		// 非严格模式：不可叠活动商品的券不生效，返回原因供确认页置灰提示（提交时才硬拒绝）
		orderPriceComputeService.orderCouponHandler(orderInfo, orderItemEntityList, promotionPricePlan, false);
		// 整船/整单优惠分摊（券之后；活动价商品不参与）
		orderPriceComputeService.applyShipWholeDiscount(orderInfo, orderItemEntityList, promoCalculation,
				promotionPricePlan);
		// 买赠赠品预览（按真实库存过滤后给确认页展示，与提交同口径）
		orderInfo.setGiftItems(orderPriceComputeService.availableGifts(promoCalculation));
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
				throw new ArynBusinessException("当前收货地址不在公司配送（岸上）范围内");
			}
		}
		// 积分抵现：与下单同口径，保证确认页金额与提交后一致；叠加策略下积分只作用于允许积分的明细
		PointsDeductContextVO pointsContext = remotePointsService
			.getDeductContext(settlementOrderDTO.getUserId());
		// 积分黑名单与下单同口径解析：确认页 pointsStackable 置灰提示与提交后过滤一致
		Set<String> pointsExcludedSpuIds = orderPriceComputeService.resolvePointsExcludedSpuIds(goodsSkuList,
				pointsContext);
		orderPriceComputeService.orderPointsDeductHandler(orderInfo, orderItemEntityList,
				settlementOrderDTO.getUsePoints(), Boolean.TRUE.equals(settlementOrderDTO.getUseMaxPoints()),
				pointsContext, false, promotionPricePlan, pointsExcludedSpuIds);
		// 确认页划线价 / 活动优惠：成交价已覆盖原价，展示字段在金额全部算完后统一回填，
		// 与提交订单后的实际成交价同源，避免前端按商品页价格自行推算形成第二套口径
		orderPriceComputeService.fillOriginalPriceDisplay(orderInfo, orderItemEntityList, goodsSkuList);
		// 叠加策略展示：明细活动标签 + 券门槛可计算金额（券/积分不可用原因已由各 handler 写入）；
		// 传入黑名单让命中明细 pointsStackable 置 false，确认页提示「部分商品不参与积分抵扣」
		orderPriceComputeService.fillStackPolicyDisplay(orderInfo, orderItemEntityList, promotionPricePlan,
				pointsExcludedSpuIds);
		orderInfo.setOrderItemList(orderItemEntityList);
		orderItemEntityList.forEach(item -> item.setPurchaseScene(orderInfo.getPurchaseScene()));
		return orderInfo;
	}


	/**
	 * 买赠赠品追加为 0 元明细（不混入付费数量）；赠品 SKU 参与库存扣减。
	 *
	 * <p>赠品独立成行：与付费明细同 SKU 时也并存（订单明细支持同 SKU 多行，
	 * 库存按 SKU 聚合校验），历史实现把同车赠品静默丢弃导致「买 A 赠 A」不成立。
	 * 真实库存在 {@code availableGifts} 已校验，此处合成库存仅用于通过扣减口径。
	 */
	private void appendGiftItems(OrderInfo orderInfo, List<PromotionCalculationVO.GiftItem> gifts,
			List<OrderItemEntity> orderItemEntityList, List<GoodsSku> goodsSkuList) {
		List<String> giftSkuIds = gifts.stream()
			.map(PromotionCalculationVO.GiftItem::getSkuId)
			.distinct()
			.toList();
		if (giftSkuIds.isEmpty()) {
			return;
		}
		Map<String, GoodsSku> giftSkuMap = remoteGoodsSkuService.getBySkuIds(giftSkuIds).stream()
			.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		for (PromotionCalculationVO.GiftItem gift : gifts) {
			GoodsSku giftSku = giftSkuMap.get(gift.getSkuId());
			if (giftSku == null) {
				log.warn("买赠赠品SKU[" + gift.getSkuId() + "]不存在，跳过");
				continue;
			}
			OrderItemEntity giftItem = new OrderItemEntity();
			giftItem.setId(IdUtil.getSnowflakeNextIdStr());
			giftItem.setSkuId(giftSku.getId());
			giftItem.setSpuId(giftSku.getGoodsSpu() != null ? giftSku.getGoodsSpu().getId() : giftSku.getSpuId());
			giftItem.setSpuName(giftSku.getGoodsSpu() != null ? giftSku.getGoodsSpu().getName() : "");
			giftItem.setPicUrl(giftSku.getPicUrl());
			giftItem.setBuyQuantity(gift.getQuantity());
			giftItem.setSalesPrice(BigDecimal.ZERO);
			giftItem.setTotalPrice(BigDecimal.ZERO);
			giftItem.setFreightPrice(BigDecimal.ZERO);
			giftItem.setCouponPrice(BigDecimal.ZERO);
			giftItem.setMemberDiscountPrice(BigDecimal.ZERO);
			giftItem.setPointsPrice(BigDecimal.ZERO);
			giftItem.setPromoPrice(BigDecimal.ZERO);
			giftItem.setPaymentPrice(BigDecimal.ZERO);
			giftItem.setGiftFlag("1");
			giftItem.setTenantId(orderInfo.getTenantId());
			orderItemEntityList.add(giftItem);
			GoodsSku giftStockSku = new GoodsSku();
			giftStockSku.setId(giftSku.getId());
			giftStockSku.setSpuId(giftItem.getSpuId());
			giftStockSku.setStock(gift.getQuantity());
			goodsSkuList.add(giftStockSku);
		}
	}

	/**
	 * 购买场景与配送上下文校验：结算与下单共用同一校验；内部配送以服务端船舶域数据落快照，
	 * 港口、泊位、时间窗不从客户端取值。
	 *
	 * <p>同时归一购买场景：未声明的按海员个人购买落库，保证订单场景恒为 1/2，
	 * 管理端列表、导出与场景筛选才有稳定口径。
	 */
	private void validateAndApplyDeliveryContext(String purchaseScene, String userId, OrderInfo orderInfo) {
		purchaseSceneValidator.validate(purchaseScene, orderInfo.getDeliveryWay());
		orderInfo.setPurchaseScene(purchaseSceneValidator.normalize(purchaseScene));
		VesselContextDTO context = deliveryContextValidator.validate(ArynTenantContextHolder.getTenantId(), userId,
				orderInfo.getDeliveryWay(), orderInfo.getVesselId(), orderInfo.getVesselCallId());
		if (context != null) {
			orderInfo.setVesselId(context.getVesselId());
			orderInfo.setVesselName(context.getVesselName());
			orderInfo.setVesselCallId(context.getVesselCallId());
			orderInfo.setPortCode(context.getPortCode());
			orderInfo.setPortName(context.getPortName());
			orderInfo.setBerth(context.getBerth());
			orderInfo.setDeliveryWindowStart(context.getDeliveryWindowStart());
			orderInfo.setDeliveryWindowEnd(context.getDeliveryWindowEnd());
		}
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


	@Override
	public List<com.aryn.cloud.order.api.vo.FrequentPurchaseVO> frequentPurchase(String tenantId, String userId) {
		return orderItemMapper.selectFrequentPurchase(tenantId, userId, LocalDateTime.now().minusDays(90), 20);
	}

	@Override
	public com.aryn.cloud.order.api.vo.ReorderPreviewVO reorderPreview(String tenantId, String userId, String orderId) {
		OrderInfo orderInfo = getOne(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getTenantId, tenantId)
			.eq(OrderInfo::getId, orderId)
			.eq(OrderInfo::getUserId, userId));
		if (orderInfo == null) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60003.getCode(), MallErrorCodeEnum.ERROR_60003.getMsg());
		}
		List<OrderItemEntity> items = orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
			.eq(OrderItemEntity::getTenantId, tenantId)
			.eq(OrderItemEntity::getOrderId, orderId));
		List<String> skuIds = items.stream().map(OrderItemEntity::getSkuId).distinct().toList();
		List<GoodsSku> skus = skuIds.isEmpty() ? List.of() : remoteGoodsSkuService.getBySkuIds(skuIds);
		Map<String, GoodsSku> skuMap = skus.stream()
			.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));

		com.aryn.cloud.order.api.vo.ReorderPreviewVO preview = new com.aryn.cloud.order.api.vo.ReorderPreviewVO();
		preview.setOrderId(orderInfo.getId());
		preview.setOrderNo(orderInfo.getOrderNo());
		preview.setPurchaseScene(orderInfo.getPurchaseScene());
		preview.setItems(items.stream().map(item -> {
			com.aryn.cloud.order.api.vo.ReorderPreviewVO.ReorderItem reorderItem = new com.aryn.cloud.order.api.vo.ReorderPreviewVO.ReorderItem();
			reorderItem.setSkuId(item.getSkuId());
			reorderItem.setSpuId(item.getSpuId());
			reorderItem.setSpuName(item.getSpuName());
			reorderItem.setSpecsInfo(item.getSpecsInfo());
			reorderItem.setPicUrl(item.getPicUrl());
			reorderItem.setOriginalQuantity(item.getBuyQuantity());
			GoodsSku sku = skuMap.get(item.getSkuId());
			// getBySkuIds 只回在售 SKU（goods_sku.status='0' 且 goods_spu.status='1'，两表 status 语义相反），
			// 查不到即已删除或 SKU/SPU 任一级下架，不能再按 status 重复判定
			if (sku == null) {
				reorderItem.setPurchasable(false);
				reorderItem.setReason("商品已下架或已删除");
				return reorderItem;
			}
			reorderItem.setCurrentPrice(sku.getSalesPrice());
			reorderItem.setCurrentStock(sku.getStock());
			reorderItem.setPriceChanged(sku.getSalesPrice() != null && item.getSalesPrice() != null
					&& sku.getSalesPrice().compareTo(item.getSalesPrice()) != 0);
			if (sku.getStock() == null || sku.getStock() < item.getBuyQuantity()) {
				reorderItem.setPurchasable(false);
				reorderItem.setReason("库存不足");
			}
			else {
				reorderItem.setPurchasable(true);
			}
			return reorderItem;
		}).toList());
		return preview;
	}

}
