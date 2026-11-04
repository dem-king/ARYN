import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品卡片划线原价的渲染契约。
 *
 * 背景：管理端装修「商品」组件早就有「商品原价」开关，但 C 端从未实现渲染，
 * 该开关长期是一条死链路（能勾、能存、无效果）。本次补齐后，用本测试锁住两件事：
 *   1. 每个商品楼层都必须渲染划线原价 —— 漏掉任何一个，运营在该楼层勾了开关也没反应；
 *   2. 都必须受 `showOriginalPrice` 开关控制 —— 否则运营关不掉，等于强制划线；
 * 并且都必须经过 shouldShowOriginalPrice 判定 —— 存量商品原价多为 0，
 * 少了这层判定会划出「￥0」。
 *
 * 这里断言源码而非渲染结果：小程序组件依赖 uni 运行时，单测跑不起来，
 * 而漏渲染/漏判定的失误恰好都能在源码层面发现（同 layout-guard.test.ts 的思路）。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 商品类装修楼层：都应支持划线价（会员价/捡漏价命中划当前售价，普通价划原价） */
const GOODS_FLOORS = [
  'src/components/diy/diy-goods/index.vue',
  'src/components/diy/diy-goods-group/index.vue',
  'src/components/diy/diy-goods-ranking/index.vue',
  'src/components/diy/diy-goods-scroll/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
]

describe('商品卡片划线原价', () => {
  it.each(GOODS_FLOORS)('%s 渲染划线价', (file) => {
    expect(source(file)).toMatch(/cardStrikePrice\(/)
  })

  it.each(GOODS_FLOORS)('%s 的划线价由运营开关控制（关得掉）', (file) => {
    const code = source(file)
    // 开关必须作为「是否允许划线」参数进入共享专区价口径：
    // 关闭时统一返回 strikePrice=null，而不是每个楼层自己再判一遍
    expect(code).toMatch(/cardPriceOf\(\s*item,[\s\S]{0,40}showOriginalPrice/)
  })

  it.each(GOODS_FLOORS)('%s 使用删除线样式，而非仅置灰', (file) => {
    expect(source(file)).toMatch(/text-decoration:\s*line-through/)
  })

  it('会员专区/低价捡漏 Tab 的价格区同样受「显示原价」开关约束', () => {
    const tabs = source('src/components/diy/diy-goods-tabs/index.vue')
    expect(tabs).toMatch(/showOriginal:\s*showData\.value\.showOriginalPrice/)
    expect(tabs).toContain('cardStrikePrice(item)')
    expect(tabs).toMatch(/text-decoration:\s*line-through/)
  })

  it('划线口径收敛到共享纯函数：开关关闭不划线，且仅原价严格高于售价才划线', () => {
    // 各楼层不再自行比较原价/售价，统一由 zone-price 保证「原价严格大于售价」
    // （存量商品原价多为 0，直接渲染会划出「¥0」）
    const util = source('src/utils/zone-price.ts')
    expect(util).toContain('input.showOriginal !== false')
    expect(util).toContain('original > sales')
  })

  it('首页商品楼层（diy-goods）的开关缺省为关，不做「缺省即显示」兜底', () => {
    // 存量装修数据里 showOriginalPrice 都是 false 且缺 originalPriceSize 等字段，
    // 若写成 !== false 之类的兜底，会让未开启的楼层也划出原价
    const code = source('src/components/diy/diy-goods/index.vue')
    expect(code).toContain('showOriginalPrice === true')
  })
})

/**
 * 分类页与商品列表页共用同一份商品卡片（goods-list-panel），
 * 没有装修开关，口径直接跟随商详：原价严格大于售价才划线。
 *
 * 接入会员价/捡漏价后划线基准由共享层给出：普通价划原价（原价 > 售价时才划），
 * 身份价命中时划当前售价——页面不再自己比价，避免同一商品在分类页与会员专区
 * 划出两条不一致的划线价。
 */
describe('分类/列表商品卡片划线原价', () => {
  const panel = 'src/components/goods-list-panel/index.vue'

  it('划线价走共享专区价口径，存量商品原价为 0 时不划出「¥0」', () => {
    const code = source(panel)
    // 划线基准由 utils/zone-price 统一判定（原价严格大于售价才给出普通划线）
    expect(code).toContain('zone.cardPriceOf(item)')
    expect(code).toContain('cardPrice(item).strikePrice !== null')
  })

  it('使用删除线样式，而非仅置灰', () => {
    const code = source(panel)
    const rule = code.slice(code.indexOf('.goods-price-original'))
    expect(rule.slice(0, rule.indexOf('}'))).toMatch(/text-decoration:\s*line-through/)
  })
})

/**
 * 购物车、收藏、足迹、推荐、选货等商品展示位：都必须按同一口径
 * （原价严格大于售价）展示划线价，避免同一商品在不同页面价格口径不一致。
 */
describe('其他商品展示位划线原价', () => {
  /** 已接入共享专区价的展示位：划线基准由 zone-price 给出（含会员价/捡漏价划当前售价） */
  const ZONE_PRICE_SURFACES = [
    'src/components/waterfall-goods/index.vue',
    'src/sub-pages/user/collect/index.vue',
    'src/sub-pages/user/footprint/index.vue',
  ]
  /** 尚未接入专区价的展示位：仍按「原价严格大于售价」自行判定 */
  const ORIGINAL_PRICE_SURFACES = [
    'src/pages/user/shopping-cart/index.vue',
    'src/sub-pages/product/ship-supply/index.vue',
  ]
  const SURFACES = [...ZONE_PRICE_SURFACES, ...ORIGINAL_PRICE_SURFACES]

  it.each(ZONE_PRICE_SURFACES)('%s 划线价走共享专区价口径', (file) => {
    const code = source(file)
    expect(code).toContain('cardPriceOf(')
    // 只有共享层给出 strikePrice 时才渲染划线，页面不自行比较原价
    expect(code).toContain('strikePrice !== null')
  })

  it.each(ORIGINAL_PRICE_SURFACES)('%s 使用 shouldShowOriginalPrice 判定', (file) => {
    expect(source(file)).toContain('shouldShowOriginalPrice')
  })

  it.each(SURFACES)('%s 使用删除线样式', (file) => {
    expect(source(file)).toMatch(/decoration="line-through"|text-decoration:\s*line-through/)
  })
})
