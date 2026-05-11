package com.aryn.cloud.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderAppraiseService {

	private final OrderInfoMapper orderInfoMapper;

	private final OrderItemMapper orderItemMapper;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	@DubboReference
	private final RemoteGoodsAppraiseService remoteGoodsAppraiseService;

	public boolean appraiseOrder(String id, List<OrderAppraiseDTO> orderAppraiseList) {
		String userId = SecurityUtils.getUser().getUserId();
		OrderInfo orderInfo = orderInfoMapper.selectById(id);
		if (Objects.isNull(orderInfo)) {
			return false;
		}
		if (!orderInfo.getAppraiseStatus().equals(CommonConstants.NO)) {
			throw new ArynBusinessException("订单已评价");
		}
		UserInfoVO userInfo = remoteMallUserService.getUserById(userId);
		if (Objects.isNull(userInfo)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50001.getMsg());
		}

		List<GoodsAppraise> goodsAppraiseList = orderAppraiseList.stream().map(v -> {
			GoodsAppraise goodsAppraise = new GoodsAppraise();
			BeanUtil.copyProperties(v, goodsAppraise);
			goodsAppraise.setUserId(userId);
			goodsAppraise.setAvatarUrl(userInfo.getAvatarUrl());
			goodsAppraise.setNickname(userInfo.getNickname());
			return goodsAppraise;
		}).collect(Collectors.toList());

		if (!remoteGoodsAppraiseService.addGoodsAppraise(goodsAppraiseList)) {
			throw new ArynBusinessException("订单评价失败");
		}

		orderInfo.setAppraiseStatus(CommonConstants.YES);
		return orderInfoMapper.updateById(orderInfo) > 0;
	}

	public boolean autoAppraiseOrder(OrderInfo orderInfo) {
		if (Objects.isNull(orderInfo)) {
			return true;
		}
		if (!orderInfo.getAppraiseStatus().equals(CommonConstants.NO)) {
			return true;
		}
		UserInfoVO userInfo = remoteMallUserService.getUserById(orderInfo.getUserId());
		if (Objects.isNull(userInfo)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_50001.getMsg());
		}
		List<OrderItemEntity> orderItemEntityList = orderItemMapper.selectByOrderId(orderInfo.getId());

		List<GoodsAppraise> goodsAppraiseList = orderItemEntityList.stream().map(v -> {
			GoodsAppraise goodsAppraise = new GoodsAppraise();
			goodsAppraise.setUserId(orderInfo.getUserId());
			goodsAppraise.setAvatarUrl(userInfo.getAvatarUrl());
			goodsAppraise.setNickname(userInfo.getNickname());
			goodsAppraise.setGoodsScore(5);
			goodsAppraise.setOrderId(orderInfo.getId());
			goodsAppraise.setOrderItemId(v.getId());
			goodsAppraise.setContent("系统默认评价");
			return goodsAppraise;
		}).collect(Collectors.toList());

		if (!remoteGoodsAppraiseService.addGoodsAppraise(goodsAppraiseList)) {
			throw new ArynBusinessException("订单评价失败");
		}

		orderInfo.setAppraiseStatus(CommonConstants.YES);
		return orderInfoMapper.updateById(orderInfo) > 0;
	}

}
