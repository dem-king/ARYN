import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 加购目的地（个人购物车 / 共享购物车）接线守门。
 *
 * 这类回归的漏网方式：
 * - 新加购入口直调 addShoppingCart，商品被默认写进个人购物车，而个人购物车
 *   与共享购物车互不相通，用户之后无法转入——类型检查与运行时都不会报错；
 * - 宿主组件接了 submitCartAdd 却忘挂弹层，加购按钮在等待选择时永久 pending；
 * - 宿主持有 `pendingChoice` 自己挂弹层（曾经如此）：quick-cart-button 这类
 *   宿主随商品卡重复渲染，同屏出现 N 份弹层——遮罩叠成纯黑、N 份 slide-up
 *   动画不同步造成抖动、弹层 DOM 落在商品卡内部导致按钮 tap 冒泡跳商详。
 *   弹层因此固定在根组件挂载，宿主一律不许再挂。
 * 三者都只能靠源码契约兜住。
 */
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 走统一目的地入口的加购宿主。
 * 新增 C 端加购入口时应加入此清单； ship-supply 选货页例外——
 * 它在共享模式下带明确的 sharedCartId 上下文，直接写共享车，个人模式下明确写个人车。
 */
const DESTINATION_HOSTS = [
  'src/composables/useQuickCart.ts',
  'src/components/quick-cart-button/index.vue',
  'src/components/goods-detail-sheet/index.vue',
  'src/sub-pages/product/goods-detail/index.vue',
  'src/sub-pages/product/frequent/index.vue',
]

/** 唯一允许挂载目的地弹层的位置（全局一份，随页面根组件渲染） */
const SHEET_MOUNT = 'src/App.ku.vue'

describe('cart destination contract', () => {
  it('routes every browsing add entry through the unified destination funnel', () => {
    for (const path of DESTINATION_HOSTS) {
      const code = source(path)
      expect(code.includes('submitCartAdd'), `${path} 应走统一加购目的地入口`).toBe(true)
    }
  })

  it('keeps direct personal-cart calls out of the funnel hosts', () => {
    for (const path of DESTINATION_HOSTS) {
      const code = source(path)
      expect(code.includes('addShoppingCart } from \'@/api/order/shoppingCart\''), `${path} 不得直调个人购物车接口，应经 useCartDestination`).toBe(false)
    }
  })

  it('mounts the destination sheet exactly once, in the page root component', () => {
    const root = source(SHEET_MOUNT)
    expect(root).toContain('shared-cart-destination-sheet')
    expect(root).toContain('v-if="!!pendingChoice"')

    for (const path of DESTINATION_HOSTS) {
      const code = source(path)
      expect(code.includes('shared-cart-destination-sheet'), `${path} 不得自带弹层：宿主随商品卡重复渲染，会让同屏出现 N 份弹层`).toBe(false)
      expect(code.includes('pendingChoice'), `${path} 不得引用 pendingChoice（仅根组件负责挂载弹层）`).toBe(false)
    }
  })

  it('asks for destination only when an editable collecting cart exists', () => {
    const store = source('src/store/sharedCartStore.ts')
    // 未登录 / 租户未开通船供时不发请求，普通加购零打扰
    expect(store).toContain('isLoggedIn')
    expect(store).toContain('shipSupplyEnabled')
    expect(store).toContain('pickActiveCart')
    // 共享车明细变化后作废缓存，避免弹层展示过期单据
    expect(store).toContain('invalidate()')

    const utils = source('src/utils/shared-cart.ts')
    expect(utils).toContain('viewerCanEdit === false')
  })

  it('keeps the shared item payload aligned with the backend contract', () => {
    const composable = source('src/composables/useCartDestination.ts')
    // 共享车明细数量字段是 requestedQuantity，与个人购物车的 quantity 不同名
    expect(composable).toContain('requestedQuantity')
    expect(composable).toContain('addSharedCartItem')

    const api = source('src/api/order/sharedCart.ts')
    expect(api).toContain('export function addSharedCartItem')
  })

  it('keeps the destination sheet above the sku popup and wired to all three answers', () => {
    const sheet = source('src/components/shared-cart-destination-sheet/index.vue')
    // 必须压住 SKU 规格弹层（990）与商详快捷面板（1000）
    expect(sheet).toContain(':z-index="1020"')
    // 关闭弹层 = 放弃本次加购，不能静默写进个人购物车
    expect(sheet).toContain('answerDestination(\'abort\')')
    // 两个去向都必须回传最终数量（采购量大时数量在弹层里改，不是各调用方各算一遍）
    expect(sheet).toMatch(/confirm\('shared'\)/)
    expect(sheet).toMatch(/confirm\('personal'\)/)
    expect(sheet).toContain('normalizeQuantity')
  })

  it('lets the buyer type a large quantity instead of tapping add repeatedly', () => {
    // 这一条是这个需求的核心回归点：船供采购一次几十上百（青菜几十斤），
    // 若只是「点一次加 MOQ 件」，用户必须反复点击凑数量，共享车还会堆重复行。
    const sheet = source('src/components/shared-cart-destination-sheet/index.vue')
    // 必须能直接键盘输入，而不只是加减按钮
    expect(sheet).toMatch(/<input[\s\S]{0,400}@input="onQuantityInput"/)
    // 输入值与提交值都要过归一化（MOQ/步长/库存），与后端 validateQuantityRules 同口径
    expect(sheet).toContain('quantity-input')

    const quickCart = source('src/composables/useQuickCart.ts')
    // 单规格快捷加购把数量规则交给确认弹层（否则弹层没有数量输入）
    expect(quickCart).toContain('resolveQuantityRule')
    expect(quickCart).toContain('{ rule:')
  })

  it('aborts the pending confirmation when the page hosting it is left', () => {
    // pendingChoice 跨页面共享、弹层随根组件注入每一页：弹层若不自己在页面
    // 离开时关掉，首页打开的弹层切 Tab 后会在新页面原样出现（弹层跟着人走）。
    // onHide 覆盖切 Tab / 前进跳转，onUnload 覆盖返回手势 / redirectTo。
    const sheet = source('src/components/shared-cart-destination-sheet/index.vue')
    expect(sheet).toMatch(/onHide\(/)
    expect(sheet).toMatch(/onUnload\(/)
    expect(sheet).toContain("pendingChoice.value?.id === requestAtMount")
  })

  it('settles every confirmation so no add button is left pending forever', () => {
    // 历史缺陷：超时兜底写成 `pendingChoice.value === request`。ref 存入对象后
    // 读出来是响应式代理，引用比对恒为 false，兜底从未生效。改为按请求号比对——
    // 这里只守「按 id 比对 + 不再有引用比对」这个行为，不绑定具体变量名。
    const composable = source('src/composables/useCartDestination.ts')
    expect(composable).toMatch(/pendingChoice\.value\?\.id === id/)
    expect(composable).not.toMatch(/pendingChoice\.value === \w+/)
    // 新确认顶掉旧确认时，旧的那笔必须也落定为 abort，否则它的 await 会一直悬着
    expect(composable).toContain('pendingChoice.value?.answer(\'abort\')')
  })

  it('leaves the ship-supply picker on its explicit dual-mode path', () => {
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    expect(shipSupply).toContain('isSharedMode')
    expect(shipSupply).toContain('addSharedCartItem')
    // 选货页上下文明确，不需要也不应该再弹目的地选择
    expect(shipSupply.includes('submitCartAdd')).toBe(false)
  })
})
