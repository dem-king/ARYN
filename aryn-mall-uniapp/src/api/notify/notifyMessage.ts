import { alovaInstance } from '@/api/core/instance'

/** 分页查询消息列表 */
export function getPage(params: {
  notifyType?: number
  current: number
  size: number
}) {
  return alovaInstance.Get<any>('/mall-notify/app/notifymessage/page', {
    params,
  })
}

/** 获取未读消息数 */
export function getUnreadCount() {
  return alovaInstance.Get<any>('/mall-notify/app/notifymessage/unread-count')
}

/** 标记消息已读 */
export function markAsRead(messageId: string) {
  return alovaInstance.Put<any>(
    `/mall-notify/app/notifymessage/read/${messageId}`,
  )
}

/** 全部已读 */
export function markAllAsRead(notifyType?: number) {
  return alovaInstance.Put<any>(
    '/mall-notify/app/notifymessage/read-all',
    undefined,
    { params: { notifyType } },
  )
}

/** 删除消息 */
export function deleteMessage(messageId: string) {
  return alovaInstance.Delete<any>(`/mall-notify/app/notifymessage/${messageId}`)
}
