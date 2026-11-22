import { describe, expect, it } from 'vitest'
import {
  COLD_CHAIN_ICON_SRC,
  DEFAULT_SERVICE_TAGS,
  resolveServiceTags,
  SERVICE_TAG_CODES,
  SERVICE_TAG_ICON_NAMES,
} from './goods-service-tags'

describe('resolveServiceTags', () => {
  it('按运营配置顺序保留合法标签', () => {
    expect(resolveServiceTags(['cold_chain', 'damage_refund', 'invoice'])).toEqual([
      'cold_chain',
      'damage_refund',
      'invoice',
    ])
  })

  it('未配置或全空时回退全局三项', () => {
    expect(resolveServiceTags(null)).toEqual(DEFAULT_SERVICE_TAGS)
    expect(resolveServiceTags([])).toEqual(DEFAULT_SERVICE_TAGS)
    expect(resolveServiceTags(['', '  ', null])).toEqual(DEFAULT_SERVICE_TAGS)
  })

  it('过滤未知 code 与重复项，并统一小写', () => {
    expect(resolveServiceTags([' Cold_Chain ', 'cold_chain', 'unknown', 'autheNtic'])).toEqual([
      'cold_chain',
      'authentic',
    ])
  })

  it('最多保留四项，与后端上限一致', () => {
    const all = [...SERVICE_TAG_CODES, 'authentic'] as string[]
    expect(resolveServiceTags(all)).toHaveLength(4)
  })

  it('每个承诺都有语义图标，冷链单独用雪花资源', () => {
    expect(SERVICE_TAG_ICON_NAMES).toEqual({
      cold_chain: 'refresh',
      damage_refund: 'rollback',
      invoice: 'spool',
      authentic: 'secured',
    })
    expect(COLD_CHAIN_ICON_SRC).toBe('/static/product/promise-cold-chain.svg')
  })
})
