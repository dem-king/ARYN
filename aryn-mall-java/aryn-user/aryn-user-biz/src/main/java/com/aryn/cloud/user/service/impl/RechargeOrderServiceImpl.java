package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.RechargeConfig;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.mapper.RechargeOrderMapper;
import com.aryn.cloud.user.service.IBalanceRecordService;
import com.aryn.cloud.user.service.IPointsRecordService;
import com.aryn.cloud.user.service.IRechargeConfigService;
import com.aryn.cloud.user.service.IRechargeOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 充值订单
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeOrderServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder>
		implements IRechargeOrderService {

	private final IRechargeConfigService rechargeConfigService;

	private final IBalanceRecordService balanceRecordService;

	private final IPointsRecordService pointsRecordService;

	@Override
	public IPage<RechargeOrder> getPage(Page page, RechargeOrder rechargeOrder) {
		return this.page(page,
				Wrappers.<RechargeOrder>lambdaQuery().orderByDesc(RechargeOrder::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public RechargeOrder createOrder(String userId, String rechargeConfigId) {
		RechargeConfig config = rechargeConfigService.getById(rechargeConfigId);
		if (config == null) {
			throw new ArynBusinessException("充值配置不存在");
		}
		if (!"0".equals(config.getStatus())) {
			throw new ArynBusinessException("充值配置已禁用");
		}

		// 生成订单号：时间戳 + 随机数
		String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
				+ RandomUtil.randomNumbers(6);

		RechargeOrder order = new RechargeOrder();
		order.setUserId(userId);
		order.setRechargeConfigId(rechargeConfigId);
		order.setRechargeAmount(config.getRechargeAmount());
		order.setGiftAmount(config.getGiftAmount());
		order.setGiftPoint(config.getGiftPoint());
		order.setOrderNo(orderNo);
		order.setPayStatus("0");
		this.save(order);

		return order;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void paySuccess(String orderNo, String payOrderNo) {
		RechargeOrder order = this.getOne(
				Wrappers.<RechargeOrder>lambdaQuery().eq(RechargeOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!"0".equals(order.getPayStatus())) {
			throw new ArynBusinessException("订单状态异常");
		}

		// 更新订单状态为已支付
		order.setPayStatus("1");
		order.setPayTime(LocalDateTime.now());
		order.setPayOrderNo(payOrderNo);
		this.updateById(order);

		// 增加余额（充值金额 + 赠送金额）
		balanceRecordService.recordBalanceChange(order.getUserId(), "1",
				order.getRechargeAmount().add(order.getGiftAmount()), "RECHARGE", "充值订单：" + orderNo);

		// 如果有赠送积分，增加积分
		if (order.getGiftPoint() != null && order.getGiftPoint() > 0) {
			pointsRecordService.recordPointsChange(order.getUserId(), "1", order.getGiftPoint(), "RECHARGE",
					"充值赠送积分：" + orderNo);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void cancelOrder(String orderNo) {
		RechargeOrder order = this.getOne(
				Wrappers.<RechargeOrder>lambdaQuery().eq(RechargeOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!"0".equals(order.getPayStatus())) {
			throw new ArynBusinessException("只能取消待支付订单");
		}

		order.setPayStatus("2");
		this.updateById(order);
	}

}
