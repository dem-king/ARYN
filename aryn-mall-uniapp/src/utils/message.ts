import type { ChatMessage, ChatMessageType } from '@/api/message/types'
import { parseLocalDateTimeTimestamp } from './datetime'

export type BusinessCardType = Extract<
  ChatMessageType,
  'NOTICE_CARD' | 'ORDER_CARD' | 'PRODUCT_CARD' | 'REFUND_CARD'
>
export type SendState = 'failed' | 'sending' | 'sent'
export type ViewChatMessage = ChatMessage & { sendState?: SendState }

/**
 * 会话时间线的时间分隔阈值：相邻消息超过 5 分钟（或跨自然日）才插一条
 * 居中时间，而不是每条气泡各挂一个时间戳 —— 同一分钟连发多条时后者会在
 * 屏幕上重复四五个时间，把聊天内容挤成次要信息。
 */
export const CHAT_TIME_DIVIDER_GAP_MS = 5 * 60 * 1000

export interface ChatTimelineTimeItem {
  kind: 'time'
  key: string
  label: string
}

export interface ChatTimelineMessageItem {
  kind: 'message'
  key: string
  message: ViewChatMessage
  /** 同一发送方连续消息组的首条：气泡组件据此决定是否显示头像 */
  groupStart: boolean
}

export type ChatTimelineItem = ChatTimelineTimeItem | ChatTimelineMessageItem

type ChatSenderRole = 'mine' | 'staff' | 'system'

function chatSenderRole(message: ViewChatMessage): ChatSenderRole {
  if (message.senderType === 'SYSTEM')
    return 'system'
  return message.senderType === 'MALL_USER' ? 'mine' : 'staff'
}

function isDifferentLocalDay(first: number, second: number) {
  const left = new Date(first)
  const right = new Date(second)
  return (
    left.getFullYear() !== right.getFullYear()
    || left.getMonth() !== right.getMonth()
    || left.getDate() !== right.getDate()
  )
}

/**
 * 把平铺的消息流收敛成「时间分隔 + 分组气泡」的渲染列表。
 *
 * 纯函数、不依赖 i18n：时间文案由调用方注入 `formatTime`（页面用
 * `formatNoticeTime`，单测可直接用恒等函数断言规则）。
 *
 * 规则：
 * 1. 首条消息前、相邻消息间隔 ≥ 5 分钟或跨自然日 → 插时间分隔；
 * 2. 与上一条同角色（我 / 客服 / 系统）且中间无分隔、无脏时间 → 同组，
 *    组内后续消息 `groupStart=false`（不重复头像）；
 * 3. `createTime` 解析失败的脏数据不插分隔、按角色分组兜底，绝不把原始串
 *    当时间展示；系统消息自身独立成组。
 */
export function buildChatTimeline(
  messages: ViewChatMessage[],
  options: { formatTime?: (raw?: null | string) => string } = {},
): ChatTimelineItem[] {
  const formatTime = options.formatTime ?? (raw => raw ?? '')
  const items: ChatTimelineItem[] = []
  let previous: ViewChatMessage | undefined

  for (const message of messages) {
    const timestamp = parseLocalDateTimeTimestamp(message.createTime)
    const previousTimestamp = previous
      ? parseLocalDateTimeTimestamp(previous.createTime)
      : null
    const role = chatSenderRole(message)
    const previousRole = previous ? chatSenderRole(previous) : null

    const crossedDay
      = timestamp !== null
        && previousTimestamp !== null
        && isDifferentLocalDay(previousTimestamp, timestamp)
    const gapTooLarge
      = timestamp !== null
        && previousTimestamp !== null
        && timestamp - previousTimestamp >= CHAT_TIME_DIVIDER_GAP_MS
    const label = timestamp !== null ? formatTime(message.createTime) : ''
    // 上一条时间不可解析（脏数据）时无法判断间隔：只要当前消息时间有效就补一条
    // 分隔，避免整段会话从头到尾没有时间参照。
    const unknownPreviousGap = previous !== undefined && previousTimestamp === null
    const needsDivider
      = label !== ''
        && (!previous || crossedDay || gapTooLarge || unknownPreviousGap)
    // key 优先用 clientMessageId：乐观气泡在服务端回包后 id 会从 local-* 换成
    // 真实 id，用 id 做 key 会让组件整条重挂、发送成功后重播一次入场动画。
    const stableKey = message.clientMessageId || message.id

    if (needsDivider) {
      items.push({
        kind: 'time',
        key: `time:${stableKey}`,
        label,
      })
    }

    items.push({
      kind: 'message',
      key: `message:${stableKey}`,
      message,
      groupStart:
        needsDivider
        || !previous
        || role === 'system'
        || previousRole !== role,
    })
    previous = message
  }

  return items
}

export function createClientMessageId() {
  return `msg-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

/**
 * 本地时间 `YYYY-MM-DD HH:mm:ss`，与后端 LocalDateTime 下发格式一致。
 *
 * 乐观消息不能用 `toISOString()`：它是 UTC 字符串（`...T13:30:00.000Z`），
 * 而全仓时间解析口径（`parseLocalDateTimeTimestamp`）把这种串按本地时间读，
 * 东八区会整体差 8 小时，首批消息的时间分隔会短暂显示错误时间。
 */
export function formatMessageLocalDateTime(value: Date = new Date()) {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
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
