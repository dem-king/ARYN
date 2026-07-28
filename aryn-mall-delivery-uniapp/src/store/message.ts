import { defineStore } from 'pinia'
import {
  getStaffNotices,
  getStaffNoticeUnreadCount,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
  type StaffNotice,
} from '@/api/message'

export const useMessageStore = defineStore('delivery-message', {
  state: () => ({
    notices: [] as StaffNotice[],
    unread: 0,
  }),
  actions: {
    async refresh() {
      const [page, unread] = await Promise.all([
        getStaffNotices({ limit: 50 }),
        getStaffNoticeUnreadCount(),
      ])
      this.notices = page.records ?? []
      this.unread = unread ?? 0
    },
    async markRead(notice: StaffNotice) {
      if (notice.readStatus === 'READ')
        return
      await markStaffNoticeRead(notice.recipientRecordId)
      notice.readStatus = 'READ'
      this.unread = Math.max(0, this.unread - 1)
    },
    async markAllRead() {
      await markAllStaffNoticesRead()
      this.notices.forEach(notice => notice.readStatus = 'READ')
      this.unread = 0
    },
  },
})
