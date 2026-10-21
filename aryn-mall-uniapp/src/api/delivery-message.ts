/**
 * 配送员端消息（工作人员站内信）。
 *
 * 配送员用独立的 TOB 配送 token（Local.deliveryToken），身份在服务端就是 SYS_USER，
 * 因此直接复用工作人员通知通道 /message/staff/notice，不需要另建一套接口。
 * 所有请求必须带 `authScope: 'delivery'`，否则请求层会拿商城 token 去调，
 * 对不上身份直接被服务端拒绝。
 */
import type { CursorPage, NoticeInboxItem } from '@/api/message/types'
import { alovaInstance } from '@/api/core/instance'

/** 配送端请求统一标记：让请求层取配送 token 而不是商城 token */
const DELIVERY_SCOPE = { headers: { authScope: 'delivery' } } as const

export function getStaffNoticeInbox(params: { cursor?: string, limit?: number }) {
  return alovaInstance.Get<CursorPage<NoticeInboxItem>>(
    '/message/staff/notice',
    { ...DELIVERY_SCOPE, params },
  )
}

export function getStaffNoticeUnreadCount() {
  return alovaInstance.Get<number>(
    '/message/staff/notice/unread/count',
    DELIVERY_SCOPE,
  )
}

export function markStaffNoticeRead(id: string) {
  return alovaInstance.Post(`/message/staff/notice/${id}/read`, undefined, DELIVERY_SCOPE)
}

export function markAllStaffNoticesRead() {
  return alovaInstance.Post('/message/staff/notice/read-all', undefined, DELIVERY_SCOPE)
}
