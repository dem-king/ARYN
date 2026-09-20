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
    const workbenchSource = source('src/components/ship-workbench/index.vue')
    const orderSource = source('src/sub-pages/order/order-detail/index.vue')
    const pagesSource = source('src/pages.json')

    // 商品统一（2026-09-20）：工作台不再有独立的「船供采购」商品目录入口，
    // 统一走商品分类；ship-supply 页面保留给共享购物车选货模式使用。
    expect(workbenchSource).not.toContain("ship-supply/index?scene=2")
    expect(workbenchSource).toContain("'/pages/product/category/index'")
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

  it('keeps the home ship workbench below the fixed navbar placeholder', () => {
    const homeSource = source('src/pages/home/index.vue')
    const diySource = source('src/components/diy/index.vue')

    // diy-page 的导航栏是 fixed 定位，其占位只对内部内容生效，
    // 因此必须暴露「导航栏下方」插槽，且插槽位于导航栏之后、DIY 组件之前
    const navbarIndex = diySource.indexOf('<hr-navbar')
    const slotIndex = diySource.indexOf('<slot name="below-navbar" />')
    const componentsIndex = diySource.indexOf('class="diy-components"')
    expect(navbarIndex).toBeGreaterThan(-1)
    expect(slotIndex).toBeGreaterThan(navbarIndex)
    expect(componentsIndex).toBeGreaterThan(slotIndex)

    // 首页必须把船舶工作台放进该插槽，不能渲染成 diy-page 的兄弟节点，否则会被导航栏盖住
    expect(homeSource).toMatch(
      /<template #below-navbar>[\s\S]*<ShipWorkbench \/>[\s\S]*<\/template>/,
    )
    expect(homeSource).not.toMatch(/<ShipWorkbench \/>\s*\n\s*<diy-page/)
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
