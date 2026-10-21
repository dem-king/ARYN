/**
 * 配送员端消息 store：站内信未读数 + 实时推送。
 *
 * 与商城端 messageStore 分开的原因：配送员用的是独立 TOB 配送 token
 * （Local.deliveryToken），与商城 TOC token 是两套身份。混用会出现
 * 「商城登录态下有未读、配送登录态下没有」互相覆盖未读数的问题。
 *
 * 通道复用工作人员链路：司机在服务端就是 SYS_USER，站内信与 WebSocket
 * 都走 /message/staff/** 与 /message/ws/staff，不需要新接口。
 */
import { defineStore } from 'pinia'
import { buildWebSocketUrl, getPageOrigin } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { getStaffNoticeUnreadCount } from '@/api/delivery-message'
import { Local } from '@/utils/storage'

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
    // 链路静默死亡不触发 onClose（后台挂起/被回收），只能靠 PONG 超时强拆重连
    if (Date.now() - lastAliveAt > DEAD_LINK_THRESHOLD) {
      task.close({})
      return
    }
    task.send({ data: 'PING', fail: () => task.close({}) })
  }, HEARTBEAT_INTERVAL)
}

export const useDeliveryMessageStore = defineStore('deliveryMessage', {
  state: () => ({
    noticeUnread: 0,
    socketState: 'closed' as 'closed' | 'connecting' | 'open',
  }),
  actions: {
    /**
     * 拉未读数。失败不清零：网络抖动时宁可展示旧值，
     * 也不能把「有未读」闪成 0 让司机以为没有新消息。
     */
    async refreshUnread() {
      const token = deliveryToken()
      if (!token) {
        this.noticeUnread = 0
        return
      }
      try {
        this.noticeUnread = await getStaffNoticeUnreadCount().send()
      }
      catch {
        // 保留上一次的值
      }
    },
    async connect() {
      if (this.socketState === 'connecting' || this.socketState === 'open')
        return
      const token = deliveryToken()
      if (!token) {
        // 无 token 时 socket 不会创建、onClose 永远不会触发，必须主动重试，
        // 否则司机进入配送端后整个生命周期都没有再建连的时机
        if (!reconnectTimer) {
          reconnectTimer = setTimeout(() => {
            reconnectTimer = undefined
            void this.connect()
          }, RECONNECT_DELAY)
        }
        return
      }
      // await 之后 TS 控制流仍把 socketState 窄化成 'closed'，断言回全集避免误报
      const stateAfterGuard = this.socketState as 'closed' | 'connecting' | 'open'
      if (stateAfterGuard === 'connecting' || stateAfterGuard === 'open')
        return
      this.socketState = 'connecting'
      const path = rewriteBootUrl(
        '/message/ws/staff',
        parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
      ) ?? '/message/ws/staff'
      socketTask = uni.connectSocket({
        url: `${buildWebSocketUrl(path, getPageOrigin())}?satoken=${encodeURIComponent(token)}`,
        header: {
          'satoken': token,
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
        uni.$emit('delivery-message-push')
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
      this.noticeUnread = 0
    },
  },
})

function deliveryToken() {
  return String(Local.get('deliveryToken') || '')
}
