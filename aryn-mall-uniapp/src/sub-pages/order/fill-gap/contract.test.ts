import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「还差 X 元」→ 凑单页链路守门。
 *
 * 这类改动最容易在后续迭代里断链：入口被改回纯文本、凑单页换了商品池、
 * 返回结算页不再重算导致用户看到旧运费。类型检查发现不了，因此按源码结构断言。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const PAGE = 'src/sub-pages/order/fill-gap/index.vue'
const CHECKOUT = 'src/sub-pages/order/order-confirm/index.vue'
const ITEM = 'src/sub-pages/order/order-confirm/components/ShopOrderItem.vue'
const PAGES_JSON = 'src/pages.json'
const ZH = 'src/sub-pages/locales/modules/checkout.zh-CN.ts'
const EN = 'src/sub-pages/locales/modules/checkout.en-US.ts'

describe('fill-gap contract', () => {
  it('凑单页只拉随订单规则商品且按售价升序，不自行累计金额', () => {
    const page = source(PAGE)
    // 只有 freight_type='2' 会抬高门槛基数，包邮/固定运费商品加购不改变差额
    expect(page).toContain('freightType: \'2\'')
    expect(page).toContain('asc: \'sales_price\'')
    // 页面不碰金额计算：门槛/差额只用 URL 透传值作文案
    expect(page).not.toContain('paymentPrice')
    expect(page).not.toContain('reduce((sum')
  })

  it('加购后返回结算页会重建 SKU 清单并重新结算，避免展示旧运费与旧差额', () => {
    const page = source(PAGE)
    expect(page).toContain('order-confirm:refresh')
    expect(page).toMatch(/addedCount\.value > 0/)
    // 只加购不同步结算快照，返回后新商品不会进入本单
    expect(page).toContain('setGoodsList')
    const checkout = source(CHECKOUT)
    expect(checkout).toContain('uni.$on(\'order-confirm:refresh\'')
    expect(checkout).toContain('uni.$off(\'order-confirm:refresh\'')
    expect(checkout).toMatch(/handleFillGapRefresh[\s\S]*?state\.orderParams\.skuReqList = buildSkuReqList\(\)[\s\S]*?toSettlement\(\)/)
  })

  it('结算页把门槛与差额透传给凑单页，且只对购物车下单开放入口', () => {
    const checkout = source(CHECKOUT)
    expect(checkout).toContain('@fill-gap="openFillGap"')
    expect(checkout).toContain('/sub-pages/order/fill-gap/index?')
    expect(checkout).toMatch(/state\.createWay !== '1'/)
    expect(checkout).toContain(':fill-gap-enabled="state.createWay === \'1\'"')
    expect(checkout).toMatch(/gap=\$\{gap\}/)
    expect(checkout).toMatch(/threshold=\$\{threshold\}/)

    const item = source(ITEM)
    expect(item).toContain('(e: \'fillGap\')')
    expect(item).toContain('fillGapEnabled')
    expect(item).toMatch(/canFillGap[\s\S]*?gap > 0/)
    expect(item).toContain('fee-row__sub--link')
    expect(item).toContain('onFillGapTap')
  })

  it('页面已注册进 pages.json，中英文案键齐全', () => {
    expect(source(PAGES_JSON)).toContain('order/fill-gap/index')
    for (const file of [ZH, EN]) {
      const locale = source(file)
      expect(locale).toContain('fillGap:')
      for (const key of ['title', 'go', 'gapHint', 'thresholdHint', 'add', 'added', 'back', 'empty', 'footnote']) {
        expect(locale).toContain(`${key}:`)
      }
    }
  })
})
