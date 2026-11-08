import { describe, expect, it } from 'vitest'
import { filterCapabilitiesForClientPlatform } from './pay'

const capabilities = [
  { tradeType: 'WX_JSAPI_PAY', channel: '1' },
  { tradeType: 'ALI_JSAPI_PAY', channel: '2' },
  { tradeType: 'FREE_PAY', channel: '0' },
]

describe('支付能力客户端平台过滤', () => {
  it('微信小程序只保留微信渠道与零元支付', () => {
    expect(filterCapabilitiesForClientPlatform(capabilities, 'WX_MA')).toEqual([
      { tradeType: 'WX_JSAPI_PAY', channel: '1' },
      { tradeType: 'FREE_PAY', channel: '0' },
    ])
  })

  it('支付宝小程序只保留支付宝渠道与零元支付', () => {
    expect(filterCapabilitiesForClientPlatform(capabilities, 'ALI_MINI')).toEqual([
      { tradeType: 'ALI_JSAPI_PAY', channel: '2' },
      { tradeType: 'FREE_PAY', channel: '0' },
    ])
  })

  it('H5/App/未知平台不额外裁剪', () => {
    expect(filterCapabilitiesForClientPlatform(capabilities, 'H5')).toEqual(capabilities)
    expect(filterCapabilitiesForClientPlatform(capabilities, '')).toEqual(capabilities)
  })
})
