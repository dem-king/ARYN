package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.OrderAdvanceAppraiseDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseEditDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单评价服务。
 *
 * <p>对齐行业口径（淘宝/有赞）：
 * <ul>
 * <li>星级必填、文字选填，空白文字由服务端写入默认文案；</li>
 * <li>允许部分评价（提交订单项子集），全部订单项都有有效评价后才把订单置为已评价；</li>
 * <li>系统默认评价单独标记 {@code appraise_type=1} 且不打分，避免污染好评率；</li>
 * <li>追评/修改/删除窗口均为评价发布后 30 天，追评与修改各仅一次，删除为逻辑删除。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAppraiseService {

	/** 用户主动评价 */
	private static final int APPRAISE_TYPE_USER = 0;

	/** 系统默认评价（超时未评），不计分、不参与好评率统计 */
	private static final int APPRAISE_TYPE_SYSTEM = 1;

	/** 文字评价选填，空白时落库的兜底文案（保证 content NOT NULL） */
	private static final String DEFAULT_APPRAISE_CONTENT = "此用户未填写评价内容";

	/** 追评文字选填，空白时落库的兜底文案 */
	private static final String DEFAULT_ADVANCE_CONTENT = "此用户未填写追评内容";

	/** 系统默认好评文案（不打分） */
	private static final String DEFAULT_AUTO_APPRAISE_CONTENT = "系统默认好评";

	/** 追评/修改/删除操作窗口（天） */
	private static final int APPRAISE_OPERATE_WINDOW_DAYS = 30;

	/** 评价标签最多 5 个，单个标签最长 10 字 */
	private static final int MAX_TAGS = 5;

	private static final int MAX_TAG_LENGTH = 10;

	private final OrderInfoMapper orderInfoMapper;

	private final OrderItemMapper orderItemMapper;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	@DubboReference
	private final RemoteGoodsAppraiseService remoteGoodsAppraiseService;

	/**
	 * 订单评价：接受订单项子集，已评过的订单项幂等跳过；全部订单项都有有效评价才把订单置为已评价。
	 */
	public boolean appraiseOrder(String id, String userId, List<OrderAppraiseDTO> orderAppraiseList) {
		OrderInfo orderInfo = orderInfoMapper.selectById(id);
		if (Objects.isNull(orderInfo) || !userId.equals(orderInfo.getUserId())) {
			return false;
		}
		if (!OrderStatusEnum.COMPLETED.getCode().equals(orderInfo.getStatus())) {
			throw new ArynBusinessException("订单尚未完成，不能评价");
		}
		List<OrderItemEntity> orderItems = orderItemMapper.selectByOrderId(id);
		if (orderAppraiseList == null || orderAppraiseList.isEmpty()
				|| orderItems == null || orderItems.isEmpty()) {
			throw new ArynBusinessException("评价内容不能为空");
		}
		Map<String, OrderItemEntity> orderItemMap = orderItems.stream()
			.collect(Collectors.toMap(OrderItemEntity::getId, Function.identity()));
		// 请求项必须属于该订单、且不重复；客户端传入的 orderId/spuId/userId 一律忽略
		Set<String> requestedItemIds = new HashSet<>();
		for (OrderAppraiseDTO appraise : orderAppraiseList) {
			if (Objects.isNull(appraise) || !StringUtils.hasText(appraise.getOrderItemId())) {
				throw new ArynBusinessException("评价商品与订单不匹配");
			}
			if (!orderItemMap.containsKey(appraise.getOrderItemId())) {
				throw new ArynBusinessException("评价商品与订单不匹配");
			}
			if (!requestedItemIds.add(appraise.getOrderItemId())) {
				throw new ArynBusinessException("评价商品不能重复提交");
			}
		}
		// 部分评价幂等：已评过的订单项不重复插入（避免撞 uk_goods_appraise_active_item）；
		// 全部是重复提交时不再查用户信息，直接刷新订单状态后幂等返回
		Set<String> appraisedItemIds = listAppraisedItemIds(id);
		List<OrderAppraiseDTO> pendingAppraiseList = orderAppraiseList.stream()
			.filter(appraise -> !appraisedItemIds.contains(appraise.getOrderItemId()))
			.collect(Collectors.toList());
		if (!pendingAppraiseList.isEmpty()) {
			UserInfoVO userInfo = remoteMallUserService.getUserById(userId);
			if (Objects.isNull(userInfo)) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50001.getMsg());
			}
			List<GoodsAppraise> goodsAppraiseList = pendingAppraiseList.stream()
				.map(appraise -> buildUserAppraise(appraise, orderInfo,
						orderItemMap.get(appraise.getOrderItemId()), userInfo))
				.collect(Collectors.toList());
			if (!remoteGoodsAppraiseService.addGoodsAppraise(goodsAppraiseList)) {
				throw new ArynBusinessException("订单评价失败");
			}
		}
		// 全部订单项都有有效评价才置已评价，否则保持待评价（支持稍后评）
		refreshOrderAppraiseStatus(orderInfo, userId);
		log.info("订单评价完成，orderId={}，本次提交={} 项，其中新插入={} 项", id, requestedItemIds.size(),
				pendingAppraiseList.size());
		return true;
	}

	/**
	 * 追评：评价发布后 30 天内、且该评价尚未追评过。
	 */
	public boolean advanceAppraiseOrder(String orderId, String orderItemId, String userId,
			OrderAdvanceAppraiseDTO advanceAppraiseDTO) {
		if (Objects.isNull(advanceAppraiseDTO) || !StringUtils.hasText(orderItemId)) {
			throw new ArynBusinessException("追评商品不能为空");
		}
		requireUserOrder(orderId, userId);
		GoodsAppraise appraise = requireOrderItemAppraise(orderId, orderItemId);
		checkOperateWindow(appraise, "追评");
		if (Objects.nonNull(appraise.getAdvanceTime())) {
			throw new ArynBusinessException("该商品已追评，不能重复追评");
		}
		appraise.setAdvanceContent(defaultContent(advanceAppraiseDTO.getContent(), DEFAULT_ADVANCE_CONTENT));
		String[] advancePicUrls = toArray(advanceAppraiseDTO.getPicUrls());
		if (advancePicUrls != null) {
			appraise.setAdvancePicUrls(advancePicUrls);
		}
		appraise.setAdvanceTime(LocalDateTime.now());
		if (!remoteGoodsAppraiseService.updateById(appraise)) {
			throw new ArynBusinessException("追评失败");
		}
		return true;
	}

	/**
	 * 修改评价：评价发布后 30 天内、且仅允许修改一次。
	 */
	public boolean editAppraiseOrder(String orderId, String orderItemId, String userId,
			OrderAppraiseEditDTO editAppraiseDTO) {
		if (Objects.isNull(editAppraiseDTO) || !StringUtils.hasText(orderItemId)) {
			throw new ArynBusinessException("修改的商品不能为空");
		}
		requireUserOrder(orderId, userId);
		GoodsAppraise appraise = requireOrderItemAppraise(orderId, orderItemId);
		// 系统默认评价是超时兜底产物，不参与用户内容修改（前端同样隐藏入口，服务端再兜一层）
		if (Integer.valueOf(APPRAISE_TYPE_SYSTEM).equals(appraise.getAppraiseType())) {
			throw new ArynBusinessException("系统默认评价不支持修改");
		}
		checkOperateWindow(appraise, "修改");
		if (Objects.nonNull(appraise.getModifyTime())) {
			throw new ArynBusinessException("评价只能修改一次，不能再次修改");
		}
		if (Objects.nonNull(editAppraiseDTO.getContent())) {
			appraise.setContent(defaultContent(editAppraiseDTO.getContent(), DEFAULT_APPRAISE_CONTENT));
		}
		String[] picUrls = toArray(editAppraiseDTO.getPicUrls());
		if (picUrls != null) {
			appraise.setPicUrls(picUrls);
		}
		if (Objects.nonNull(editAppraiseDTO.getGoodsScore())) {
			appraise.setGoodsScore(editAppraiseDTO.getGoodsScore());
		}
		appraise.setModifyTime(LocalDateTime.now());
		if (!remoteGoodsAppraiseService.updateById(appraise)) {
			throw new ArynBusinessException("评价修改失败");
		}
		return true;
	}

	/**
	 * 删除评价：逻辑删除；删除后订单不再「全部已评」，appraise_status 回落到待评价。
	 */
	public boolean deleteAppraiseOrder(String orderId, String orderItemId, String userId) {
		if (!StringUtils.hasText(orderItemId)) {
			throw new ArynBusinessException("删除的商品不能为空");
		}
		OrderInfo orderInfo = requireUserOrder(orderId, userId);
		GoodsAppraise appraise = findOrderItemAppraise(orderId, orderItemId);
		if (Objects.isNull(appraise)) {
			// 已删除：幂等成功
			return true;
		}
		// 系统默认评价不可删除（前端隐藏入口，服务端兜底），避免删掉超时兜底记录后订单重新变成待评价
		if (Integer.valueOf(APPRAISE_TYPE_SYSTEM).equals(appraise.getAppraiseType())) {
			throw new ArynBusinessException("系统默认评价不支持删除");
		}
		checkOperateWindow(appraise, "删除");
		if (!remoteGoodsAppraiseService.removeById(appraise.getId())) {
			throw new ArynBusinessException("评价删除失败");
		}
		refreshOrderAppraiseStatus(orderInfo, userId);
		return true;
	}

	/**
	 * 系统自动评价：超时未评订单按订单项补系统默认评价。
	 * <p>默认评价 {@code appraise_type=1}、匿名、不打分（goodsScore=null），避免污染好评率；
	 * 已评过的订单项跳过，订单仍置为已评价。
	 */
	public boolean autoAppraiseOrder(OrderInfo orderInfo) {
		if (Objects.isNull(orderInfo)) {
			return true;
		}
		if (CommonConstants.YES.equals(orderInfo.getAppraiseStatus())) {
			return true;
		}
		UserInfoVO userInfo = remoteMallUserService.getUserById(orderInfo.getUserId());
		if (Objects.isNull(userInfo)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50001.getMsg());
		}
		List<OrderItemEntity> orderItemEntityList = orderItemMapper.selectByOrderId(orderInfo.getId());
		if (orderItemEntityList == null || orderItemEntityList.isEmpty()) {
			return true;
		}
		Set<String> appraisedItemIds = listAppraisedItemIds(orderInfo.getId());
		List<GoodsAppraise> goodsAppraiseList = orderItemEntityList.stream()
			.filter(orderItem -> !appraisedItemIds.contains(orderItem.getId()))
			.map(orderItem -> {
				GoodsAppraise goodsAppraise = new GoodsAppraise();
				goodsAppraise.setUserId(orderInfo.getUserId());
				goodsAppraise.setAvatarUrl(userInfo.getAvatarUrl());
				goodsAppraise.setNickname(userInfo.getNickname());
				// 默认评价不打分：不写商品/物流/服务评分
				goodsAppraise.setOrderId(orderInfo.getId());
				goodsAppraise.setOrderItemId(orderItem.getId());
				goodsAppraise.setSpuId(orderItem.getSpuId());
				goodsAppraise.setContent(DEFAULT_AUTO_APPRAISE_CONTENT);
				goodsAppraise.setAppraiseType(APPRAISE_TYPE_SYSTEM);
				goodsAppraise.setIsAnonymous(1);
				return goodsAppraise;
			})
			.collect(Collectors.toList());
		if (!goodsAppraiseList.isEmpty() && !remoteGoodsAppraiseService.addGoodsAppraise(goodsAppraiseList)) {
			throw new ArynBusinessException("订单评价失败");
		}
		orderInfo.setAppraiseStatus(CommonConstants.YES);
		return orderInfoMapper.updateById(orderInfo) > 0;
	}

	/**
	 * 构造用户主动评价：orderId/spuId/userId/昵称头像一律取服务端快照，忽略客户端伪造值。
	 */
	private GoodsAppraise buildUserAppraise(OrderAppraiseDTO appraiseDTO, OrderInfo orderInfo,
			OrderItemEntity orderItem, UserInfoVO userInfo) {
		GoodsAppraise goodsAppraise = new GoodsAppraise();
		goodsAppraise.setOrderId(orderInfo.getId());
		goodsAppraise.setOrderItemId(orderItem.getId());
		goodsAppraise.setSpuId(orderItem.getSpuId());
		goodsAppraise.setUserId(orderInfo.getUserId());
		goodsAppraise.setAvatarUrl(userInfo.getAvatarUrl());
		goodsAppraise.setNickname(userInfo.getNickname());
		goodsAppraise.setGoodsScore(appraiseDTO.getGoodsScore());
		goodsAppraise.setLogisticsScore(appraiseDTO.getLogisticsScore());
		goodsAppraise.setServiceScore(appraiseDTO.getServiceScore());
		goodsAppraise.setContent(defaultContent(appraiseDTO.getContent(), DEFAULT_APPRAISE_CONTENT));
		String[] picUrls = toArray(appraiseDTO.getPicUrls());
		if (picUrls != null) {
			goodsAppraise.setPicUrls(picUrls);
		}
		String[] tags = normalizeTags(appraiseDTO.getTags());
		if (tags != null) {
			goodsAppraise.setTags(tags);
		}
		goodsAppraise.setIsAnonymous(Boolean.TRUE.equals(appraiseDTO.getIsAnonymous()) ? 1 : 0);
		goodsAppraise.setAppraiseType(APPRAISE_TYPE_USER);
		return goodsAppraise;
	}

	/**
	 * 刷新订单评价状态：全部订单项都有有效评价才置 '1'，否则回落/保持 '0'。
	 */
	private void refreshOrderAppraiseStatus(OrderInfo orderInfo, String userId) {
		List<OrderItemEntity> orderItems = orderItemMapper.selectByOrderId(orderInfo.getId());
		Set<String> evaluatedItemIds = listAppraisedItemIds(orderInfo.getId());
		boolean allEvaluated = orderItems != null && !orderItems.isEmpty()
				&& evaluatedItemIds.containsAll(
						orderItems.stream().map(OrderItemEntity::getId).collect(Collectors.toSet()));
		String targetStatus = allEvaluated ? CommonConstants.YES : CommonConstants.NO;
		if (targetStatus.equals(orderInfo.getAppraiseStatus())) {
			return;
		}
		int updated = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getUserId, userId)
			.eq(OrderInfo::getStatus, OrderStatusEnum.COMPLETED.getCode())
			.set(OrderInfo::getAppraiseStatus, targetStatus));
		if (updated == 0) {
			throw new ArynBusinessException("订单状态已变化，无法评价");
		}
	}

	/**
	 * 查询订单当前有效评价（逻辑删除的由 @TableLogic 自动排除）。
	 */
	private List<GoodsAppraise> listOrderAppraises(String orderId) {
		List<GoodsAppraise> appraiseList = remoteGoodsAppraiseService.listByOrderId(orderId);
		return appraiseList == null ? new ArrayList<>() : appraiseList;
	}

	private Set<String> listAppraisedItemIds(String orderId) {
		return listOrderAppraises(orderId).stream()
			.map(GoodsAppraise::getOrderItemId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	/**
	 * 校验订单归属：不存在或不属于当前用户统一按「订单不存在」处理，避免越权探测。
	 */
	private OrderInfo requireUserOrder(String orderId, String userId) {
		OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
		if (Objects.isNull(orderInfo) || !userId.equals(orderInfo.getUserId())) {
			throw new ArynBusinessException("订单不存在");
		}
		return orderInfo;
	}

	private GoodsAppraise requireOrderItemAppraise(String orderId, String orderItemId) {
		GoodsAppraise appraise = findOrderItemAppraise(orderId, orderItemId);
		if (Objects.isNull(appraise)) {
			throw new ArynBusinessException("评价不存在或已删除");
		}
		return appraise;
	}

	private GoodsAppraise findOrderItemAppraise(String orderId, String orderItemId) {
		Map<String, GoodsAppraise> appraiseMap = new LinkedHashMap<>();
		for (GoodsAppraise appraise : listOrderAppraises(orderId)) {
			if (StringUtils.hasText(appraise.getOrderItemId())) {
				appraiseMap.putIfAbsent(appraise.getOrderItemId(), appraise);
			}
		}
		return appraiseMap.get(orderItemId);
	}

	private void checkOperateWindow(GoodsAppraise appraise, String action) {
		LocalDateTime createTime = appraise.getCreateTime();
		if (Objects.nonNull(createTime)
				&& createTime.isBefore(LocalDateTime.now().minusDays(APPRAISE_OPERATE_WINDOW_DAYS))) {
			throw new ArynBusinessException("评价发布已超过30天，不能" + action);
		}
	}

	private String defaultContent(String content, String defaultContent) {
		return StringUtils.hasText(content) ? content.trim() : defaultContent;
	}

	private String[] toArray(List<String> values) {
		if (values == null) {
			return null;
		}
		return values.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.toArray(String[]::new);
	}

	/**
	 * 标签过滤：去空、去超长、去重，最多保留 5 个。
	 */
	private String[] normalizeTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return null;
		}
		List<String> normalizedTags = tags.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(StringUtils::hasText)
			.filter(tag -> tag.length() <= MAX_TAG_LENGTH)
			.distinct()
			.limit(MAX_TAGS)
			.collect(Collectors.toList());
		return normalizedTags.isEmpty() ? null : normalizedTags.toArray(new String[0]);
	}

}
