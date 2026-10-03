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
    expect(cart).toContain('goods.goodsSku ? \'无货\' : \'下架\'')
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
