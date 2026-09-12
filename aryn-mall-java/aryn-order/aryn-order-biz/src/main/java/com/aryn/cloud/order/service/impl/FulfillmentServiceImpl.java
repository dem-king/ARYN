package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.FulfillmentExceptionDTO;
import com.aryn.cloud.order.api.dto.FulfillmentPickScanDTO;
import com.aryn.cloud.order.api.dto.FulfillmentShortReportDTO;
import com.aryn.cloud.order.api.dto.FulfillmentWaveCreateDTO;
import com.aryn.cloud.order.api.entity.FulfillmentException;
import com.aryn.cloud.order.api.entity.FulfillmentPickItem;
import com.aryn.cloud.order.api.entity.FulfillmentWave;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.FulfillmentExceptionMapper;
import com.aryn.cloud.order.mapper.FulfillmentPickItemMapper;
import com.aryn.cloud.order.mapper.FulfillmentWaveMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.IFulfillmentService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 仓库履约服务实现。
 *
 * <p>状态机：待配货 → 拣货中 → 已复核 → 已交司机 → 配送中 → 已交付（异常/短装分支）。
 * 扫码校验 SKU 与数量上限；交接司机后明细锁定；短装必须记录原因和实际数量。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentServiceImpl implements IFulfillmentService {

	private final FulfillmentWaveMapper fulfillmentWaveMapper;

	private final FulfillmentPickItemMapper fulfillmentPickItemMapper;

	private final FulfillmentExceptionMapper fulfillmentExceptionMapper;

	private final OrderInfoMapper orderInfoMapper;

	private final OrderItemMapper orderItemMapper;

	private final com.aryn.cloud.order.mapper.SharedCartMapper sharedCartMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentWave createWave(String tenantId, String operatorId, String operatorName,
			FulfillmentWaveCreateDTO dto) {
		FulfillmentWave wave = new FulfillmentWave();
		wave.setId(IdWorker.getIdStr());
		wave.setWaveNo("WV" + IdWorker.getIdStr());
		wave.setWarehouseId(dto.getWarehouseId());
		wave.setPortCode(dto.getPortCode());
		wave.setPortName(dto.getPortName());
		wave.setVesselCallId(dto.getVesselCallId());
		wave.setStatus(FulfillmentWave.STATUS_PENDING_PICK);
		wave.setPlanDeliveryTime(dto.getPlanDeliveryTime());
		wave.setRemark(dto.getRemark());
		wave.setOperatorId(operatorId);
		wave.setOperatorName(operatorName);
		wave.setVersion(0);
		wave.setTenantId(tenantId);
		wave.setCreateTime(LocalDateTime.now());
		wave.setDelFlag("0");
		fulfillmentWaveMapper.insert(wave);
		return wave;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<FulfillmentPickItem> addOrderToWave(String tenantId, String waveId, String orderId) {
		FulfillmentWave wave = requireWave(tenantId, waveId);
		if (!FulfillmentWave.STATUS_PENDING_PICK.equals(wave.getStatus())) {
			throw new ArynBusinessException("波次已开始拣货，不能再加入订单");
		}
		OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
		if (orderInfo == null || !Objects.equals(orderInfo.getTenantId(), tenantId)) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!OrderStatusEnum.WAITING_FOR_DELIVERY.getCode().equals(orderInfo.getStatus())) {
			throw new ArynBusinessException("订单当前状态不能加入波次");
		}
		if (!Objects.equals(orderInfo.getPortCode(), wave.getPortCode())) {
			throw new ArynBusinessException("订单目的港口与波次港口不一致");
		}
		Long duplicated = fulfillmentPickItemMapper.selectCount(Wrappers.lambdaQuery(FulfillmentPickItem.class)
				.eq(FulfillmentPickItem::getTenantId, tenantId)
				.eq(FulfillmentPickItem::getOrderId, orderId)
				.eq(FulfillmentPickItem::getDelFlag, "0"));
		if (duplicated != null && duplicated > 0) {
			throw new ArynBusinessException("订单已加入其他波次");
		}
		List<OrderItemEntity> orderItems = orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItemEntity.class)
				.eq(OrderItemEntity::getTenantId, tenantId)
				.eq(OrderItemEntity::getOrderId, orderId));
		if (CollectionUtils.isEmpty(orderItems)) {
			throw new ArynBusinessException("订单没有可拣货明细");
		}
		List<FulfillmentPickItem> pickItems = orderItems.stream().map(orderItem -> {
			FulfillmentPickItem pickItem = new FulfillmentPickItem();
			pickItem.setId(IdWorker.getIdStr());
			pickItem.setWaveId(waveId);
			pickItem.setOrderId(orderId);
			pickItem.setOrderItemId(orderItem.getId());
			pickItem.setSkuId(orderItem.getSkuId());
			pickItem.setSpuName(orderItem.getSpuName());
			pickItem.setSkuName(orderItem.getSpecsInfo());
			// 条码快照优先取 SKU ID（扫码端同时接受条码或 SKU ID）
			pickItem.setSkuBarcode(orderItem.getSkuId());
			pickItem.setRequiredQuantity(orderItem.getBuyQuantity());
			pickItem.setPickedQuantity(0);
			pickItem.setShortQuantity(0);
			pickItem.setPickStatus(FulfillmentPickItem.PICK_PENDING);
			pickItem.setTenantId(tenantId);
			pickItem.setCreateTime(LocalDateTime.now());
			pickItem.setDelFlag("0");
			fulfillmentPickItemMapper.insert(pickItem);
			return pickItem;
		}).toList();

		wave.setStatus(FulfillmentWave.STATUS_PICKING);
		fulfillmentWaveMapper.updateById(wave);
		return pickItems;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentPickItem scanPick(String tenantId, String pickerId, FulfillmentPickScanDTO dto) {
		FulfillmentWave wave = requireWave(tenantId, dto.getWaveId());
		requireWaveEditableByWarehouse(wave);
		FulfillmentPickItem item = requirePickItem(tenantId, dto.getItemId());
		if (!Objects.equals(item.getWaveId(), wave.getId())) {
			throw new ArynBusinessException("拣货明细不属于该波次");
		}
		if (!Objects.equals(item.getSkuBarcode(), dto.getScannedCode())
				&& !Objects.equals(item.getSkuId(), dto.getScannedCode())) {
			throw new ArynBusinessException("扫码 SKU 与拣货明细不匹配");
		}
		if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
			throw new ArynBusinessException("扫码数量必须大于0");
		}
		int picked = item.getPickedQuantity() != null ? item.getPickedQuantity() : 0;
		if (picked + dto.getQuantity() > item.getRequiredQuantity()) {
			throw new ArynBusinessException("扫码数量超过应拣数量");
		}
		item.setPickedQuantity(picked + dto.getQuantity());
		if (item.getPickedQuantity().equals(item.getRequiredQuantity())) {
			item.setPickStatus(FulfillmentPickItem.PICK_DONE);
		}
		item.setScannedTime(LocalDateTime.now());
		item.setPickerId(pickerId);
		fulfillmentPickItemMapper.updateById(item);
		return item;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentPickItem reportShort(String tenantId, String pickerId, FulfillmentShortReportDTO dto) {
		FulfillmentWave wave = requireWave(tenantId, dto.getWaveId());
		requireWaveEditableByWarehouse(wave);
		FulfillmentPickItem item = requirePickItem(tenantId, dto.getItemId());
		if (!Objects.equals(item.getWaveId(), wave.getId())) {
			throw new ArynBusinessException("拣货明细不属于该波次");
		}
		if (!StringUtils.hasText(dto.getReasonCode())) {
			throw new ArynBusinessException("短装必须选择原因");
		}
		int actual = dto.getActualQuantity() != null ? dto.getActualQuantity() : 0;
		if (actual < 0 || actual > item.getRequiredQuantity()) {
			throw new ArynBusinessException("实际数量超出应拣范围");
		}
		item.setPickedQuantity(actual);
		item.setShortQuantity(item.getRequiredQuantity() - actual);
		item.setShortReasonCode(dto.getReasonCode());
		item.setShortReasonDesc(dto.getReasonDesc());
		item.setSubstitutedSkuId(dto.getSubstitutedSkuId());
		item.setPickStatus(StringUtils.hasText(dto.getSubstitutedSkuId()) ? FulfillmentPickItem.PICK_REPLACED
				: FulfillmentPickItem.PICK_SHORT);
		item.setScannedTime(LocalDateTime.now());
		item.setPickerId(pickerId);
		fulfillmentPickItemMapper.updateById(item);

		FulfillmentException exception = new FulfillmentException();
		exception.setId(IdWorker.getIdStr());
		exception.setWaveId(wave.getId());
		exception.setOrderId(item.getOrderId());
		exception.setExceptionType(FulfillmentException.TYPE_SHORT_PICK);
		String reason = StringUtils.hasText(dto.getReasonDesc()) ? dto.getReasonDesc() : dto.getReasonCode();
		exception.setDescription("SKU[" + item.getSkuId() + "]短装 " + item.getShortQuantity() + "，原因：" + reason);
		exception.setStatus(FulfillmentException.STATUS_OPEN);
		exception.setTenantId(tenantId);
		exception.setCreateBy(pickerId);
		exception.setCreateTime(LocalDateTime.now());
		exception.setDelFlag("0");
		fulfillmentExceptionMapper.insert(exception);
		return item;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentWave reviewWave(String tenantId, String operatorId, String operatorName, String waveId) {
		FulfillmentWave wave = requireWave(tenantId, waveId);
		if (!FulfillmentWave.STATUS_PICKING.equals(wave.getStatus())) {
			throw new ArynBusinessException("波次当前状态不能复核");
		}
		List<FulfillmentPickItem> items = listWaveItems(tenantId, waveId);
		boolean hasUnfinished = items.stream()
			.anyMatch(item -> FulfillmentPickItem.PICK_PENDING.equals(item.getPickStatus()));
		if (hasUnfinished) {
			throw new ArynBusinessException("存在未拣完的明细，不能复核");
		}
		wave.setStatus(FulfillmentWave.STATUS_REVIEWED);
		wave.setPickedTime(LocalDateTime.now());
		wave.setReviewedTime(LocalDateTime.now());
		wave.setOperatorId(operatorId);
		wave.setOperatorName(operatorName);
		fulfillmentWaveMapper.updateById(wave);
		return wave;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentWave handOverToDriver(String tenantId, String operatorId, String operatorName, String waveId,
			String staffId) {
		FulfillmentWave wave = requireWave(tenantId, waveId);
		if (!FulfillmentWave.STATUS_REVIEWED.equals(wave.getStatus())) {
			throw new ArynBusinessException("波次必须先复核才能交接司机");
		}
		wave.setStatus(FulfillmentWave.STATUS_HANDED_OVER);
		wave.setHandedOverTime(LocalDateTime.now());
		wave.setOperatorId(operatorId);
		wave.setOperatorName(operatorName);
		wave.setRemark(appendRemark(wave.getRemark(), "交接司机:" + staffId));
		fulfillmentWaveMapper.updateById(wave);
		return wave;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentException reportException(String tenantId, String reporterId, FulfillmentExceptionDTO dto) {
		FulfillmentException exception = new FulfillmentException();
		exception.setId(IdWorker.getIdStr());
		exception.setWaveId(dto.getWaveId());
		exception.setOrderId(dto.getOrderId());
		exception.setTaskId(dto.getTaskId());
		exception.setExceptionType(dto.getExceptionType());
		exception.setDescription(dto.getDescription());
		exception.setEvidenceUrls(dto.getEvidenceUrls());
		exception.setStatus(FulfillmentException.STATUS_OPEN);
		exception.setTenantId(tenantId);
		exception.setCreateBy(reporterId);
		exception.setCreateTime(LocalDateTime.now());
		exception.setDelFlag("0");
		fulfillmentExceptionMapper.insert(exception);
		return exception;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public FulfillmentException closeException(String tenantId, String handlerId, String exceptionId,
			String handleRemark) {
		FulfillmentException exception = fulfillmentExceptionMapper.selectOne(Wrappers.lambdaQuery(FulfillmentException.class)
				.eq(FulfillmentException::getTenantId, tenantId)
				.eq(FulfillmentException::getId, exceptionId));
		if (exception == null) {
			throw new ArynBusinessException("异常不存在");
		}
		exception.setStatus(FulfillmentException.STATUS_CLOSED);
		exception.setHandlerId(handlerId);
		exception.setHandleRemark(handleRemark);
		exception.setHandledTime(LocalDateTime.now());
		fulfillmentExceptionMapper.updateById(exception);
		return exception;
	}

	@Override
	public IPage<FulfillmentWave> wavePage(String tenantId, IPage<FulfillmentWave> page, FulfillmentWave query) {
		return fulfillmentWaveMapper.selectPage(page, Wrappers.lambdaQuery(FulfillmentWave.class)
				.eq(FulfillmentWave::getTenantId, tenantId)
				.eq(StringUtils.hasText(query.getStatus()), FulfillmentWave::getStatus, query.getStatus())
				.eq(StringUtils.hasText(query.getPortCode()), FulfillmentWave::getPortCode, query.getPortCode())
				.eq(StringUtils.hasText(query.getVesselCallId()), FulfillmentWave::getVesselCallId,
						query.getVesselCallId())
				.orderByAsc(FulfillmentWave::getPlanDeliveryTime));
	}

	@Override
	public List<FulfillmentPickItem> listWaveItems(String tenantId, String waveId) {
		return fulfillmentPickItemMapper.selectList(Wrappers.lambdaQuery(FulfillmentPickItem.class)
				.eq(FulfillmentPickItem::getTenantId, tenantId)
				.eq(FulfillmentPickItem::getWaveId, waveId)
				.orderByAsc(FulfillmentPickItem::getCreateTime));
	}

	@Override
	public IPage<FulfillmentException> exceptionPage(String tenantId, IPage<FulfillmentException> page,
			FulfillmentException query) {
		return fulfillmentExceptionMapper.selectPage(page, Wrappers.lambdaQuery(FulfillmentException.class)
				.eq(FulfillmentException::getTenantId, tenantId)
				.eq(StringUtils.hasText(query.getStatus()), FulfillmentException::getStatus, query.getStatus())
				.eq(StringUtils.hasText(query.getOrderId()), FulfillmentException::getOrderId, query.getOrderId())
				.orderByDesc(FulfillmentException::getCreateTime));
	}

	private FulfillmentWave requireWave(String tenantId, String waveId) {
		FulfillmentWave wave = fulfillmentWaveMapper.selectOne(Wrappers.lambdaQuery(FulfillmentWave.class)
				.eq(FulfillmentWave::getTenantId, tenantId)
				.eq(FulfillmentWave::getId, waveId));
		if (wave == null) {
			throw new ArynBusinessException("拣货波次不存在");
		}
		return wave;
	}

	private FulfillmentPickItem requirePickItem(String tenantId, String itemId) {
		FulfillmentPickItem item = fulfillmentPickItemMapper.selectOne(Wrappers.lambdaQuery(FulfillmentPickItem.class)
				.eq(FulfillmentPickItem::getTenantId, tenantId)
				.eq(FulfillmentPickItem::getId, itemId));
		if (item == null) {
			throw new ArynBusinessException("拣货明细不存在");
		}
		return item;
	}

	/**
	 * 交接司机后仓库不能再修改已交接明细。
	 */
	private void requireWaveEditableByWarehouse(FulfillmentWave wave) {
		if (FulfillmentWave.STATUS_HANDED_OVER.equals(wave.getStatus())
				|| FulfillmentWave.STATUS_COMPLETED.equals(wave.getStatus())
				|| FulfillmentWave.STATUS_CANCELED.equals(wave.getStatus())) {
			throw new ArynBusinessException("波次已交接司机或结束，不能再修改明细");
		}
	}

	private String appendRemark(String remark, String addition) {
		String text = StringUtils.hasText(remark) ? remark + "；" + addition : addition;
		return text.length() > 500 ? text.substring(0, 500) : text;
	}


	@Override
	public java.util.Map<String, Object> portBoard(String tenantId, String portCode, java.time.LocalDate date) {
		if (!StringUtils.hasText(portCode) || date == null) {
			throw new ArynBusinessException("看板必须指定港口与日期");
		}
		java.time.LocalDateTime dayStart = date.atStartOfDay();
		java.time.LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

		// 波次（计划交付在当日，按港口）
		List<FulfillmentWave> waves = fulfillmentWaveMapper.selectList(Wrappers.lambdaQuery(FulfillmentWave.class)
				.eq(FulfillmentWave::getTenantId, tenantId)
				.eq(FulfillmentWave::getPortCode, portCode)
				.ge(FulfillmentWave::getPlanDeliveryTime, dayStart)
				.lt(FulfillmentWave::getPlanDeliveryTime, dayEnd)
				.orderByAsc(FulfillmentWave::getPlanDeliveryTime));

		// 配送任务（按港口与时间窗快照聚合状态）
		List<FulfillmentPickItem> waveItems = waves.isEmpty() ? List.of()
				: fulfillmentPickItemMapper.selectList(Wrappers.lambdaQuery(FulfillmentPickItem.class)
						.eq(FulfillmentPickItem::getTenantId, tenantId)
						.in(FulfillmentPickItem::getWaveId, waves.stream().map(FulfillmentWave::getId).toList()));

		long pendingOrders = waves.stream()
			.filter(wave -> FulfillmentWave.STATUS_PENDING_PICK.equals(wave.getStatus())
					|| FulfillmentWave.STATUS_PICKING.equals(wave.getStatus()))
			.count();
		long handedOver = waves.stream()
			.filter(wave -> FulfillmentWave.STATUS_HANDED_OVER.equals(wave.getStatus()))
			.count();
		long completed = waves.stream()
			.filter(wave -> FulfillmentWave.STATUS_COMPLETED.equals(wave.getStatus()))
			.count();
		long shortItems = waveItems.stream()
			.filter(item -> FulfillmentPickItem.PICK_SHORT.equals(item.getPickStatus()))
			.count();

		java.util.Map<String, Object> board = new java.util.HashMap<>();
		board.put("portCode", portCode);
		board.put("date", date.toString());
		board.put("waveCount", waves.size());
		board.put("pendingWaves", pendingOrders);
		board.put("handedOverWaves", handedOver);
		board.put("completedWaves", completed);
		board.put("shortItemCount", shortItems);
		board.put("waves", waves);
		return board;
	}


	@Override
	public java.util.Map<String, Object> vesselCallImpact(String tenantId, String vesselCallId) {
		if (!StringUtils.hasText(vesselCallId)) {
			throw new ArynBusinessException("靠港计划ID不能为空");
		}
		List<FulfillmentWave> waves = fulfillmentWaveMapper.selectList(Wrappers.lambdaQuery(FulfillmentWave.class)
				.eq(FulfillmentWave::getTenantId, tenantId)
				.eq(FulfillmentWave::getVesselCallId, vesselCallId)
				.in(FulfillmentWave::getStatus, List.of(FulfillmentWave.STATUS_PENDING_PICK,
						FulfillmentWave.STATUS_PICKING, FulfillmentWave.STATUS_REVIEWED)));
		java.util.List<com.aryn.cloud.order.api.entity.OrderInfo> orders = orderInfoMapper.selectList(
				Wrappers.lambdaQuery(com.aryn.cloud.order.api.entity.OrderInfo.class)
						.eq(com.aryn.cloud.order.api.entity.OrderInfo::getTenantId, tenantId)
						.eq(com.aryn.cloud.order.api.entity.OrderInfo::getVesselCallId, vesselCallId)
						.in(com.aryn.cloud.order.api.entity.OrderInfo::getStatus, List.of("2", "3"))
						.eq(com.aryn.cloud.order.api.entity.OrderInfo::getDelFlag, "0"));
		java.util.List<com.aryn.cloud.order.api.entity.SharedCart> carts = java.util.List.of();
		try {
			carts = sharedCartMapper.selectList(Wrappers.lambdaQuery(com.aryn.cloud.order.api.entity.SharedCart.class)
					.eq(com.aryn.cloud.order.api.entity.SharedCart::getTenantId, tenantId)
					.eq(com.aryn.cloud.order.api.entity.SharedCart::getVesselCallId, vesselCallId)
					.in(com.aryn.cloud.order.api.entity.SharedCart::getStatus,
							List.of(com.aryn.cloud.order.api.entity.SharedCart.STATUS_COLLECTING,
									com.aryn.cloud.order.api.entity.SharedCart.STATUS_WAITING_CONFIRM))
					.eq(com.aryn.cloud.order.api.entity.SharedCart::getDelFlag, "0"));
		}
		catch (Exception ex) {
			log.warn("查询共享购物车影响面失败（可忽略）", ex);
		}
		java.util.Map<String, Object> impact = new java.util.HashMap<>();
		impact.put("vesselCallId", vesselCallId);
		impact.put("orderCount", orders.size());
		impact.put("orderNos", orders.stream()
				.map(com.aryn.cloud.order.api.entity.OrderInfo::getOrderNo).limit(10).toList());
		impact.put("sharedCartCount", carts.size());
		impact.put("waveCount", waves.size());
		impact.put("waveNos", waves.stream().map(FulfillmentWave::getWaveNo).limit(10).toList());
		return impact;
	}

}
