import type { ChatMessage } from '@/api/message/types'
import { describe, expect, it } from 'vitest'
import {
  businessCardRoute,
  customerServiceRoute,
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
