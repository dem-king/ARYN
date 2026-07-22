package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.notice.NoticeInboxQuery;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxItemVO;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxPageVO;
import com.aryn.cloud.message.api.vo.notice.NoticeVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 通知管理和本人收件箱服务。 */
public interface NoticeService {

	NoticeVO createDraft(String tenantId, String senderId, String senderName, NoticeSaveRequest request);

	NoticeVO updateDraft(String tenantId, String operatorId, String noticeId, NoticeSaveRequest request);

	NoticeVO get(String tenantId, String noticeId);

	IPage<NoticeVO> page(String tenantId, String status, Page<?> page);

	void publish(String tenantId, String operatorId, String noticeId);

	void revoke(String tenantId, String operatorId, String noticeId);

	NoticeInboxPageVO inbox(String tenantId, MessageIdentityType identityType, String identityId,
			NoticeInboxQuery query);

	NoticeInboxItemVO inboxDetail(String tenantId, MessageIdentityType identityType, String identityId,
			String noticeId);

	long unreadCount(String tenantId, MessageIdentityType identityType, String identityId);

	void markRead(String tenantId, MessageIdentityType identityType, String identityId, String noticeId);

	void markAllRead(String tenantId, MessageIdentityType identityType, String identityId);

	void hide(String tenantId, MessageIdentityType identityType, String identityId, String noticeId);

}
