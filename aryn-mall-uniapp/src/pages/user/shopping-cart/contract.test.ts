import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 购物车「防串船分组」契约守门。
 *
 * 背景（真实缺陷）：购物车行新增了 `vessel_id` / `vessel_call_id` / `purchase_scene`
 * 三个归属快照，前端按 `vesselCallId` 把购物车分组成「当前船舶 / 其他靠港计划」，
 * 结算时再据此拦截跨靠港下单。但手写的 `ShoppingCartMapper.xml` 用的是**显式列清单**，
 * 这三个列没被加进去 —— 接口照常返回 200，字段却全为 null。
 *
 * 漏网的隐蔽之处：前后端的写入路径（加购 DTO、实体、单测）全都正确，
 * 编译、类型检查、既有单测也全绿，只有列表接口悄悄把归属丢成 null：
 * 用户看到的是「加购时明明选了靠港，进购物车全归到『未指定配送计划』」，
 * 「切换船舶」按钮也因为分组永远只有一组而消失，跨靠港拦截一并失效。
 *
 * 因此这里把「SQL 必须查这些列」与「前端依赖这些字段」绑成一条契约。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const CART_PAGE = 'src/pages/user/shopping-cart/index.vue'
const CART_MAPPER_XML
  = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/resources/mapper/ShoppingCartMapper.xml'
const CART_SERVICE
  = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/ShoppingCartServiceImpl.java'

describe('shopping cart vessel grouping contract', () => {
  it('selects the vessel ownership columns the frontend groups by', () => {
    const mapper = repoSource(CART_MAPPER_XML)
    const selectSql = mapper.slice(
      mapper.indexOf('<sql id="shoppingCartSql">'),
      mapper.indexOf('</sql>'),
    )
    // 少任何一列，分组与结算拦截都会静默失效（字段变 null，接口仍 200）
    for (const column of ['`vessel_id`', '`vessel_call_id`', '`purchase_scene`']) {
      expect(selectSql).toContain(column)
    }
  })

  it('declares the same columns on the entity', () => {
    const entity = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/entity/ShoppingCart.java',
    )
    for (const field of ['vesselId', 'vesselCallId', 'purchaseScene']) {
      expect(entity).toContain(field)
    }
  })

  it('groups cart rows by vesselCallId in the cart page', () => {
    const cart = source(CART_PAGE)
    // 分组键与 SQL 查询的列必须同名，改名时两处必须一起动
    expect(cart).toContain('row.vesselCallId')
    expect(cart).toContain('cartGroups')
  })

  it('distinguishes vessel-without-active-call rows from unattributed rows', () => {
    const cart = source(CART_PAGE)
    // 靠港结束（ETD 过点/已完成）后快照只带船不带靠港：行 vesselId 有值、
    // vesselCallId 为空。这与「从未归属过船舶」的行同组（分组键都是 ''），
    // 但前者是设计内回落（配送改走常规方式），不能同样显示「未指定配送计划」
    // 让用户误以为归属又丢了（2026-10-04 实测：靠港 11:30 过点后重加购恒显未指定）
    expect(cart).toContain('row.vesselId')
    expect(cart).toContain('cart.noActiveCall')
  })

  it('shows the vessel call status without requiring cart rows', () => {
    const cart = source(CART_PAGE)
    // 状态行挂在列表之外：空购物车也能看到靠港状态并直达申报入口；
    // 加购才暴露「没有靠港计划」的旧路径已由该行取代（2026-10-04）
    expect(cart).toContain('showShipStatusBar')
    expect(cart).toContain('capabilityStore.shipSupplyEnabled')
    expect(cart).toContain('!!shipContextStore.vesselId')
    // 申报成功（chooseCall → emit change）后必须刷新列表，
    // 否则服务端顺延过的行仍按旧分组渲染
    expect(cart).toContain('@change="getCartPage"')
  })

  it('loads the ship context before snapshotting it onto a new cart row', () => {
    const api = source('src/api/order/shoppingCart.ts')
    // 归属快照由 shipContextStore 写入，而它不持久化、冷启动为空。
    // 不先装载就写信，行落库即 vessel_call_id=NULL，
    // 购物车一律显示「未指定配送计划」，跨靠港拦截随之失效
    // （2026-09-29 实测：09-27 那行有归属，09-28 两行均为 NULL）。
    expect(api).toContain('ensureShipContextLoaded')
    expect(api).toContain('await withShipContext()')

    // 两条写入路径都要覆盖：单品加购与批量加购
    const addFn = api.slice(api.indexOf('function addShoppingCart'))
    expect(addFn).toContain('withShipContext')
    const batchFn = api.slice(api.indexOf('function batchAddShoppingCart'))
    expect(batchFn).toContain('withShipContext')
  })

  it('keeps the ship context load idempotent per login session', () => {
    const loader = source('src/composables/useShipContextLoad.ts')
    // 「无船舶 / 纯零售 / 服务异常」也算已尝试：不加标记会导致每次加购白打接口。
    // 登出必须复位，否则换账号登录后装载不回来。
    expect(loader).toContain('resolved')
    expect(loader).toContain('resolved = true')
    expect(loader).toContain('resolved = false')
  })

  it('does not let the cart list wait on the ship context request', () => {
    const cart = source(CART_PAGE)
    // 列表与上下文并行：分组是 computed，任一侧就绪都会重算，
    // 串行 await 会让弱网下的购物车多等一个请求才出内容
    const onShow = cart.slice(cart.indexOf('onShow(async'), cart.indexOf('</script>'))
    expect(onShow).toContain('void loadShipContext()')
  })

  it('blocks cross-port-call checkout using the same field', () => {
    const confirm = source('src/sub-pages/order/order-confirm/index.vue')
    expect(confirm).toContain('item.vesselCallId')
  })

  it('normalizes the null list response for an empty cart', () => {
    const cart = source(CART_PAGE)
    // 空购物车时后端返回 data:null；直接赋给 cartList 会让分组等
    // 依赖数组语义的代码面对 null
    expect(cart).toContain('state.cartList = response || []')
  })

  it('tolerates cart rows whose SKU is off-shelf or removed', () => {
    const cart = source(CART_PAGE)
    const handleEdit = cart.slice(
      cart.indexOf('function handleEdit'),
      cart.indexOf('function checkedAll'),
    )
    // 下架行的 goodsSku 为空，编辑模式切换时必须判空，否则整页报错
    expect(handleEdit).toContain('!val.goodsSku')
    expect(handleEdit).toContain('val.goodsSku && val.goodsSku.stock > 0')
    // 模板据 goodsSku 为空渲染「下架」，与后端返回 null 的语义配套
    expect(cart).toContain('cart.outOfStockShort')
    expect(cart).toContain('cart.delisted')
  })
})

