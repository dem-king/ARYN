import type { ViewChatMessage } from './message'
import type { ChatMessage } from '@/api/message/types'
import { describe, expect, it } from 'vitest'
import {
  buildChatTimeline,
  businessCardRoute,
  CHAT_TIME_DIVIDER_GAP_MS,
  customerServiceRoute,
  formatMessageLocalDateTime,
  latestSequence,
  mergeCursorMessages,
  mergeServerMessage,
  resolveImageMimeType,
} from './message'

function message(clientMessageId: string, seqNo: number): ChatMessage {
  return {
    clientMessageId,
    content: clientMessageId,
    conversationId: 'conversation-1',
    createTime: '2026-07-21 10:00:00',
    id: `message-${seqNo}`,
    messageType: 'TEXT',
    senderId: 'member-1',
    senderType: 'MALL_USER',
    seqNo,
  }
}

describe('mobile message helpers', () => {
  it('merges the HTTP response by stable clientMessageId', () => {
    const optimistic = {
      ...message('client-1', Number.MAX_SAFE_INTEGER),
      id: 'local',
      sendState: 'sending' as const,
    }
    expect(mergeServerMessage([optimistic], message('client-1', 7))).toEqual([
      expect.objectContaining({ id: 'message-7', sendState: 'sent', seqNo: 7 }),
    ])
  })

  it('deduplicates cursor recovery and advances to the latest sequence', () => {
    const merged = mergeCursorMessages(
      [message('client-1', 1)],
      [message('client-1', 1), message('client-2', 2)],
    )
    expect(merged).toHaveLength(2)
    expect(latestSequence(merged)).toBe(2)
  })

  it('only creates known internal business-card routes', () => {
    expect(businessCardRoute('ORDER_CARD', { orderId: 'order 1' })).toBe(
      '/sub-pages/order/order-detail/index?id=order%201',
    )
    expect(businessCardRoute('REFUND_CARD', {})).toBe('')
  })

  it('encodes a station customer-service card route', () => {
    const route = customerServiceRoute({
      messageType: 'PRODUCT_CARD',
      payload: { productId: 'p-1' },
    })
    expect(
      route.startsWith(
        '/sub-pages/message/chat/index?cardType=PRODUCT_CARD&cardPayload=',
      ),
    ).toBe(true)
  })

  it('resolves the real image mimeType from the file extension', () => {
    expect(resolveImageMimeType('/tmp/a/b/c.jpg')).toBe('image/jpeg')
    expect(resolveImageMimeType('/tmp/a/b/c.JPEG')).toBe('image/jpeg')
    expect(resolveImageMimeType('https://cdn.example.com/x.png?imageView=1')).toBe('image/png')
    expect(resolveImageMimeType('wxfile://tmp_123.webp')).toBe('image/webp')
  })

  it('falls back to jpeg for temp paths without an extension', () => {
    // 小程序相机临时文件常常没有扩展名：后端只认 jpeg/png/webp，回退 jpeg 才不会 400
    expect(resolveImageMimeType('wxfile://tmp_123')).toBe('image/jpeg')
    expect(resolveImageMimeType('')).toBe('image/jpeg')
    expect(resolveImageMimeType(undefined)).toBe('image/jpeg')
  })
})

function chatMessage(
  seqNo: number,
  overrides: Partial<ViewChatMessage> = {},
): ViewChatMessage {
  return {
    clientMessageId: `client-${seqNo}`,
    content: `message-${seqNo}`,
    conversationId: 'conversation-1',
    createTime: '2026-07-21 10:00:00',
    id: `message-${seqNo}`,
    messageType: 'TEXT',
    senderId: seqNo % 2 === 0 ? 'member-1' : 'staff-1',
    senderType: seqNo % 2 === 0 ? 'MALL_USER' : 'SYS_USER',
    seqNo,
    ...overrides,
  }
}

const identityFormatter = (raw?: null | string) => raw ?? ''

describe('formatMessageLocalDateTime', () => {
  it('formats a Date as the backend local datetime pattern instead of a UTC ISO string', () => {
    const local = new Date(2026, 6, 21, 9, 5, 3)
    expect(formatMessageLocalDateTime(local)).toBe('2026-07-21 09:05:03')
    // 乐观消息的时间必须能被时间线解析，否则首批消息的分隔时间会差一个时区
    const timeline = buildChatTimeline(
      [chatMessage(1, { createTime: formatMessageLocalDateTime(local) })],
      { formatTime: identityFormatter },
    )
    expect(timeline[0]).toMatchObject({
      kind: 'time',
      label: '2026-07-21 09:05:03',
    })
  })
})

