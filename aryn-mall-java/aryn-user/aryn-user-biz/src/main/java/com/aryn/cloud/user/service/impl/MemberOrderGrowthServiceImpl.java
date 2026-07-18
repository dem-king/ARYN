package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberOrderGrowth;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.MemberOrderGrowthMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IMemberOrderGrowthService;
import com.aryn.cloud.user.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberOrderGrowthServiceImpl implements IMemberOrderGrowthService {

	private final MemberOrderGrowthMapper growthMapper;

	private final UserInfoMapper userInfoMapper;

	private final IPointsRecordService pointsRecordService;

	private final IMemberLevelService memberLevelService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void processOrderComplete(OrderCompleteEvent event) {
		validateEvent(event);
		int points = event.getGoodsPaymentAmount().multiply(event.getPointsMultiplier())
				.setScale(0, RoundingMode.DOWN).intValueExact();
		MemberOrderGrowth growth = new MemberOrderGrowth()
				.setId(IdWorker.getIdStr())
				.setOrderId(event.getOrderId())
				.setOrderNo(event.getOrderNo())
				.setUserId(event.getUserId())
				.setGoodsPaymentAmount(event.getGoodsPaymentAmount())
				.setPointsAwarded(points)
				.setTenantId(event.getTenantId())
				.setCreateTime(LocalDateTime.now());
		if (growthMapper.insertIfAbsent(growth) == 0) {
			return;
		}

		UserInfo before = userInfoMapper.selectById(event.getUserId());
		if (before == null || userInfoMapper.increaseTotalConsume(event.getUserId(), event.getGoodsPaymentAmount()) == 0) {
			throw new ArynBusinessException("会员订单成长累计失败");
		}
		if (points > 0) {
			pointsRecordService.recordPointsChange(event.getUserId(), "1", points, "ORDER_COMPLETE",
					"订单完成积分奖励：" + event.getOrderNo());
		}
		else {
			memberLevelService.recalculateLevel(event.getUserId());
		}
	}

	private void validateEvent(OrderCompleteEvent event) {
		if (event == null || event.getOrderId() == null || event.getUserId() == null || event.getTenantId() == null) {
			throw new ArynBusinessException("订单完成事件缺少必要字段");
		}
		if (event.getGoodsPaymentAmount() == null
				|| event.getGoodsPaymentAmount().compareTo(BigDecimal.ZERO) < 0) {
			throw new ArynBusinessException("订单实付商品金额不合法");
		}
		if (event.getPointsMultiplier() == null || event.getPointsMultiplier().compareTo(BigDecimal.ONE) < 0) {
			throw new ArynBusinessException("订单积分倍率不合法");
		}
	}

}
