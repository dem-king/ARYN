import alovaInstance from './core/instance'

export interface StaffNotice {
  category?: string
  content?: string
  messageId: string
  priority?: string
  readStatus?: string
  receivedTime?: string
  recipientRecordId: string
  summary?: string
  title: string
}

export interface StaffNoticePage {
  hasMore: boolean
  nextCursor?: string
  records: StaffNotice[]
}

export function getStaffNotices(params: { cursor?: string, limit?: number } = {}) {
  return alovaInstance.Get<StaffNoticePage>('/message/staff/notice', { params })
}

export function getStaffNoticeUnreadCount() {
  return alovaInstance.Get<number>('/message/staff/notice/unread/count')
}

export function markStaffNoticeRead(id: string) {
  return alovaInstance.Post(`/message/staff/notice/${id}/read`)
}

export function markAllStaffNoticesRead() {
  return alovaInstance.Post('/message/staff/notice/read-all')
}
