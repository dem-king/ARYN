package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationContextVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.api.vo.conversation.MemberContextVO;
import com.aryn.cloud.message.api.vo.conversation.OrderContextItemVO;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.service.ConversationContextService;
import com.aryn.cloud.message.service.ConversationService;
import com.aryn.cloud.order.api.remote.RemoteOrderContextService;
import com.aryn.cloud.order.api.vo.OrderContextVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 会话上下文聚合实现。
 *
 * <p>鉴权：沿用会话详情的参与者口径；非参与者但持主管权限时走 {@code selectForTenant} 只读降级。
 * 订单只对 CUSTOMER_SERVICE 会话查询（STAFF_DIRECT 返回空），订单域异常/超时降级为空数组 + warn。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextServiceImpl implements ConversationContextService {

	/** 上下文面板展示的最近订单条数（契约约定最多 3 条）。 */
	private static final int ORDER_LIMIT = 3;

	private final ConversationService conversationService;

	private final MessageConversationMapper conversationMapper;

	@DubboReference
	private final RemoteOrderContextService orderContextService;

	@Override
	public ConversationContextVO getContext(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId, boolean supervisor) {
		ConversationVO conversation = resolveConversation(tenantId, identityType, identityId, conversationId,
				supervisor);
		ConversationContextVO context = new ConversationContextVO();
		context.setConversation(conversation);
		context.setContextPayload(conversation.getContextPayload());
		context.setMember(buildMember(conversation));
		context.setOrders(loadRecentOrders(tenantId, conversation));
		return context;
	}

	/** 会话读取：优先参与者视角；只有主管权限时降级为全租户只读视角（不要求参与者）。 */
	private ConversationVO resolveConversation(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId, boolean supervisor) {
		try {
			return conversationService.get(tenantId, identityType, identityId, conversationId);
		}
		catch (ArynBusinessException exception) {
			if (!supervisor) {
				throw exception;
			}
			ConversationVO view = conversationMapper.selectForTenant(tenantId, conversationId);
			if (view == null) {
				throw new ArynBusinessException("会话不存在或无权访问");
			}
			return view;
		}
	}

	/** 会员卡：仅 CUSTOMER_SERVICE 会话返回；昵称快照缺失回退会员 ID，不新增用户域接口。 */
	private MemberContextVO buildMember(ConversationVO conversation) {
		if (!isCustomerServiceConversation(conversation)) {
			return null;
		}
		MemberContextVO member = new MemberContextVO();
		member.setId(conversation.getCustomerId());
		member.setName(StringUtils.hasText(conversation.getCustomerName()) ? conversation.getCustomerName()
				: conversation.getCustomerId());
		member.setAvatar(conversation.getCustomerAvatar());
		return member;
	}

	/** 最近订单：只对客服会话查询；订单域异常/超时降级为空数组，不阻塞上下文面板。 */
	private List<OrderContextItemVO> loadRecentOrders(String tenantId, ConversationVO conversation) {
		if (!isCustomerServiceConversation(conversation)) {
			return List.of();
		}
		try {
			List<OrderContextVO> orders = orderContextService.queryRecentOrders(tenantId,
					conversation.getCustomerId(), ORDER_LIMIT);
			if (orders == null || orders.isEmpty()) {
				return List.of();
			}
			return orders.stream().map(this::toItem).toList();
		}
		catch (RuntimeException exception) {
			log.warn("查询会员最近订单失败，上下文面板降级为空列表 tenantId={}, conversationId={}", tenantId,
					conversation.getId(), exception);
			return List.of();
		}
	}

	private boolean isCustomerServiceConversation(ConversationVO conversation) {
		return conversation != null && ConversationType.CUSTOMER_SERVICE.name().equals(conversation.getConversationType())
				&& StringUtils.hasText(conversation.getCustomerId());
	}

	private OrderContextItemVO toItem(OrderContextVO order) {
		OrderContextItemVO item = new OrderContextItemVO();
		BeanUtils.copyProperties(order, item);
		return item;
	}

}
