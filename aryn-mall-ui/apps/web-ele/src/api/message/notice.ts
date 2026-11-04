import type {
  ChatMessage,
  CursorPage,
  NoticeInboxItem,
  NoticeRecipientItem,
  NoticeRecord,
} from './types';

import { requestClient } from '#/api/request';

export interface NoticeSavePayload {
  category: string;
  content: string;
  deptIds?: string[];
  expireTime?: string;
  jumpPayload?: string;
  jumpType?: 'BIZ_DETAIL' | 'INTERNAL_ROUTE' | 'NONE';
  mallUserIds?: string[];
  memberLevelId?: string;
  memberTagId?: string;
  priority?: string;
  roleIds?: string[];
  staffUserIds?: string[];
  summary?: string;
  targetTypes: Array<'MALL_USER' | 'SYS_USER'>;
  title: string;
}

export function getNoticePage(params: Record<string, any>) {
  return requestClient.get<{ records: NoticeRecord[]; total: number }>(
    '/message/admin/notice/page',
    { params },
  );
}

export function createNotice(data: NoticeSavePayload) {
  return requestClient.post<NoticeRecord>('/message/admin/notice', data);
}

export function updateNotice(id: string, data: NoticeSavePayload) {
  return requestClient.put<NoticeRecord>(`/message/admin/notice/${id}`, data);
}

export function publishNotice(id: string) {
  return requestClient.post(`/message/admin/notice/${id}/publish`);
}

export function revokeNotice(id: string) {
  return requestClient.delete(`/message/admin/notice/${id}/publish`);
}

export function deleteNotice(id: string) {
  return requestClient.delete(`/message/admin/notice/${id}`);
}

export function retryNoticeDispatch(id: string) {
  return requestClient.post(`/message/admin/notice/${id}/dispatch/retry`);
}

export function republishNotice(id: string) {
  return requestClient.post(`/message/admin/notice/${id}/republish`);
}

export function getNoticeRecipients(id: string, params: Record<string, any>) {
  return requestClient.get<{ records: NoticeRecipientItem[]; total: number }>(
    `/message/admin/notice/${id}/recipients`,
    { params },
  );
}

export function getStaffNoticeInbox(params: {
  cursor?: string;
  limit?: number;
}) {
  return requestClient.get<CursorPage<NoticeInboxItem>>(
    '/message/staff/notice',
    {
      params,
    },
  );
}

export function getStaffNoticeUnreadCount() {
  return requestClient.get<number>('/message/staff/notice/unread/count');
}

export function markStaffNoticeRead(id: string) {
  return requestClient.post(`/message/staff/notice/${id}/read`);
}

export function markAllStaffNoticesRead() {
  return requestClient.post('/message/staff/notice/read-all');
}

export function hideStaffNotice(id: string) {
  return requestClient.post(`/message/staff/notice/${id}/hide`);
}

/**
 * 回复工作人员收到的内部通知。
 *
 * 后端会按发布人自动打开/创建工作人员私信并插入一条 NOTICE_CARD 卡片消息，
 * 返回的 `conversationId` 用于跳转私信页定位会话；`clientMessageId` 由调用方生成，
 * 保证重复提交不会产生两条卡片消息。
 */
export function replyStaffNotice(id: string, clientMessageId: string) {
  return requestClient.post<ChatMessage>(`/message/staff/notice/${id}/reply`, {
    clientMessageId,
  });
}
