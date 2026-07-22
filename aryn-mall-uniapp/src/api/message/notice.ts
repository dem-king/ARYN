import type { ChatMessage, CursorPage, NoticeInboxItem } from './types'
import { alovaInstance } from '@/api/core/instance'

export function getNoticeInbox(params: { cursor?: string, limit?: number }) {
  return alovaInstance.Get<CursorPage<NoticeInboxItem>>('/message/app/notice', {
    params,
  })
}

export function getNoticeDetail(id: string) {
  return alovaInstance.Get<NoticeInboxItem>(`/message/app/notice/${id}`)
}

export function getNoticeUnreadCount() {
  return alovaInstance.Get<number>('/message/app/notice/unread/count')
}

export function markNoticeRead(id: string) {
  return alovaInstance.Post(`/message/app/notice/${id}/read`)
}

export function markAllNoticesRead() {
  return alovaInstance.Post('/message/app/notice/read-all')
}

export function hideNotice(id: string) {
  return alovaInstance.Post(`/message/app/notice/${id}/hide`)
}

export function replyToNotice(id: string, clientMessageId: string) {
  return alovaInstance.Post<ChatMessage>(`/message/app/notice/${id}/reply`, {
    clientMessageId,
  })
}
