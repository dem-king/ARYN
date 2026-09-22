import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 快捷加购前后端契约守门。
 *
 * 这类问题的典型漏网方式是：前端调用的路径与后端 @RequestMapping 漂移，
 * 或加购按钮没有阻止冒泡导致点加购跳进了商品详情——两者都不会被类型检查发现。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const CONTROLLER = 'aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/controller/app/AppGoodsSpuController.java'

describe('quick cart routing contract', () => {
  it('declares the quick-cart path and matches the backend mapping', () => {
    const api = source('src/api/product/spu.ts')
    // 用正则避免在普通字符串里写模板占位符（no-template-curly-in-string）
    expect(api).toMatch(/\/product\/app\/goodsspu\/quick-cart\/\$\{id\}/)

    const controller = repoSource(CONTROLLER)
    expect(controller).toContain('@RequestMapping("/app/goodsspu")')
    expect(controller).toContain('@GetMapping("/quick-cart/{id}")')
  })

  it('resolves quick-cart before the generic /{id} mapping cannot shadow it', () => {
    // /quick-cart/{id} 与 /{id} 同为 GET，Spring 按最长字面量优先匹配；
    // 这里固化「两段路径」的写法，避免被改成单段路径后与详情接口冲突。
    const api = source('src/api/product/spu.ts')
    expect(api).toMatch(/quick-cart\/\$\{id\}/)
    expect(api).not.toMatch(/goodsspu\/\$\{id\}\/quick-cart/)
  })
})

describe('quick cart interaction contract', () => {
  it('stops tap propagation so tapping add-to-cart does not open the detail page', () => {
    const button = source('src/components/quick-cart-button/index.vue')
    expect(button).toContain('@tap.stop="handleAdd"')
  })

  it('requires login before hitting the cart API', () => {
    const composable = source('src/composables/useQuickCart.ts')
    expect(composable).toContain('if (!authStore.isLoggedIn)')
    expect(composable).toContain('uni.navigateTo({ url: \'/pages/login/index\' })')
  })

  it('never reports success unless the cart API resolved', () => {
    const composable = source('src/composables/useQuickCart.ts')
    // 加购失败时把 added 置为 false，避免「提示已加入但购物车是空的」
    expect(composable).toContain('await addShoppingCart({ skuId: info.skuId, quantity })')
    expect(composable).toMatch(/catch \{[\s\S]*?added: false/)
  })

  it('opens the SKU popup for multi-spec goods instead of guessing a SKU', () => {
    const button = source('src/components/quick-cart-button/index.vue')
    expect(button).toContain('result.needChoose')
    expect(button).toContain('openSkuPopup(result.info)')
    expect(button).toContain('vk-data-goods-sku-popup')
  })

  it('mounts the SKU popup lazily so lists do not multiply popup nodes', () => {
    const button = source('src/components/quick-cart-button/index.vue')
    // 列表页每张卡片都渲染一份弹层会让小程序节点数随商品数线性增长
    expect(button).toContain('v-if="skuMounted"')
    expect(button).toContain('skuMounted.value = true')
  })

  it('exposes quick add on the product list and decoration goods surfaces', () => {
    // 商品列表与分类页共用 goods-list-panel，加购入口随之收敛到该面板
    expect(source('src/components/goods-list-panel/index.vue')).toContain('<quick-cart-button')
    expect(source('src/sub-pages/product/goods-list/index.vue')).toContain('<goods-list-panel')
    expect(source('src/components/diy/diy-goods/index.vue')).toContain('<quick-cart-button')
    expect(source('src/components/diy/diy-goods-group/index.vue')).toContain('<quick-cart-button')
    expect(source('src/components/diy/diy-goods-waterfall/index.vue')).toContain('<quick-cart-button')
  })
})

describe('ship supply seed contract', () => {
  it('seeds ship SKUs as enabled so they are purchasable', () => {
    // goods_sku.status 语义：0=启用、1=停用（管理端 ElSwitch active-value="0"）。
    // 结算/加购链路（selectSkuByIds）只取 status='0'，种子写成 '1' 会让全部船供商品报「库存不足」。
    for (const file of [
      'aryn-mall-java/db/boot/62ship_supply_seed_acceptance.sql',
      'aryn-mall-java/db/cloud/62ship_supply_seed_acceptance.sql',
    ]) {
      const seed = repoSource(file)
      expect(seed).toMatch(/'1590229800633634816', 'seed', '0',/)
      expect(seed).not.toMatch(/'1590229800633634816', 'seed', '1',/)
    }
  })
})
