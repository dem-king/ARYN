import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('mobile review contracts', () => {
  it('does not log route or user details', () => {
    const routerSource = source('src/router/index.ts')
    expect(routerSource).not.toMatch(/console\.(?:error|log|warn)/)
  })

  it('enables WeChat request-domain validation', () => {
    expect(source('manifest.config.ts')).toContain('urlCheck: true')
  })

  it('applies token and Boot URL validation at integration boundaries', () => {
    const authSource = source('src/store/authStore.ts')
    const requestSource = source('src/api/core/instance.ts')

    expect(authSource.match(/requireTokenValue\(response\)/g)).toHaveLength(3)
    expect(requestSource).toContain('rewriteBootUrl(method.url, openBoot)')
    expect(requestSource).toContain('delete method.config.headers.skipToken')
    expect(requestSource).not.toContain('VITE_OPEN_BOOT === \'true\'')
  })

  it('keeps delivery authentication and service routing isolated', () => {
    const deliverySource = source('src/api/delivery.ts')
    const requestSource = source('src/api/core/instance.ts')

    expect(deliverySource).toContain("const DELIVERY_API_BASE = '/mall-order/app/delivery'")
    expect(deliverySource).toContain("'/auth/token/delivery-login'")
    expect(deliverySource).toContain('/mall-order/app/order/${orderId}/delivery-progress')
    expect(requestSource).toContain("Local.get('deliveryToken')")
    expect(source('src/router/index.ts')).toContain("const DELIVERY_ROUTE_PREFIX = '/pages/delivery/'")
    expect(source('src/api/upms/file.ts')).toContain("Local.remove('deliveryToken')")
  })

  it('keeps ship workbench navigation aligned with declared pages', () => {
    const workbenchSource = source('src/components/diy/diy-ship-workbench/index.vue')
    const orderSource = source('src/sub-pages/order/order-detail/index.vue')
    const pagesSource = source('src/pages.json')

    // 商品统一（2026-09-20）：工作台不再有独立的「船供采购」商品目录入口，
    // 统一走商品分类；ship-supply 页面保留给共享购物车选货模式使用。
    // 首页降级为单行状态条（2026-09-21）后连「去选购」也一并移除：
    // 底部 TabBar 的「分类」已是同一入口，首屏不重复堆叠主路径按钮。
    expect(workbenchSource).not.toContain("ship-supply/index?scene=2")
    expect(workbenchSource).not.toContain("'/pages/product/category/index'")
    expect(workbenchSource).toContain("'/sub-pages/product/frequent/index'")
    expect(orderSource).toContain("'/pages/user/shopping-cart/index'")
    expect(workbenchSource).not.toContain("'/pages/product/index'")
    expect(orderSource).not.toContain("'/pages/cart/index'")
    expect(pagesSource).toContain('"path": "product/ship-supply/index"')
    expect(pagesSource).toContain('"path": "product/frequent/index"')
  })

  it('does not persist vessel context across sessions', () => {
    expect(source('src/store/persist.ts')).toContain("'shipContext'")
    expect(source('src/store/authStore.ts')).toContain('useShipContextStore().reset()')
  })

  it('renders the ship workbench as a decoration component, not a hardcoded slot', () => {
    const homeSource = source('src/pages/home/index.vue')
    const diySource = source('src/components/diy/index.vue')
    const registrySource = source('src/components/diy/registry.ts')

    // 2026-09-21：原先固定挂在 diy-page 的 below-navbar 插槽，运营无法调整位置或隐藏。
    // 改为装修组件后必须走 diy 渲染链路，首页不再持有任何船舶逻辑。
    expect(diySource).not.toContain('below-navbar')
    expect(homeSource).not.toContain('ShipWorkbench')
    expect(homeSource).not.toContain('below-navbar')
    expect(diySource).toContain("item.type === 'ship-workbench'")
    expect(registrySource).toContain("'ship-workbench': DiyShipWorkbench")
  })

  it('keeps the cart header in flow and the checkout bar above the tab bar', () => {
    const cartSource = source('src/pages/user/shopping-cart/index.vue')

    // 收货地址栏曾用 position: fixed 却不占位，把下面的共享购物车入口盖住；
    // 保持普通流内布局，地址栏就不会遮挡任何内容。
    const headerRule = cartSource.match(/\.cart-header \{[\s\S]*?\n\}/)?.[0] ?? ''
    expect(headerRule).not.toContain('position: fixed')

    // 结算条固定在 TabBar 之上：H5 的 TabBar 是一层 fixed 遮盖（z-index 998），
    // 用 --window-bottom 定位才能露出「去结算」按钮；小程序该变量为 0。
    const footerRule = cartSource.match(/\.cart-footer \{[\s\S]*?\.cart-footer-warp/)?.[0] ?? ''
    expect(footerRule).toContain('bottom: var(--window-bottom')

    // 结算条悬浮在内容之上，必须有等高占位，否则会遮住推荐商品最后一行。
    expect(cartSource).toContain('class="cart-footer-holder"')
    expect(cartSource).toMatch(/\.cart-footer-holder \{[\s\S]*?height: calc\(/)
  })

  it('keeps internal IDs out of the delivery eligibility contract', () => {
    const deliverySource = source('src/api/delivery.ts')

    // 资格查询响应类型只保留展示字段，禁止出现 sysUserId/staffId 等内部 ID
    const eligibilityMatch = deliverySource.match(
      /export interface DeliveryEligibility \{[\s\S]*?\n\}/,
    )
    expect(eligibilityMatch).toBeTruthy()
    const eligibilitySource = eligibilityMatch?.[0] ?? ''
    expect(eligibilitySource).toContain('eligible')
    expect(eligibilitySource).toContain('status')
    expect(eligibilitySource).not.toMatch(/\bsysUserId\b/)
    expect(eligibilitySource).not.toMatch(/\bstaffId\b/)
    expect(eligibilitySource).not.toMatch(/\bphone\b/)
  })

  it('removes named event handlers during the page unload lifecycle', () => {
    const confirmSource = source('src/sub-pages/order/order-confirm/index.vue')
    const listSource = source('src/sub-pages/order/order-list/index.vue')

    expect(confirmSource).toContain('onUnload(() =>')
    expect(confirmSource).toContain(
      'uni.$off(\'update:selectedAddress\', handleSelectedAddressUpdate)',
    )
    expect(confirmSource).not.toContain('uni.$once(\'beforeUnload\'')

    expect(listSource).toContain('onUnload(() =>')
    expect(listSource).toContain('uni.$off(\'refresh\', handleRefresh)')
    expect(listSource).not.toContain('uni.$once(\'beforeUnload\'')
  })
})

describe('home ship workbench visibility model (方案 B)', () => {
  it('降级为单行状态条，不再占据首页第一屏的卡片位置', () => {
    const workbenchSource = source('src/components/diy/diy-ship-workbench/index.vue')

    // 卡片曾用「船名 + 靠港 + 两个大按钮」占满首屏；状态条固定单行高度
    expect(workbenchSource).toContain('height: 88rpx')
    // 卡片上的两个大按钮（去选购 / 常购）不再存在：「去选购」由底部 TabBar 承担，
    // 「常购」改为状态条右侧的轻量文字入口，状态条内不应再出现 button 元素
    expect(workbenchSource).not.toContain('goPersonal')
    expect(workbenchSource).not.toContain('<button')
  })

  it('未登录、无船、异常一律不渲染，首页第一屏不出现引导或告警文案', () => {
    const workbenchSource = source('src/components/diy/diy-ship-workbench/index.vue')

    // 未登录与 error 直接落到 visible=false；noVessel 由 hasVessel 兜住
    expect(workbenchSource).toContain('if (!authStore.isLoggedIn)')
    expect(workbenchSource).toContain("status.value !== 'error'")
    // 旧的 guest / noVessel 引导文案必须彻底移除
    expect(workbenchSource).not.toContain('登录后可绑定船舶')
    expect(workbenchSource).not.toContain('暂未关联船舶')
    expect(workbenchSource).not.toContain('船舶服务暂时不可用')
  })

  it('纯零售租户不渲染船舶状态条', () => {
    const workbenchSource = source('src/components/diy/diy-ship-workbench/index.vue')

    expect(workbenchSource).toContain('tenantCapabilityStore.ensureLoaded()')
    expect(workbenchSource).toContain('tenantCapabilityStore.shipSupplyEnabled')
    // 不具备船供能力时不应发起船舶请求
    expect(workbenchSource).toContain('if (!tenantCapabilityStore.shipSupplyEnabled)')
  })

  it('把未绑定用户的入口收进「我的」，避免自助出路丢失', () => {
    const userCenter = source('src/pages/user/user-center/index.vue')

    expect(userCenter).toContain('/sub-pages/vessel/bind/index')
    expect(userCenter).toContain('vesselEntranceVisible')
    expect(userCenter).toContain('tenantCapabilityStore.shipSupplyEnabled')
  })
})