describe('shopping cart off-shelf tolerance contract', () => {
  it('does not fail the whole cart page when every SKU is off-shelf', () => {
    const service = repoSource(CART_SERVICE)
    // 商品服务查不到 SKU 属正常业务状态，不能整体抛错，
    // 否则整车商品都已下架的用户连清空购物车的入口都进不去
    expect(service).not.toContain('query goods sku list fail!')
  })
})

/**
 * 购物车分组头「说人话」契约守门。
 *
 * 背景（真实困惑，2026-10-24）：靠港结束后滞留行的分组头显示
 * 「其他靠港计划 · 413825」——裸靠港 ID 尾号对用户零信息；且当前无靠港时
 * 仍出红字「结算前请切换船舶」，既无目标可切、结算也不会被拦（拦截只在
 * 当前有靠港时才比对），是假警告。用户截屏来问「413825 是什么」。
 *
 * 这里把「标签用回填的展示快照」「行动只在真能做到时出现」「已结束靠港
 * 给转入入口」绑成契约，防止分组文案再次退化成机器 ID。
 */
describe('shopping cart stale-call group contract', () => {
  it('renders group labels from the backfilled call snapshot, never from a raw call id', () => {
    const cart = source(CART_PAGE)
    // 标签必须消费后端回填的展示快照字段（港口/船名/是否已结束），
    // 禁止再拿 vesselCallId 切尾号当文案
    expect(cart).toContain('vesselCallPortName')
    expect(cart).toContain('vesselCallOrderable')
    expect(cart).not.toContain('key.slice(-6)')
  })

  it('keeps the stale-call snapshot flowing from the server list', () => {
    // 实体瞬态字段 → 服务端按靠港批量回填 → 前端消费，三处缺一即静默退化
    const entity = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/entity/ShoppingCart.java',
    )
    for (const field of ['vesselCallPortName', 'vesselCallVesselName', 'vesselCallOrderable']) {
      expect(entity).toContain(field)
    }
    const service = repoSource(CART_SERVICE)
    // 展示快照而非可下单查询：已结束的靠港也要报得出港口
    expect(service).toContain('getVesselCallSnapshot')
  })

  it('offers the transfer entry only when a target call exists, wired to the transfer API', () => {
    const cart = source(CART_PAGE)
    // 行动判定与转移调用必须成对出现：给了「转入本次靠港」就要真调转入接口
    expect(cart).toContain('transferCartRowsToCall')
    expect(cart).toContain('transferGroup')
    const action = functionSource(cart, 'groupAction')
    // 行动以当前靠港为目标：当前无靠港时不得出现任何行动（无目标可切/可转）
    expect(action).toContain('vesselCallId')
  })

  it('transfers rows through the dedicated endpoint that merges on unique-key collision', () => {
    const api = source('src/api/order/shoppingCart.ts')
    expect(api).toContain('/mall-order/app/shopping-cart/transfer-call')
    // 唯一键 uk_shopping_cart_active_row 是生成列：合并必须在服务端完成
    const service = repoSource(CART_SERVICE)
    expect(service).toContain('migrateRowToVesselCall')
    expect(service).toContain('DuplicateKeyException')
  })
})

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

