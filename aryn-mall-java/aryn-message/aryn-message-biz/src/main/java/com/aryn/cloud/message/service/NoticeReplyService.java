package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.vo.conversation.ChatMessageVO;

/** 通知回复转入独立会话的服务。 */
public interface NoticeReplyService {

	ChatMessageVO replyAsMember(String tenantId, String memberId, String memberName, String memberAvatar,
			String noticeId, String clientMessageId);

	ChatMessageVO replyAsStaff(String tenantId, String staffId, String staffName, String staffAvatar,
			String noticeId, String clientMessageId);

}
