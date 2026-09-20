import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 共享购物车前后端契约守门。
 *
 * 这类问题的典型漏网方式是：页面写好了但忘了在 pages.json 注册，
 * 或前端调用的路径与后端 @RequestMapping 漂移——两者都不会被类型检查发现。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const API_BASE = '/mall-order/app/shared-cart'

describe('shared cart routing contract', () => {
  it('registers both shared cart pages in pages.json', () => {
    const pages = source('src/pages.json')
    expect(pages).toContain('"path": "order/shared-cart/list"')
    expect(pages).toContain('"name": "shared-cart-list"')
    expect(pages).toContain('"path": "order/shared-cart/detail"')
    expect(pages).toContain('"name": "shared-cart-detail"')
  })

  it('keeps the list page navigating to the detail page', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('/sub-pages/order/shared-cart/detail?id=')
  })

  it('keeps the detail page entering ship supply in shared-cart picking mode', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).toContain('sharedCartId=')
    expect(detail).toContain('/sub-pages/product/ship-supply/index')
  })

  it('keeps ship supply supporting shared-cart picking mode', () => {
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    expect(shipSupply).toContain('sharedCartId')
    expect(shipSupply).toContain('addSharedCartItem')
    // 共享车模式下不得再写入个人购物车，否则同一件商品会进两处
    expect(shipSupply).toContain('isSharedMode')
  })

  it('exposes every shared cart page from an existing entry point', () => {
    const cart = source('src/pages/user/shopping-cart/index.vue')
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    expect(cart).toContain('/sub-pages/order/shared-cart/list')
    expect(shipSupply).toContain('/sub-pages/order/shared-cart/list')
  })
})

describe('shared cart API contract', () => {
  it('declares the C-end base path and the my-carts endpoint', () => {
    const api = source('src/api/order/sharedCart.ts')
    expect(api).toContain(`const BASE = '${API_BASE}'`)
    expect(api).toContain('`${BASE}/my`')
  })

  it('matches the backend controller base path and my-carts mapping', () => {
    const controller = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppSharedCartController.java',
    )
    expect(controller).toContain('@RequestMapping("/app/shared-cart")')
    expect(controller).toContain('@GetMapping("/my")')
    // 详情必须返回带 viewer* 权限标记的 VO，前端不自行推断权限
    expect(controller).toContain('Result<SharedCartVO>')
  })

  it('declares the same mutating endpoints as the backend controller', () => {
    const api = source('src/api/order/sharedCart.ts')
    const controller = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppSharedCartController.java',
    )
    for (const mapping of [
      '@PostMapping("/{id}/members")',
      '@PostMapping("/{id}/items")',
      '@PutMapping("/{id}/items/{itemId}")',
      '@DeleteMapping("/{id}/items/{itemId}")',
      '@PostMapping("/{id}/confirm")',
      '@PostMapping("/{id}/close")',
    ]) {
      expect(controller).toContain(mapping)
    }
    expect(api).toContain('`${BASE}/${id}/members`')
    expect(api).toContain('`${BASE}/${id}/items`')
    expect(api).toContain('`${BASE}/${id}/items/${itemId}`')
    expect(api).toContain('`${BASE}/${id}/confirm`')
    expect(api).toContain('`${BASE}/${id}/close`')
  })

  it('不再由前端计算有效期，收集截止统一由服务端按创建时刻 +24h 决定', () => {
    // 客户端时钟不可信，且历史上「不限期」会导致购物车永不过期；
    // 有效期改由 SharedCartServiceImpl.create 写入 now + 24h。
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).not.toContain('payload.expiresAt')
    expect(list).not.toContain('windowHours')
    expect(list).toContain('24 小时')
  })

  it('复用已有购物车时提示用户，而非谎称新建', () => {
    // 同船已有进行中的采购时服务端返回 adoptedExisting=true，
    // 前端必须区分「新建」与「并入已有」，否则用户会误以为重复创建成功。
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('adoptedExisting')
  })
})

describe('shared cart permission contract', () => {
  it('drives detail page actions from backend viewer flags', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).toContain('viewerCanEdit')
    expect(detail).toContain('viewerCanConfirm')
    expect(detail).toContain('viewerIsOwner')
  })

  it('never uses a shadowed vue api name for page state', () => {
    // `readonly` 与 vue 的 readonly() 冲突，模板中会解析成函数导致条件恒真
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).not.toMatch(/const readonly\s*=/)
  })

  it('keeps delivery way labels single-sourced', () => {
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    for (const file of [shipSupply, detail]) {
      expect(file).not.toMatch(/deliveryWay === '1'[\s\S]{0,80}\?[\s\S]{0,40}普通快递/)
    }
  })
})
