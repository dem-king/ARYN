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
