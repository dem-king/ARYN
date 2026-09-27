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

/** 商品类装修楼层：都应支持划线原价 */
const GOODS_FLOORS = [
  'src/components/diy/diy-goods/index.vue',
  'src/components/diy/diy-goods-group/index.vue',
  'src/components/diy/diy-goods-ranking/index.vue',
  'src/components/diy/diy-goods-scroll/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
]

describe('商品卡片划线原价', () => {
  it.each(GOODS_FLOORS)('%s 渲染划线原价', (file) => {
    expect(source(file)).toContain('shouldShowOriginalPrice')
  })

  it.each(GOODS_FLOORS)('%s 的划线价由运营开关控制（关得掉）', (file) => {
    const code = source(file)
    // 开关必须参与判定，否则运营在后台取消勾选也依然会划线
    expect(code).toMatch(/showOriginalPrice\s*&&\s*shouldShowOriginalPrice|showOriginalPrice\s*===/m)
  })

  it.each(GOODS_FLOORS)('%s 使用删除线样式，而非仅置灰', (file) => {
    expect(source(file)).toMatch(/text-decoration:\s*line-through/)
  })

  it('划线原价的判定口径与商详页一致（同一份 price-display）', () => {
    // 商详页 GoodsInfo.vue 用的是 salesPrice < originalPrice，此处共用 shouldShowOriginalPrice，
    // 二者都要求「原价严格大于售价」，避免同一商品在列表划线、在详情不划线
    const helper = source('src/components/diy/price-display.ts')
    expect(helper).toContain('original > current')
  })

  it('首页商品楼层（diy-goods）的开关缺省为关，不做「缺省即显示」兜底', () => {
    // 存量装修数据里 showOriginalPrice 都是 false 且缺 originalPriceSize 等字段，
    // 若写成 !== false 之类的兜底，会让未开启的楼层也划出原价
    const code = source('src/components/diy/diy-goods/index.vue')
    expect(code).toContain('showOriginalPrice === true')
  })
})
