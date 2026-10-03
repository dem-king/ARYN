import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 购物车 tabBar 角标接线守门。
 *
 * 原始缺陷：加购成功只有一闪而过的 toast，底部「购物车」tab 永远无角标，
 * 用户切到购物车页之前对车里已有商品毫无感知。角标接线全是运行时不报错的
 * 纯源码约定，只能靠契约测试兜住：
 * - 根组件忘了挂 useTabBarBadge → watch 不存在，角标永远不动；
 * - 各页面自己调 uni.setTabBarBadge → 绕开共享 store，口径漂移且互相覆盖；
 * - tab 下标写死数字 → pages.config.ts 调整 tab 顺序后角标打到别的 tab；
 * - setTabBarBadge 失败被无视（H5 冷启动 tabBar 未就绪）→ 数量到位也看不到角标。
 */
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('tabBar 购物车角标契约', () => {
  it('角标同步只在根组件挂载一次', () => {
    const root = source('src/App.ku.vue')
    expect(root).toContain('useTabBarBadge()')
  })

  it('watch 的是共享 store 的 cartCount，而不是各页面自行打角标', () => {
    const composable = source('src/composables/useTabBarBadge.ts')
    // 数量写入口（登录/分类页/购物车页/统一加购入口/下单后）全部落在
    // shoppingCartStore.cartCount，角标必须 watch 同一份状态
    expect(composable).toMatch(/watch\(\(\) => shoppingCartStore\.cartCount/)
    expect(composable).toContain('immediate: true')
  })

  it('tab 下标由 pages.json 配置推导，禁止写死数字', () => {
    const composable = source('src/composables/useTabBarBadge.ts')
    expect(composable).toContain('resolveCartTabIndex(tabBar?.list ?? [])')
    // uni API 的 index 参数一律来自配置推导
    expect(composable).not.toMatch(/index:\s*\d/)
  })

  it('setTabBarBadge 失败（tabBar 未就绪）必须有界重试，removeTabBarBadge 静默吞错', () => {
    const composable = source('src/composables/useTabBarBadge.ts')
    expect(composable).toContain('uni.setTabBarBadge')
    expect(composable).toContain('uni.removeTabBarBadge')
    expect(composable).toContain('BADGE_MAX_RETRIES')
    expect(composable).toContain('BADGE_RETRY_DELAY_MS')
  })

  it('removeTabBarBadge 必须显式传 fail 回调吞错（非 tabBar 页挂载根组件必失败）', () => {
    const composable = source('src/composables/useTabBarBadge.ts')
    // 小程序端 API 失败不传 fail 回调会走 App.onError 变成未捕获错误：
    // 非 tabBar 页（配送端各页、商详页等）cartCount 为 0 时每次挂载都触发
    expect(composable).toMatch(/uni\.removeTabBarBadge\(\{[\s\S]*?fail:/)
  })

  it('角标文案与 tab 下标口径收敛在 utils/cart-badge（与分类页悬浮徽标同源 cartCount）', () => {
    const utils = source('src/utils/cart-badge.ts')
    expect(utils).toContain('CART_TAB_PATH = \'pages/user/shopping-cart/index\'')
    expect(utils).toContain('CART_BADGE_LIMIT = 99')
  })
})
