package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 坐席待办提醒视图。
 *
 * <p>管理端铃铛原先只统计站内信，客服消息到达时没有任何提示；这里把“本人会话未读”
 * 与“共享池待领取”聚合成一次读取，避免铃铛为两类提醒分别请求接口。
 */
@Data
public class ConversationAttentionVO implements Serializable {

	/** 存在未读消息的本人会话数。 */
	private long unreadConversations;

	/** 共享池中等待领取的会话数。 */
	private long waitingTotal;

	/** 需要提醒的会话明细，未读在前、待领取在后，仅返回前若干条。 */
	private List<ConversationVO> conversations;

}
