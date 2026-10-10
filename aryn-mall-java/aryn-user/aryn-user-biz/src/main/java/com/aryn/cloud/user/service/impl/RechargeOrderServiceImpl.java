package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.remote.RemoteOrderConfigService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import com.aryn.cloud.user.api.dto.RechargePrepayDTO;
import com.aryn.cloud.user.api.entity.RechargeConfig;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.api.vo.AppRechargeOrderVO;
import com.aryn.cloud.user.mapper.RechargeOrderMapper;
import com.aryn.cloud.user.service.IBalanceRecordService;
import com.aryn.cloud.user.service.IPointsRecordService;
import com.aryn.cloud.user.service.IRechargeConfigService;
import com.aryn.cloud.user.service.IRechargeOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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

	@DubboReference
	private RemotePayService remotePayService;

	@DubboReference
	private RemoteOrderConfigService remoteOrderConfigService;

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
	public Map<String, Object> prepay(String userId, RechargePrepayDTO prepayDTO) {
		String notifyUrl = remoteOrderConfigService.getNotifyUrl();
		if (!StringUtils.hasText(notifyUrl)) {
			throw new ArynBusinessException("订单配置缺少支付回调地址");
		}

		RechargeOrder order = this.getOne(Wrappers.<RechargeOrder>lambdaQuery()
				.eq(RechargeOrder::getOrderNo, prepayDTO.getOrderNo())
				.eq(RechargeOrder::getUserId, userId));
		if (order == null) {
			throw new ArynBusinessException("充值订单不存在");
		}
		if (!"0".equals(order.getPayStatus())) {
			throw new ArynBusinessException("充值订单不是待支付状态");
		}

		CreateOrderReqDTO createOrderReqDTO = new CreateOrderReqDTO();
		createOrderReqDTO.setTradeType(prepayDTO.getTradeType());
		createOrderReqDTO.setSubject("余额充值");
		createOrderReqDTO.setBuyerId(SecurityUtils.getUser().getOpenId());
		createOrderReqDTO.setTotalAmount(String.valueOf(order.getRechargeAmount()));
		createOrderReqDTO.setNotifyUrl(notifyUrl);
		createOrderReqDTO.setOutTradeNo(order.getOrderNo());
		createOrderReqDTO.setQuitUrl(prepayDTO.getQuitUrl());
		createOrderReqDTO.setReturnUrl(prepayDTO.getReturnUrl());
		createOrderReqDTO.setUserId(order.getUserId());
		JSONObject extraParams = new JSONObject();
		extraParams.put(PayConstants.EXTRA_PARAMS_PAY_TYPE, prepayDTO.getPaymentType());
		extraParams.put("mqNotifyUrl", RocketMqConstants.PAY_NOTIFY_TOPIC);
		createOrderReqDTO.setExtra(extraParams.toJSONString());

		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("orderNo", order.getOrderNo());
		resultMap.put("payParams", remotePayService.createOrder(createOrderReqDTO));
		return resultMap;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void paySuccess(String orderNo, String payOrderNo) {
		RechargeOrder order = this.getOne(
				Wrappers.<RechargeOrder>lambdaQuery().eq(RechargeOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new ArynBusinessException("订单不存在");
		}
		if ("1".equals(order.getPayStatus())) {
			// 支付回调可能重复投递：已入账的直接返回，避免 MQ 反复重投
			log.info("充值订单已支付，忽略重复回调：orderNo={}", orderNo);
			return;
		}
		if (!"0".equals(order.getPayStatus())) {
			throw new ArynBusinessException("订单状态异常");
		}

		// 抢占「待支付 → 已支付」：回调与主动查单确认可能并发到达，
		// 只有拿到推进权的那一次才入账，否则会重复加余额
		if (baseMapper.markPaidIfPending(order.getId(), payOrderNo, LocalDateTime.now()) == 0) {
			log.info("充值订单已被并发推进为已支付，跳过重复入账：orderNo={}", orderNo);
			return;
		}

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
	public AppRechargeOrderVO queryAndSettle(String userId, String orderNo) {
		RechargeOrder order = this.getOne(Wrappers.<RechargeOrder>lambdaQuery()
				.eq(RechargeOrder::getOrderNo, orderNo)
				.eq(RechargeOrder::getUserId, userId));
		if (order == null) {
			return null;
		}
		if (!"0".equals(order.getPayStatus())) {
			return AppRechargeOrderVO.from(order);
		}

		// 本地仍是待支付：请支付域向渠道取权威结论，成功时它会广播 pay-notify-topic，
		// 由本模块的监听器统一入账，因此这里只需回读最新状态
		PaySettlementResult settled;
		try {
			settled = remotePayService.queryAndSettlePayOrder(orderNo);
		}
		catch (Exception exception) {
			// 渠道不可达不应让查询接口失败：用户看到的仍是「待确认」，可再次刷新
			log.warn("充值单支付结果核对失败，回退为本地状态：orderNo={}", orderNo, exception);
			return AppRechargeOrderVO.from(order);
		}
		RechargeOrder latest = this.getOne(Wrappers.<RechargeOrder>lambdaQuery()
				.eq(RechargeOrder::getOrderNo, orderNo)
				.eq(RechargeOrder::getUserId, userId));
		AppRechargeOrderVO vo = AppRechargeOrderVO.from(latest == null ? order : latest);
		vo.setChannelTradeState(settled == null ? null : settled.getTradeState());
		return vo;
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
