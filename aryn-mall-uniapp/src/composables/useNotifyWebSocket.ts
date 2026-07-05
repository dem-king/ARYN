/**
 * 站内信 WebSocket 客户端
 *
 * 功能：
 * 1. 自动连接 / 断线重连
 * 2. 30s 心跳保活
 * 3. 收到新消息时通过 uni.$emit('notify:new', message) 全局广播
 * 4. 提供 connected 响应式状态
 */
import { ref } from 'vue'
import { useAuthStore } from '@/store/authStore'

const RECONNECT_DELAY = 5000
const HEARTBEAT_INTERVAL = 30000

let socketTask: UniApp.SocketTask | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let manualClose = false

export const connected = ref(false)

function buildWsUrl(): string {
  const baseUrl: string = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9999'
  // http(s) → ws(s)
  const wsBase = baseUrl.replace(/^http/i, 'ws')
  // 单体模式下 context-path 为 /boot，WS 路径前缀同步
  const isOpenBoot = import.meta.env.VITE_OPEN_BOOT === 'true'
  const prefix = isOpenBoot ? '/boot' : ''
  const authStore = useAuthStore()
  const token = authStore.getToken
  return `${wsBase}${prefix}/ws/notify?token=${token}`
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (socketTask) {
      socketTask.send({
        data: 'ping',
        fail: () => {
          console.warn('[WS] 心跳发送失败')
        },
      })
    }
  }, HEARTBEAT_INTERVAL)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function scheduleReconnect() {
  if (manualClose) return
  if (reconnectTimer) clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(() => {
    console.log('[WS] 尝试重连...')
    connectNotifyWebSocket()
  }, RECONNECT_DELAY)
}

export function connectNotifyWebSocket() {
  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return
  }

  // 避免重复连接
  if (socketTask) {
    return
  }

  manualClose = false
  const url = buildWsUrl()
  console.log('[WS] 连接中:', url.replace(/token=.*/, 'token=***'))

  socketTask = uni.connectSocket({
    url,
    success: () => {},
    complete: () => {},
  })

  socketTask.onOpen(() => {
    connected.value = true
    console.log('[WS] 连接成功')
    startHeartbeat()
    // 连接成功后刷新一次未读数
    uni.$emit('notify:unread-refresh')
  })

  socketTask.onMessage((res) => {
    if (res.data === 'pong') return
    try {
      const message = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      // 全局广播新消息
      uni.$emit('notify:new', message)
      // 触发未读数刷新
      uni.$emit('notify:unread-refresh')
      // 震动提醒
      // #ifdef MP-WEIXIN || APP-PLUS
      uni.vibrateShort({ type: 'light' })
      // #endif
    }
    catch (e) {
      console.error('[WS] 解析消息失败', e)
    }
  })

  socketTask.onClose(() => {
    connected.value = false
    socketTask = null
    stopHeartbeat()
    console.log('[WS] 连接关闭')
    if (!manualClose) {
      scheduleReconnect()
    }
  })

  socketTask.onError((err) => {
    connected.value = false
    console.error('[WS] 连接错误', err)
  })
}

export function disconnectNotifyWebSocket() {
  manualClose = true
  stopHeartbeat()
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (socketTask) {
    socketTask.close({})
    socketTask = null
  }
  connected.value = false
  console.log('[WS] 主动断开')
}
