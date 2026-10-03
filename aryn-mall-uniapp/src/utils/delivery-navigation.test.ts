import { describe, expect, it } from 'vitest'
import { resolveDeliveryDestination, resolveMapCoordinates } from './delivery-navigation'

/**
 * 导航目的地口径的守门测试。
 *
 * 线上真实数据：船供单 `delivery_task.recipient_address` 为 NULL，只有
 * `port_name`（福建港）和 `berth`（321）。若只认收货地址，司机点「导航」
 * 拿到的是「暂无收货地址」，等于按钮白放。
 */

describe('resolveDeliveryDestination', () => {
  it('有收货地址时用收货地址', () => {
    expect(resolveDeliveryDestination({ recipientAddress: '上海市浦东新区峨山路 91 弄' }))
      .toBe('上海市浦东新区峨山路 91 弄')
  })

  it('收货地址为空时回落到港口 + 泊位', () => {
    expect(resolveDeliveryDestination({
      recipientAddress: null,
      portName: '福建港',
      berth: '321',
    })).toBe('福建港 321')
  })

  it('只有港口没有泊位时只给港口名', () => {
    expect(resolveDeliveryDestination({ recipientAddress: '', portName: '上海港' })).toBe('上海港')
  })

  it('泊位不能单独成目的地（「321」本身没有任何搜索价值）', () => {
    expect(resolveDeliveryDestination({ recipientAddress: null, portName: null, berth: '321' })).toBe('')
  })

  it('地址与港口都缺失时返回空串，供上层提示「暂无收货地址」', () => {
    expect(resolveDeliveryDestination({})).toBe('')
    expect(resolveDeliveryDestination(null)).toBe('')
    expect(resolveDeliveryDestination(undefined)).toBe('')
  })

  it('纯空白地址不当作有效目的地（否则复制出来是空文本）', () => {
    expect(resolveDeliveryDestination({ recipientAddress: '   ' })).toBe('')
    expect(resolveDeliveryDestination({ recipientAddress: '  ', portName: ' 福建港 ' })).toBe('福建港')
  })
})

describe('resolveMapCoordinates', () => {
  it('经纬度齐全时原样返回', () => {
    expect(resolveMapCoordinates({ latitude: 31.2304, longitude: 121.4737 }))
      .toEqual({ latitude: 31.2304, longitude: 121.4737 })
  })

  it('缺失任一坐标即视为无坐标（回落到复制地址）', () => {
    expect(resolveMapCoordinates({ latitude: 31.2304 })).toBeNull()
    expect(resolveMapCoordinates({ longitude: 121.4737 })).toBeNull()
    expect(resolveMapCoordinates({})).toBeNull()
    expect(resolveMapCoordinates(null)).toBeNull()
  })

  it('(0,0) 哨兵值不算有效坐标', () => {
    expect(resolveMapCoordinates({ latitude: 0, longitude: 0 })).toBeNull()
  })

  it('赤道/本初子午线上的单侧 0 仍算有效坐标', () => {
    expect(resolveMapCoordinates({ latitude: 0, longitude: 121.4737 }))
      .toEqual({ latitude: 0, longitude: 121.4737 })
  })

  it('naN / null 不传进 openLocation（那会弹一个司机看不懂的失败）', () => {
    expect(resolveMapCoordinates({ latitude: Number.NaN, longitude: 121.4737 })).toBeNull()
    expect(resolveMapCoordinates({ latitude: null, longitude: null })).toBeNull()
  })
})