describe('buildChatTimeline', () => {
  it('starts the timeline with a time divider and hides repeated avatars in a group', () => {
    const timeline = buildChatTimeline(
      [
        chatMessage(1, { senderType: 'SYS_USER', createTime: '2026-07-21 10:00:00' }),
        chatMessage(2, { senderType: 'SYS_USER', createTime: '2026-07-21 10:00:30' }),
        chatMessage(3, { senderType: 'SYS_USER', createTime: '2026-07-21 10:01:00' }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline.map(item => item.kind)).toEqual(['time', 'message', 'message', 'message'])
    expect(timeline[0]).toMatchObject({ label: '2026-07-21 10:00:00' })
    expect(timeline[1]).toMatchObject({ groupStart: true })
    expect(timeline[2]).toMatchObject({ groupStart: false })
    expect(timeline[3]).toMatchObject({ groupStart: false })
  })

  it('opens a new group when the sender role changes without adding a divider', () => {
    const timeline = buildChatTimeline(
      [
        chatMessage(1, { senderType: 'SYS_USER', createTime: '2026-07-21 10:00:00' }),
        chatMessage(2, { senderType: 'MALL_USER', createTime: '2026-07-21 10:00:20' }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline).toHaveLength(3)
    expect(timeline[2]).toMatchObject({ groupStart: true })
  })

  it('inserts a divider exactly at the five-minute gap', () => {
    const first = new Date(2026, 6, 21, 10, 0, 0).getTime()
    const next = first + CHAT_TIME_DIVIDER_GAP_MS
    const timeline = buildChatTimeline(
      [
        chatMessage(1, { createTime: '2026-07-21 10:00:00' }),
        chatMessage(3, {
          createTime: '2026-07-21 10:05:00',
          senderType: 'SYS_USER',
        }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline.filter(item => item.kind === 'time')).toHaveLength(2)
    expect(timeline[2]).toMatchObject({ kind: 'time', label: '2026-07-21 10:05:00' })
    expect(timeline[3]).toMatchObject({ groupStart: true })
    expect(next - first).toBe(CHAT_TIME_DIVIDER_GAP_MS)
  })

  it('keeps one group just under the five-minute gap', () => {
    const timeline = buildChatTimeline(
      [
        chatMessage(1, { createTime: '2026-07-21 10:00:00' }),
        chatMessage(3, { createTime: '2026-07-21 10:04:59', senderType: 'SYS_USER' }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline.filter(item => item.kind === 'time')).toHaveLength(1)
  })

  it('inserts a divider across local midnight even when the gap is short', () => {
    const timeline = buildChatTimeline(
      [
        chatMessage(1, { createTime: '2026-07-21 23:58:00' }),
        chatMessage(3, { createTime: '2026-07-22 00:01:00', senderType: 'SYS_USER' }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline.filter(item => item.kind === 'time')).toHaveLength(2)
  })

  it('treats system messages as standalone groups and breaks the surrounding group', () => {
    const timeline = buildChatTimeline(
      [
        chatMessage(2, { createTime: '2026-07-21 10:00:00' }),
        chatMessage(5, { senderType: 'SYSTEM', createTime: '2026-07-21 10:00:10' }),
        chatMessage(2, { createTime: '2026-07-21 10:00:20', seqNo: 6 }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline.filter(item => item.kind === 'message').map(item =>
      item.kind === 'message' ? item.groupStart : null,
    )).toEqual([true, true, true])
  })

  it('never renders an unparsable time as a divider', () => {
    const timeline = buildChatTimeline(
      [
        chatMessage(1, { createTime: 'not-a-date' }),
        chatMessage(2, { createTime: '2026-07-21 10:00:00' }),
      ],
      { formatTime: identityFormatter },
    )
    expect(timeline[0]?.kind).toBe('message')
    expect(timeline.filter(item => item.kind === 'time')).toHaveLength(1)
  })

  it('handles an empty conversation', () => {
    expect(buildChatTimeline([], { formatTime: identityFormatter })).toEqual([])
  })

  it('keeps a stable key when the optimistic message is replaced by the server copy', () => {
    const optimistic: ViewChatMessage = {
      ...chatMessage(1, {
        clientMessageId: 'client-stable',
        id: 'local-client-stable',
        senderType: 'MALL_USER',
      }),
    }
    const before = buildChatTimeline([optimistic], { formatTime: identityFormatter })
    const afterServer = buildChatTimeline(
      [
        {
          ...optimistic,
          id: 'message-99',
          seqNo: 99,
          sendState: 'sent',
        },
      ],
      { formatTime: identityFormatter },
    )
    expect(before.map(item => item.key)).toEqual(afterServer.map(item => item.key))
    expect(before[1]?.key).toBe('message:client-stable')
  })
})
