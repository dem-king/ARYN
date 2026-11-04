import type { ChatMessage, ChatMessageType } from '@/api/message/types'

export type BusinessCardType = Extract<
  ChatMessageType,
  'NOTICE_CARD' | 'ORDER_CARD' | 'PRODUCT_CARD' | 'REFUND_CARD'
>
export type SendState = 'failed' | 'sending' | 'sent'
export type ViewChatMessage = ChatMessage & { sendState?: SendState }

export function createClientMessageId() {
  return `msg-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

/**
 * 按文件扩展名推断图片 MIME。
 *
 * 后端 IMAGE 消息的 `mimeType` 只接受 `image/jpeg|png|webp`，并会把它落库供
 * 下载端按真实类型渲染；旧实现固定写 `image/jpeg`，png/webp 图片存成 jpeg
 * 后部分端拿到的 Content-Type 与字节不符。临时文件路径没有扩展名（小程序
 * 相机临时文件）时回退 `image/jpeg`，仍在后端白名单内。
 */
export function resolveImageMimeType(
  filePath?: null | string,
): 'image/jpeg' | 'image/png' | 'image/webp' {
  const value = (filePath ?? '').trim().toLowerCase()
  const extension = /\.([a-z0-9]+)(?:[?#].*)?$/.exec(value)?.[1]
  switch (extension) {
    case 'png':
      return 'image/png'
    case 'webp':
      return 'image/webp'
    // jpg/jpeg 与未知扩展名（含无扩展名的临时文件）统一回退 jpeg
    default:
      return 'image/jpeg'
  }
}

export function mergeServerMessage(
  messages: ViewChatMessage[],
  serverMessage: ChatMessage,
) {
  const result = [...messages]
  const index = result.findIndex(
    item => item.clientMessageId === serverMessage.clientMessageId,
  )
  const next = { ...serverMessage, sendState: 'sent' as const }
  if (index >= 0)
    result.splice(index, 1, next)
  else result.push(next)
  return result.sort((first, second) => first.seqNo - second.seqNo)
}

export function mergeCursorMessages(
  messages: ViewChatMessage[],
  incoming: ChatMessage[],
) {
  return incoming.reduce(
    (result, message) => mergeServerMessage(result, message),
    messages,
  )
}

export function latestSequence(messages: Array<Pick<ChatMessage, 'seqNo'>>) {
  return messages.reduce(
    (latest, message) => Math.max(latest, message.seqNo),
    0,
  )
}

export function parseMessagePayload(payload?: string) {
  if (!payload)
    return {} as Record<string, any>
  try {
    const value = JSON.parse(payload)
    return value && typeof value === 'object'
      ? (value as Record<string, any>)
      : {}
  }
  catch {
    return {}
  }
}

export function businessCardRoute(
  type: BusinessCardType,
  payload: Record<string, any>,
) {
  const routes: Record<BusinessCardType, { field: string, path: string }> = {
    NOTICE_CARD: {
      field: 'noticeId',
      path: '/sub-pages/message/notice/detail',
    },
    ORDER_CARD: {
      field: 'orderId',
      path: '/sub-pages/order/order-detail/index',
    },
    PRODUCT_CARD: {
      field: 'productId',
      path: '/sub-pages/product/goods-detail/index',
    },
    REFUND_CARD: {
      field: 'refundId',
      path: '/sub-pages/order/order-refunds/refunds-detail/index',
    },
  }
  const route = routes[type]
  const id = payload[route.field]
  return typeof id === 'string' && id
    ? `${route.path}?id=${encodeURIComponent(id)}`
    : ''
}

export function customerServiceRoute(card?: {
  messageType: BusinessCardType
  payload: Record<string, any>
}) {
  if (!card)
    return '/sub-pages/message/chat/index'
  return `/sub-pages/message/chat/index?cardType=${card.messageType}&cardPayload=${encodeURIComponent(JSON.stringify(card.payload))}`
}
