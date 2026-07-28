import { describe, expect, it } from 'vitest'
import {
  buildDeliveryMethods,
  customerDeliveryTimeline,
  deliveryWayLabel,
} from '@/api/order/mallDelivery'

describe('客户商城配送流程', () => {
  it('范围内可选择商城配送，范围外展示不可用原因', () => {
    expect(
      buildDeliveryMethods({ available: true, reason: '' }),
    ).toContainEqual({
      name: '商城配送',
      value: '3',
    })
    expect(
      buildDeliveryMethods({
        available: false,
        reason: '当前地址不在配送范围',
      }),
    ).toContainEqual(
      expect.objectContaining({
        disabled: true,
        reason: '当前地址不在配送范围',
        value: '3',
      }),
    )
  })

  it('配送方式文案保持快递、自提兼容并识别商城配送', () => {
    expect(deliveryWayLabel('1')).toBe('普通快递')
    expect(deliveryWayLabel('2')).toBe('上门自提')
    expect(deliveryWayLabel('3')).toBe('商城配送')
  })

  it('客户进度只由公开时间点生成，不暴露内部日志和配送员电话', () => {
    const timeline = customerDeliveryTimeline({
      assigneeMobile: '13800000000',
      exceptionSummary: '内部风控说明',
      logs: [{ description: '内部日志' }],
      status: 'DELIVERING',
      assignedAt: '2026-07-28 10:00:00',
      pickedUpAt: '2026-07-28 11:00:00',
    })
    expect(JSON.stringify(timeline)).not.toContain('13800000000')
    expect(JSON.stringify(timeline)).not.toContain('内部')
    expect(timeline.at(-1)?.title).toBe('配送员已取货，正在送往收货地址')
  })
})
