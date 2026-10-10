import { defineStore } from 'pinia'
import { buildWebSocketUrl, getPageOrigin } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'
import { getConversationInbox } from '@/api/message/conversation'
import { getNoticeUnreadCount } from '@/api/message/notice'

let socketTask: UniApp.SocketTask | undefined
let reconnectTimer: ReturnType<typeof setTimeout> | undefined
let heartbeatTimer: ReturnType<typeof setInterval> | undefined
let lastAliveAt = 0

const RECONNECT_DELAY = 3000
/** 心跳间隔：健康链路每个周期都能收到服务端 PONG。 */
const HEARTBEAT_INTERVAL = 25000
/** 超过该时长没有收到任何帧即判定假在线，强制走 onClose 重连。 */
const DEAD_LINK_THRESHOLD = 65000

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    const task = socketTask
    if (!task) {
      stopHeartbeat()
      return
    }
    // 链路静默死亡（如开发者工具/手机后台挂起后 socket 被回收）不会触发
    // onClose，socketState 会永远停在 open；只有靠 PONG 超时识别并强拆重连。
    if (Date.now() - lastAliveAt > DEAD_LINK_THRESHOLD) {
      task.close({})
      return
    }
    task.send({ data: 'PING', fail: () => task.close({}) })
  }, HEARTBEAT_INTERVAL)
}

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
    async connect() {
      if (this.socketState === 'connecting' || this.socketState === 'open')
        return
      const authStore = useAuthStore()
      if (!authStore.getToken) {
        // 无 token 时 socket 不会创建、onClose 永远不会触发，必须主动重试，
        // 否则静默登录完成后整个生命周期都没有再建连的时机。
        if (!reconnectTimer) {
          reconnectTimer = setTimeout(() => {
            reconnectTimer = undefined
            void this.connect()
          }, RECONNECT_DELAY)
        }
        return
      }
      // 建连前守卫：绑定/会话租户校验失败不得创建 socket。
      // 守卫错误在此终止（不进入 onClose 重连循环），由调用方的 await/void 语义消化。
      try {
        await ensureTenantReady()
        await ensureSessionTenant('mall', authStore.getToken)
      }
      catch {
        this.socketState = 'closed'
        return
      }
      // await 之后 TS 控制流仍把 socketState 窄化成 'closed'，断言回全集避免误报
      const stateAfterGuard = this.socketState as 'closed' | 'connecting' | 'open'
      if (stateAfterGuard === 'connecting' || stateAfterGuard === 'open')
        return
      this.socketState = 'connecting'
      const path = rewriteBootUrl(
        '/message/ws/app',
        parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
      ) ?? '/message/ws/app'
      socketTask = uni.connectSocket({
        url: `${buildWebSocketUrl(path, getPageOrigin())}?satoken=${encodeURIComponent(authStore.getToken)}`,
        header: {
          'satoken': authStore.getToken,
          'tenant-id': import.meta.env.VITE_TENANT_ID,
        },
        complete: () => {},
      })
      socketTask.onOpen(() => {
        this.socketState = 'open'
        lastAliveAt = Date.now()
        startHeartbeat()
      })
      socketTask.onMessage((event) => {
        lastAliveAt = Date.now()
        if (event.data === 'PONG')
          return
        void this.refreshUnread()
        uni.$emit('message-push')
      })
      socketTask.onClose(() => {
        this.socketState = 'closed'
        stopHeartbeat()
        if (!reconnectTimer) {
          reconnectTimer = setTimeout(() => {
            reconnectTimer = undefined
            void this.connect()
          }, RECONNECT_DELAY)
        }
      })
      socketTask.onError(() => socketTask?.close({}))
    },
    disconnect() {
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = undefined
      }
      stopHeartbeat()
      socketTask?.close({})
      socketTask = undefined
      lastAliveAt = 0
      this.socketState = 'closed'
    },
  },
})
