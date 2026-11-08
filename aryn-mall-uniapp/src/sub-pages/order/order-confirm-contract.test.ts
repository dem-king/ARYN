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

/**
 * 「无收货地址结算」契约守门。
 *
 * 背景（真实缺陷）：用户没有收货地址时进确认页，后端 settlementOrder 对配送方式
 * 1/3 直接抛「用户收货地址不存在」，整次结算失败——商品列表零行、金额 ¥0.00；
 * 补完地址回来页面仍停在空数据态（选址只赋值不重结算），怎么操作都恢复不了。
 *
 * 修复分四层，缺一即复发：
 * - 后端：结算为预览，无地址跳过运费与收件人回填，不再整单失败（提交订单仍强校验）；
 * - 确认页：选址事件必须触发重新结算，把商品金额与运费补回来；
 * - 确认页：默认地址接口失败时也必须走到 initData，不能让页面永远 loading；
 * - 展示：无地址时运费行显示「选择地址后计算」，不显示误导性的 ￥0.00。
 */
describe('order confirm no-address settlement contract', () => {
  it('re-settles after the user picks an address', () => {
    const confirm = source(ORDER_CONFIRM)
    const handler = functionSource(confirm, 'handleSelectedAddressUpdate')
    // 选完地址只赋值不重结算 = 图二死态：商品金额永远是 ¥0.00
    expect(handler).toContain('selectedAddress.value = newAddress')
    expect(handler).toContain('toSettlement()')
  })

  it('always reaches initData even when the default-address lookup fails', () => {
    const confirm = source(ORDER_CONFIRM)
    const loader = functionSource(confirm, 'getDefaultAddress')
    // getDefault 异常时 initData 悬空会让页面永远停在 loading
    expect(loader).toContain('catch')
    expect(loader).toContain('initData()')
  })

  it('keeps createOrder strict and settlement preview tolerant of missing address', () => {
    const service = repoSource(ORDER_SERVICE)
    // 结算为预览：无地址不再抛 ERROR_50002（否则确认页空 ¥0.00 且无恢复入口），
    // 全服务仅 createOrder 保留 2 处强校验；有人把抛错加回结算侧即复发
    const strictThrows = service.match(/ERROR_50002\.getCode\(\)/g) || []
    expect(strictThrows).toHaveLength(2)
    expect(service).toContain('结算为预览')
  })

  it('shows shipping as pending while no address is chosen', () => {
    const item = source('src/sub-pages/order/order-confirm/components/ShopOrderItem.vue')
    expect(item).toContain('addressPending')
    expect(item).toContain('shippingPending')
  })

  it('returns the saved address straight to the confirm page in choose mode', () => {
    const list = source('src/sub-pages/user/address/index.vue')
    const form = source('src/sub-pages/user/address/form.vue')
    // 列表选择模式跳表单要透传 choose 上下文；表单保存成功后回传新地址并连退两级
    // （结算页 ← 地址列表 ← 表单），省去「回列表再点一次选中」
    expect(list).toContain('isChoosing.value ? { placeChooseFlag: \'true\' } : {}')
    expect(form).toContain('String(options?.placeChooseFlag) === \'true\'')
    expect(form).toContain('uni.$emit(\'update:selectedAddress\', saved)')
    expect(form).toContain('uni.navigateBack({ delta: 2 })')
  })
})