/**
 * 购物车「限时折扣价」契约守门。
 *
 * 背景（真实缺陷）：折扣会场标「8 折 ¥23.92」，购物车行价与合计却仍是 ¥29.90。
 * 购物车拿到的是 SKU 当前售价，活动价只在会场由活动配置渲染 —— 若不接入
 * 批量折扣价接口，用户从会场加购后看到的仍是原价，与「结算按活动价」的预期对不上。
 *
 * 这里把「整页一次批量拉价」「行价/划线价/合计同源」「新人价优先」绑成契约，
 * 避免后续重构再让购物车与折扣价断线。
 */
describe('shopping cart limited-discount price contract', () => {
  it('loads discount prices in one batch per cart list load', () => {
    const cart = source(CART_PAGE)
    // 共享层：批量接口 + 按 SKU 精确解析 + 成交单价兜底
    expect(cart).toContain('getGoodsDiscountPrices')
    expect(cart).toContain('resolveDiscountPriceDisplay')
    expect(cart).toContain('discountUnitPrice')
    // 整页只允许一处调用：逐行请求会把购物车首屏放大成 N 个请求
    expect(cart.match(/getGoodsDiscountPrices\(/g) || []).toHaveLength(1)

    const loader = functionSource(cart, 'loadDiscountPrices')
    expect(loader).toContain('getGoodsDiscountPrices(skuIds)')
    // 同一 SKU 可能有多行，查询前必须去重
    expect(loader).toContain('new Set')
    // 失败静默回落原价，不能冒泡到购物车列表与结算主流程
    expect(loader).toContain('catch')
    expect(loader).toContain('discountDisplays.value = {}')

    // 与新人价同一时机：列表加载/刷新/增删行都会走 getCartPage，
    // 两个价格增强请求都在这里触发且不阻塞首屏
    const listLoader = functionSource(cart, 'getCartPage')
    expect(listLoader).toContain('void loadNewbiePrices()')
    expect(listLoader).toContain('void loadDiscountPrices()')
  })

  it('renders the discount price as main price with badge and strike-through base price', () => {
    const cart = source(CART_PAGE)
    // 行主价必须走与合计同源的 cartUnitPrice，不能各写一遍三目
    expect(cart).toContain(':text="cartUnitPrice(goods)"')
    // 命中折扣时展示力度角标，文案由共享层 formatDiscountValue 口径生成
    expect(cart).toContain('discountDisplayOf(goods).showDiscountPrice')
    expect(cart).toContain('discountDisplayOf(goods).badgeText')
    expect(cart).toContain('discount-badge')
    // 未命中/接口失败回落共享空展示（空表即全部按原价）
    expect(cart).toContain('emptyDiscountDisplay()')

    // 划线价取折扣基准价（display.originalPrice），与折后价同一份判定结果
    const strike = functionSource(cart, 'strikePriceOf')
    expect(strike).toContain('discountDisplayOf(row)')
    expect(strike).toContain('discount.originalPrice')
  })

  it('keeps newbie price ahead of limited discount and totals on the same unit price', () => {
    const cart = source(CART_PAGE)
    // 优先级：新人价 > 限时折扣 > 售价（与后端 OrderPriceComputeService 一致）
    const unit = functionSource(cart, 'cartUnitPrice')
    const newbieAt = unit.indexOf('showNewbiePrice')
    const discountAt = unit.indexOf('discountUnitPrice')
    expect(newbieAt).toBeGreaterThan(-1)
    expect(discountAt).toBeGreaterThan(newbieAt)
    // 无新人价时才落到折扣成交价，未命中由 discountUnitPrice 回落售价
    expect(unit).toContain('discountUnitPrice(fallback')

    // 合计与行价同源：不再直接读 salesPrice，否则行价折后、合计原价
    const compute = functionSource(cart, 'computePrice')
    expect(compute).toContain('cartUnitPrice(val)')
    expect(compute).not.toContain('goodsSku.salesPrice')
    expect(compute).not.toContain('effectiveUnitPrice(')
  })
})
