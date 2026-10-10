package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 拼团退款成功处理：把成团失败退款的成员置为已取消。
 *
 * <p>退款由拼团超时任务经订单服务真实发起（拼团失败自动退款），
 * 支付网关退款成功后的回调链路会发布本事件；此处按订单号定位成员收尾，
 * 普通售后退款（非拼团失败）不会命中拼团成员，直接跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyRefundHandler implements PromotionRefundEventHandler {

	private final IGroupBuyMemberService groupBuyMemberService;

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		String orderId = event.getOrderId();
		GroupBuyMember member = groupBuyMemberService.getOne(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getOrderId, orderId));
		if (member == null) {
			return;
		}
		if (GroupBuyMemberStatusEnum.STATUS_2.getCode().equals(member.getMemberStatus())) {
			return;
		}
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_2.getCode());
		groupBuyMemberService.updateById(member);
		log.info("拼团退款完成, orderId={}, recordId={}, memberId={}", orderId, member.getRecordId(), member.getId());
	}
}
