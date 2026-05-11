package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import com.aryn.cloud.promotion.service.IGroupBuyRecordService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyPayHandler implements PromotionPayEventHandler {

	private final IGroupBuyRecordService groupBuyRecordService;

	private final IGroupBuyMemberService groupBuyMemberService;

	@Override
	public void handle(OrderPaySuccessEvent event) {
		String orderId = event.getOrderId();
		long exists = groupBuyMemberService.count(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getOrderId, orderId)
				.ne(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_2.getCode()));
		if (exists == 0) {
			return;
		}
		log.info("拼团支付成功处理, orderId={}", orderId);
		groupBuyRecordService.handlePaySuccess(orderId);
	}
}
