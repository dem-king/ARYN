import type { CursorPage, NoticeInboxItem, NoticeRecord } from './types';

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
