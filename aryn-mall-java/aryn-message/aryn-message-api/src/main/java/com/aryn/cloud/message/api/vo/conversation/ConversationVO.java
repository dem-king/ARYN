package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会话视图。 */
@Data
public class ConversationVO implements Serializable {

	private String id;
	private String conversationType;
	private String queueCode;
	private String customerId;
	private String assignedStaffId;
	private String status;
	private Long lastSeq;
	private Long lastReadSeq;
	private Long unreadCount;
	private String lastMessageSummary;
	private LocalDateTime lastMessageTime;
	private LocalDateTime closedTime;
	private LocalDateTime reopenDeadline;
	private String contextPayload;

	/** 会员昵称快照（取自 message_participant 的 MALL_USER 参与者，缺失时前端回退 customerId）。 */
	private String customerName;

	/** 会员头像快照。 */
	private String customerAvatar;

	/** 负责客服昵称快照（取自 message_participant 的 SYS_USER 当前负责人，缺失时前端回退 assignedStaffId）。 */
	private String assignedStaffName;

	/** 负责客服头像快照。 */
	private String assignedStaffAvatar;

	/**
	 * 对方最后已读 seq。
	 *
	 * <p>查看者为会员（MALL_USER）时取当前负责客服的已读游标，查看者为工作人员（SYS_USER）时
	 * 取会员的已读游标；主管全量视图无法确定查看者身份，固定取会员已读游标。用于「已读/未读」回执。
	 */
	private Long peerLastReadSeq;

	/**
	 * 最近活跃时间 = COALESCE(last_message_time, create_time)。
	 *
	 * <p>仅用于服务端生成不透明游标（nextCursor），前端无需消费；单独返回是因为
	 * {@code COALESCE} 排序键无法从其它字段还原，不编码它翻页会在「无消息的新会话」处漏页。</p>
	 */
	private LocalDateTime lastActiveTime;

}
