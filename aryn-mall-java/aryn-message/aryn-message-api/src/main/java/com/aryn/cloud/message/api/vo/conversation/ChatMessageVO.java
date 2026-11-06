package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会话消息视图。 */
@Data
public class ChatMessageVO implements Serializable {

	private String id;
	private String conversationId;
	private Long seqNo;
	private String senderType;
	private String senderId;
	private String senderName;
	private String senderAvatar;
	private String messageType;
	private String content;
	private String payload;
	private String clientMessageId;
	private String quotedMessageId;

	/** 引用目标消息类型；无引用或目标不可用/已撤回时为 null。 */
	private String quotedMessageType;

	/** 引用摘要（文本截断 100 字符，图片/卡片为类型标签）；无引用或目标不可用/已撤回时为 null。 */
	private String quotedMessageSummary;

	/** 引用目标发送者昵称快照；不可用时为 null。 */
	private String quotedSenderName;

	/**
	 * 引用目标是否不可用（已撤回/已删除/查不到）。
	 *
	 * <p>无引用（quotedMessageId 为空）时为 null；有引用时必返回：false=引用可用，true=已撤回/不可用。</p>
	 */
	private Boolean quotedRecalled;

	private String recallStatus;

	/** 撤回时间；未撤回为 null。 */
	private LocalDateTime recallTime;

	private LocalDateTime createTime;

}
