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

/** 去掉注释，避免「注释里提到某某」被当成真实调用（契约只认代码） */
function stripComments(code: string) {
  return code
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '')
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
    // 登录拦截走统一守卫（记录 redirectPath，登录后回原页），不再各处裸跳登录页
    expect(composable).toContain('useRequireLogin')
    expect(composable).toContain('goLogin()')
  })

  it('never reports success unless the cart write resolved', () => {
    const composable = source('src/composables/useQuickCart.ts')
    // 加购（个人/共享）经统一入口 submitCartAdd，写入成功才把 added 置为 true：
    // 失败走 catch、用户关闭目的地弹层走 abort，两条路径都必须 added: false
    expect(composable).toContain('await submitCartAdd(')
    expect(composable).toMatch(/dest === 'abort'[\s\S]*?added: false/)
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

  it('exposes quick add on the recommendation stream cards with a sold-out guard', () => {
    // 购物车「为你推荐」、商详「你可能还会喜欢」、个人中心「猜你喜欢」、
    // 订单详情与支付结果推荐位共用 waterfall-goods，加购入口收在这一处。
    const waterfall = source('src/components/waterfall-goods/index.vue')
    // 整卡点击仍跳商详；加购按钮在卡片内部，冒泡由 quick-cart-button 根节点 stop
    expect(waterfall).toContain('@click="toDetail(item.id)"')
    // 守卫必须包住按钮本体（不能挂在别的元素上把按钮留在守卫外），尺寸/回调与列表同源
    const guardedBlock
      = waterfall.match(/<view v-if="item\.stock > 0"[^>]*>([\s\S]*?)<\/view>/)?.[1] ?? ''
    expect(guardedBlock).toContain('<quick-cart-button')
    expect(guardedBlock).toContain('size="48rpx"')
    expect(guardedBlock).toContain('@added="handleAdded"')
    // 加购成功要真的 emit 出去：只声明 emits + 模板接线、删掉 emit 调用时，
    // 购物车页永远收不到刷新信号，而类型检查与其余契约都发现不了。
    expect(waterfall).toContain("(e: 'added', spuId: string): void")
    expect(waterfall).toContain("emit('added', spuId)")
    // 目的地确认弹层由 App.ku.vue 全局单例挂载：随卡片重复渲染的组件不得自己再挂一份
    const code = stripComments(waterfall)
    expect(code).not.toContain('shared-cart-destination-sheet')
    expect(code).not.toContain('useCartDestination')
  })

  it('binds the recommendation sold-out guard to the backend stock column', () => {
    // 守卫依赖推荐接口下发 stock：后端若把 stock 从 goodsSpuSql / selectRecommendPage
    // 摘掉或换成精简 VO，前端会把所有商品判成售罄（按钮静默全消失），而前端测试仍然全绿。
    // 先剥注释：列名只出现在注释里不算数。
    const mapper = stripComments(repoSource(
      'aryn-mall-java/aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml',
    ))
    const spuSql = mapper.slice(mapper.indexOf('<sql id="goodsSpuSql">'), mapper.indexOf('</sql>'))
    expect(spuSql).toContain('goods_spu.stock')
    const recommendSelect = mapper.slice(mapper.indexOf('<select id="selectRecommendPage"'))
    const recommendBlock = recommendSelect.slice(0, recommendSelect.indexOf('</select>'))
    expect(recommendBlock).toContain('<include refid="goodsSpuSql"/>')
    // 出参实体也必须是带 stock 的 GoodsSpu：换成精简 VO 时 MyBatis 会把 stock 静默丢弃
    expect(recommendBlock).toContain(
      'resultType="com.aryn.cloud.product.api.entity.GoodsSpu"',
    )
    expect(repoSource(
      'aryn-mall-java/aryn-product/aryn-product-api/src/main/java/com/aryn/cloud/product/api/entity/GoodsSpu.java',
    )).toMatch(/private Integer stock;/)
    // 前端消费的是 /recommend 这条路由，路径漂移会让契约对不上号
    expect(repoSource(CONTROLLER)).toContain('@GetMapping("/recommend")')
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
