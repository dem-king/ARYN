import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「购物车同款多行 → 结算按 SKU 合并」契约守门。
 *
 * 背景（真实缺陷）：购物车按靠港分组，同一 SKU 可以合法地分别落在
 * 「此前靠港计划（已结束）」的滞留行与「当前无靠港」行（vessel_call_id 为空）。
 * 结算页 initData 原先把 createGoodsList 原样映射成 skuReqList，而后端要求
 * 一个 SKU 至多一行：POST /app/orderinfo/settlement 返回
 * 「订单商品不能包含重复SKU」，确认页空白、金额 ¥0.00。
 *
 * 修复分两层，缺一即复发：
 * - 前端：提交前只合并数量（购物车仍分行展示，保留每行的靠港归属）；
 * - 后端：createOrder / settlementOrder 两条入口都归一化，兜住共享购物车
 *   按成员拆行等前端列表里看不到的重复来源。
 *
 * 同时守住「防串船校验继续用原始行」：合并后的列表丢掉了每行的 vesselCallId，
 * 拿它做归属比对会静默放过跨靠港下单。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

/** 截取页面源码里一个函数的实现：到下一个顶层 `function` / `async function` 为止 */
function functionSource(page: string, name: string) {
  const start = page.indexOf(`function ${name}(`)
  if (start < 0)
    return ''
  const ends = [
    page.indexOf('\nfunction ', start + 1),
    page.indexOf('\nasync function ', start + 1),
  ].filter(index => index > start)
  return page.slice(start, ends.length ? Math.min(...ends) : page.length)
}

const ORDER_CONFIRM = 'src/sub-pages/order/order-confirm/index.vue'
const CART_PAGE = 'src/pages/user/shopping-cart/index.vue'
const ORDER_SERVICE
  = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java'

describe('order confirm duplicate SKU merge contract', () => {
  it('builds skuReqList from the merged duplicate-SKU list', () => {
    const confirm = source(ORDER_CONFIRM)
    const initData = functionSource(confirm, 'initData')
    // 提交参数必须由合并后的列表构造；原样映射会让同 SKU 的两行一起提交
    expect(initData).toContain('mergeDuplicateSkuItems(createGoodsList.value || [])')
    expect(initData).toContain('state.orderParams.skuReqList = orderItemList')
  })

  it('keeps the cross-port-call guard on the raw createGoodsList rows', () => {
    const confirm = source(ORDER_CONFIRM)
    const toPay = functionSource(confirm, 'toPay')
    // 合并会丢掉每行的 vesselCallId 归属，防串船必须逐行比对原始列表
    expect(toPay).toContain('createGoodsList.value')
    expect(toPay).toContain('vesselCallId')
    expect(toPay).not.toContain('mergeDuplicateSkuItems')
  })

  it('normalizes duplicate SKUs on both order creation entry points', () => {
    const service = repoSource(ORDER_SERVICE)
    // createOrder 与 settlementOrder 都要归一化：购物车结算与直接下单走不同入口，
    // 只改一处时另一条路径仍会报「订单商品不能包含重复SKU」
    const normalizeCalls = service.match(/OrderSkuReqNormalizer\.normalize/g) || []
    expect(normalizeCalls).toHaveLength(2)
  })

  it('explains duplicated SKU rows on the cart page instead of merging the display', () => {
    const cart = source(CART_PAGE)
    // 购物车保留分行展示（靠港归属信息），只对重复行挂一句结算说明
    expect(cart).toContain('duplicateSkuIds')
    expect(cart).toContain('cart.duplicateMergeHint')
    expect(cart).toContain('duplicateSkuIds.has(String(goods.skuId))')
  })
})
