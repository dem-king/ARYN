package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.constant.MessageJumpConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.order.service.SharedCartInviteNotifyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 共享购物车邀请通知实现：逐人下发 {@link MessageSendCommand}。
 *
 * <p>发送时机由调用方负责（{@code TransactionalMqUtils.sendAfterCommit} 包一层），
 * 本类立即发送、不感知事务。发送失败只告警，不影响邀请落库。
 *
 * <p>jumpPayload 统一指向共享购物车详情深链（C 端通知详情页暂未消费 route，
 * 先按管理端同款 JSON 对象约定落库，后续接线时无需回填数据）。
 *
 * @author aryn
 * @since 2026/11/1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedCartInviteNotifyServiceImpl implements SharedCartInviteNotifyService {

	private static final String CATEGORY = "ORDER";

	private static final String BIZ_TYPE = "SHARED_CART_INVITE";

	private static final String CART_DETAIL_ROUTE = "/sub-pages/order/shared-cart/detail";

	private final RocketMQTemplate rocketMQTemplate;

	private final ObjectMapper objectMapper;

	@Override
	public void notifyInvited(String tenantId, String cartId, boolean shoreCart, String vesselName,
			String memberUserId, String memberNickname) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(cartId) || !StringUtils.hasText(memberUserId)) {
			log.warn("共享购物车邀请通知缺少必要参数，跳过：tenantId={} cartId={} memberUserId={}",
					tenantId, cartId, memberUserId);
			return;
		}
		String title = shoreCart ? "你被邀请参与合并采购" : "你被邀请参与船舶补给采购";
		String where = shoreCart
				? "一份岸上合并采购"
				: (StringUtils.hasText(vesselName) ? "船舶「" + vesselName + "」的补给采购" : "一份船舶补给采购");
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId("shared-cart-invite:" + cartId + ":" + memberUserId);
		command.setTenantId(tenantId);
		command.setRecipientType(MessageIdentityType.MALL_USER.name());
		command.setRecipientId(memberUserId);
		command.setRecipientName(memberNickname);
		command.setCategory(CATEGORY);
		command.setTitle(title);
		command.setSummary("你已被加入" + where + "的共享采购清单");
		command.setContent("你已被加入" + where + "的共享采购清单（收集截止后统一提交）。"
				+ "打开小程序「我的 - 共享购物车」即可查看并添加你需要的商品。");
		command.setBizType(BIZ_TYPE);
		command.setBizId(cartId);
		command.setCardPayload(json(Map.of("bizType", BIZ_TYPE, "bizId", cartId, "title", title)));
		// jump_payload 落 json 列：必须包成 JSON 对象，裸串会被消费端校验拒绝
		command.setJumpPayload(json(Map.of(MessageJumpConstants.ROUTE, CART_DETAIL_ROUTE + "?id=" + cartId)));
		command.setChannels(List.of("IN_APP"));
		try {
			rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
		}
		catch (Exception e) {
			log.warn("共享购物车邀请通知发送失败 cartId={} memberUserId={}: {}", cartId, memberUserId, e.getMessage());
		}
	}

	private String json(Map<String, String> payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException e) {
			log.warn("共享购物车邀请通知 payload 序列化失败: {}", e.getMessage());
			return null;
		}
	}

}
