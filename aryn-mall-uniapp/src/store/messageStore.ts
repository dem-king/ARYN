import { defineStore } from 'pinia'
import { getConversationInbox } from '@/api/message/conversation'
import { getNoticeUnreadCount } from '@/api/message/notice'

let socketTask: UniApp.SocketTask | undefined
let reconnectTimer: ReturnType<typeof setTimeout> | undefined

export const useMessageStore = defineStore('message', {
  state: () => ({
    conversationUnread: 0,
    noticeUnread: 0,
    socketState: 'closed' as 'closed' | 'connecting' | 'open',
  }),
  getters: {
    totalUnread: state => state.conversationUnread + state.noticeUnread,
  },
  actions: {
    async refreshUnread() {
      const [noticeUnread, conversations] = await Promise.all([
        getNoticeUnreadCount().send(),
        getConversationInbox({ limit: 100 }).send(),
      ])
      this.noticeUnread = noticeUnread
      this.conversationUnread = conversations.records.reduce(
        (total, item) => total + (item.unreadCount || 0),
        0,
      )
    },
    connect() {
      if (this.socketState === 'connecting' || this.socketState === 'open')
        return
      const authStore = useAuthStore()
      if (!authStore.getToken)
        return
      this.socketState = 'connecting'
      const apiBase = String(import.meta.env.VITE_API_BASE_URL || '')
        .replace(/\/$/, '')
        .replace(/^http/, 'ws')
      const path
        = import.meta.env.VITE_OPEN_BOOT === 'true'
          ? '/boot/ws/app'
          : '/message/ws/app'
      socketTask = uni.connectSocket({
        url: `${apiBase}${path}?satoken=${encodeURIComponent(authStore.getToken)}`,
        header: {
          'satoken': authStore.getToken,
          'tenant-id': import.meta.env.VITE_TENANT_ID,
        },
        complete: () => {},
      })
      socketTask.onOpen(() => {
        this.socketState = 'open'
      })
      socketTask.onMessage(() => {
        void this.refreshUnread()
        uni.$emit('message-push')
      })
      socketTask.onClose(() => {
        this.socketState = 'closed'
        reconnectTimer = setTimeout(() => this.connect(), 3000)
      })
      socketTask.onError(() => socketTask?.close({}))
    },
    disconnect() {
      if (reconnectTimer)
        clearTimeout(reconnectTimer)
      socketTask?.close({})
      socketTask = undefined
      this.socketState = 'closed'
    },
  },
})
