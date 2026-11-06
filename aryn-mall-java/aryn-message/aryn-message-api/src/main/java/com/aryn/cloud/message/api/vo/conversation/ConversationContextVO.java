package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话上下文聚合视图（客服工作台信息面板：会员卡 + 最近订单 + 会话摘要）。
 *
 * <p>会员资料取自会话参与者快照（不新增用户域接口）；订单来自订单域 Dubbo（仅 CUSTOMER_SERVICE 会话查询，
 * 异常/超时降级为空数组）；接口本身仍按会话详情鉴权（参与者，或主管走全租户只读视角）。</p>
 */
@Data
public class ConversationContextVO implements Serializable {

	/** 会员资料；STAFF_DIRECT 私信会话为 null。 */
	private MemberContextVO member;

	/** 最近订单（最多 3 条，按 createTime 倒序）；私信会话/无数据/订单域降级时为空数组。 */
	private List<OrderContextItemVO> orders;

	/** 会话摘要（复用既有 ConversationVO，含 createTime/waitingSince/contextPayload 等）。 */
	private ConversationVO conversation;

	/** 会话业务上下文 JSON（与 conversation.contextPayload 同值，方便前端直接取用）；无则 null。 */
	private String contextPayload;

}
