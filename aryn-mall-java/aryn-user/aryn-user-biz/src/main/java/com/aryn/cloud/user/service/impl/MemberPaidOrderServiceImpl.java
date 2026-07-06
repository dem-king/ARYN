package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.MemberPaidOrder;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.MemberPaidOrderMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IMemberPaidOrderService;
import com.aryn.cloud.user.service.IUserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 付费会员订单
 *
 * @author aryn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPaidOrderServiceImpl extends ServiceImpl<MemberPaidOrderMapper, MemberPaidOrder>
		implements IMemberPaidOrderService {

	private final IMemberLevelService memberLevelService;

	private final IUserInfoService userInfoService;

	@Override
	public IPage<MemberPaidOrder> getPage(Page page, MemberPaidOrder memberPaidOrder) {
		return this.page(page,
				Wrappers.<MemberPaidOrder>lambdaQuery()
					.eq(memberPaidOrder.getUserId() != null, MemberPaidOrder::getUserId, memberPaidOrder.getUserId())
					.eq(memberPaidOrder.getStatus() != null, MemberPaidOrder::getStatus, memberPaidOrder.getStatus())
					.orderByDesc(MemberPaidOrder::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public MemberPaidOrder createOrder(String userId, String memberLevelId) {
		MemberLevel level = memberLevelService.getById(memberLevelId);
		if (level == null) {
			throw new ArynBusinessException("会员等级不存在");
		}
		if (!"1".equals(level.getIsPaid())) {
			throw new ArynBusinessException("该等级不是付费会员等级");
		}
		if (!"0".equals(level.getStatus())) {
			throw new ArynBusinessException("该会员等级已禁用");
		}

		// 检查是否有未支付的订单
		long pendingCount = this.count(Wrappers.<MemberPaidOrder>lambdaQuery()
				.eq(MemberPaidOrder::getUserId, userId)
				.eq(MemberPaidOrder::getMemberLevelId, memberLevelId)
				.eq(MemberPaidOrder::getStatus, "pending"));
		if (pendingCount > 0) {
			throw new ArynBusinessException("已有待支付的订单，请先支付或取消");
		}

		// 生成订单号
		String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
				+ RandomUtil.randomNumbers(6);

		MemberPaidOrder order = new MemberPaidOrder();
		order.setUserId(userId);
		order.setMemberLevelId(memberLevelId);
		order.setOrderNo(orderNo);
		order.setPrice(level.getPrice());
		order.setDuration(level.getDuration());
		order.setStatus("pending");
		this.save(order);

		log.info("创建付费会员订单, userId={}, memberLevelId={}, orderNo={}", userId, memberLevelId, orderNo);
		return order;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void paySuccess(String orderNo) {
		MemberPaidOrder order = this.getOne(
				Wrappers.<MemberPaidOrder>lambdaQuery().eq(MemberPaidOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!"pending".equals(order.getStatus())) {
			throw new ArynBusinessException("订单状态异常");
		}

		LocalDateTime now = LocalDateTime.now();
		order.setStatus("paid");
		order.setPayTime(now);
		order.setStartTime(now);
		order.setEndTime(now.plusMonths(order.getDuration()));
		this.updateById(order);

		// 更新用户会员等级
		UserInfo userInfo = userInfoService.getById(order.getUserId());
		if (userInfo != null) {
			userInfo.setMemberLevelId(order.getMemberLevelId());
			userInfoService.updateById(userInfo);
		}

		log.info("付费会员支付成功, userId={}, orderNo={}, memberLevelId={}", order.getUserId(), orderNo,
				order.getMemberLevelId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void cancelOrder(String orderNo) {
		MemberPaidOrder order = this.getOne(
				Wrappers.<MemberPaidOrder>lambdaQuery().eq(MemberPaidOrder::getOrderNo, orderNo));
		if (order == null) {
			throw new ArynBusinessException("订单不存在");
		}
		if (!"pending".equals(order.getStatus())) {
			throw new ArynBusinessException("只能取消待支付订单");
		}

		order.setStatus("cancelled");
		this.updateById(order);

		log.info("取消付费会员订单, orderNo={}", orderNo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void checkExpiredOrders() {
		LocalDateTime now = LocalDateTime.now();
		List<MemberPaidOrder> expiredOrders = this.list(Wrappers.<MemberPaidOrder>lambdaQuery()
				.eq(MemberPaidOrder::getStatus, "paid")
				.le(MemberPaidOrder::getEndTime, now));

		if (expiredOrders.isEmpty()) {
			return;
		}

		for (MemberPaidOrder order : expiredOrders) {
			order.setStatus("expired");
			this.updateById(order);
			log.info("付费会员到期, userId={}, orderNo={}", order.getUserId(), order.getOrderNo());
		}
	}

}