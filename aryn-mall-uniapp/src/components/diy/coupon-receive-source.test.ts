import { describe, expect, it } from 'vitest'
import { orderCouponsByIds, resolveCouponReceiveSource } from './coupon-receive-source'

describe('领券组件数据来源', () => {
  it('旧配置没有 dataSource 时按自动领取列表处理', () => {
    expect(resolveCouponReceiveSource(undefined, 3)).toEqual({
      ids: [],
      mode: 'automatic',
    })
  })

  it('手选模式按配置顺序去重并截断到最多显示数量', () => {
    expect(resolveCouponReceiveSource({
      mode: 'manual',
      targetIds: ['b', '', 'a', 'b', 'c'],
    }, 2)).toEqual({
      ids: ['b', 'a'],
      mode: 'manual',
    })
  })

  it('最多显示数量非法时兜底为 1', () => {
    expect(resolveCouponReceiveSource({
      mode: 'manual',
      targetIds: ['a', 'b'],
    }, Number.NaN).ids).toEqual(['a'])
  })

  it('byIds 返回乱序时按配置顺序回填，查不到的券丢弃', () => {
    const rows = [{ id: 'b' }, { id: 'a' }]
    expect(orderCouponsByIds(rows, ['a', 'expired', 'b']).map(item => item.id))
      .toEqual(['a', 'b'])
  })
})
