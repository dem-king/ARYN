package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationContextVO;

/**
 * 会话上下文聚合服务（客服工作台信息面板：会员卡 + 最近订单 + 会话摘要）。
 *
 * <p>会员资料来自会话参与者快照（不新增用户域接口）；订单来自订单域 Dubbo 远程接口，
 * 仅 CUSTOMER_SERVICE 会话查询，异常/超时降级为空数组，接口本身仍 200。</p>
 */
public interface ConversationContextService {

	/**
	 * 查询会话上下文。
	 * @param tenantId 租户
	 * @param identityType 调用者身份类型（工作人员/会员）
	 * @param identityId 调用者 ID
	 * @param conversationId 会话 ID
	 * @param supervisor 调用者是否持 message:service:supervisor 权限（非会话参与者时用于主管只读降级）
	 * @return 上下文聚合视图
	 */
	ConversationContextVO getContext(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId, boolean supervisor);

}
