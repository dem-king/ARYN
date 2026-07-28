import { onBeforeUnmount } from 'vue'
import { buildDeliveryHeaders, buildDeliveryWebSocketUrl, rewriteDeliveryBootUrl } from '@/api/core/instance'
import { useAuthStore } from '@/store/auth'

export async function handleStaffSocketEvent(_payload: string, refresh: () => Promise<unknown>) {
  await refresh()
}

export function useStaffWebSocket(refresh: () => Promise<unknown>) {
  const authStore = useAuthStore()
  let socket: UniApp.SocketTask | undefined
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined

  function connect() {
    if (!authStore.token || socket)
      return
    const path = rewriteDeliveryBootUrl('/message/ws/staff')
    socket = uni.connectSocket({
      url: `${buildDeliveryWebSocketUrl(path)}?satoken=${encodeURIComponent(authStore.token)}`,
      header: buildDeliveryHeaders(authStore.token, authStore.tenantId, import.meta.env.VITE_DELIVERY_MINI_APP_ID),
      complete: () => {},
    })
    socket.onMessage(event => void handleStaffSocketEvent(String(event.data ?? ''), refresh))
    socket.onClose(() => {
      socket = undefined
      reconnectTimer = setTimeout(connect, 3000)
    })
    socket.onError(() => socket?.close({}))
  }

  function disconnect() {
    if (reconnectTimer)
      clearTimeout(reconnectTimer)
    socket?.close({})
    socket = undefined
  }

  onBeforeUnmount(disconnect)
  return { connect, disconnect }
}
