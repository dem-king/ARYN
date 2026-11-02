import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 游客登录守卫接线守门（2026-10-29 第二轮修复）。
 *
 * 背景：路由守卫只拦 `uni-mini-router` 的 `router.push/replace`；页面里的裸
 * `uni.navigateTo`、装修组件链接、微信分享卡片直达都能把游客带进需登录的页面/按钮。
 * 请求层（`handlers.isGuestRequest`）只保证「没带 token 的 401 不再误弹会话失效」，
 * 但**不会引导登录** —— 入口漏接守卫时，游客点完按钮只看到一条 toast、没有后续动作，
 * 或者页面一进来就是空态。这类回归类型检查发现不了，只能用源码契约钉住。
 *
 * 统一入口：`src/composables/useRequireLogin.ts`
 *  - 点击型入口：`if (!ensureLoggedIn()) return`（写 redirectPath → 打开登录页）
 *  - 页面级入口：`authStore.isLoggedIn` 判空 + 游客态/跳登录（不发需登录请求）
 */
const projectRoot = fileURLToPath(new URL('..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** marker 之后紧邻的函数体必须接入 `ensureLoggedIn` 守卫（防「加了 import 没加守卫」） */
function expectGuardAfter(file: string, marker: string, window = 320) {
  const code = source(file)
  const index = code.indexOf(marker)
  expect(index, `${file} 未找到 ${marker}`).toBeGreaterThan(-1)
  expect(
    code.slice(index, index + window),
    `${file} 的 ${marker} 应在函数体首行接入 ensureLoggedIn 登录守卫`,
  ).toContain('ensureLoggedIn')
}

describe('guest login guard contract', () => {
  it('ships one reusable guard that records redirectPath and opens the login page', () => {
    const guard = source('src/composables/useRequireLogin.ts')
    expect(guard).toContain("export const LOGIN_PAGE_PATH = '/pages/login/index'")
    expect(guard).toContain("export const LOGIN_REDIRECT_KEY = 'redirectPath'")
    // 回跳必须带 query（商详 id / 共享车分享 token 都靠它），因此取完整路径
    expect(guard).toContain('getCurrentFullPath()')
    expect(guard).toContain('if (authStore.isLoggedIn)')
    expect(guard).toContain('uni.navigateTo')
    // 连续点按不叠登录页、已在登录页不回跳成环
    expect(guard).toContain('navigating')
    expect(guard).toContain('LOGIN_PAGE_ROUTE')
  })

  it('guards every coupon-receive entry point', () => {
    expectGuardAfter('src/components/diy/diy-coupon-receive/index.vue', 'async function handleReceive(')
    expectGuardAfter('src/components/diy/diy-coupon-ladder/index.vue', 'async function handleReceive(')
    expectGuardAfter('src/sub-pages/promotion/coupon/coupon-list/index.vue', 'async function handleAction(')
  })

  it('hides the message bell from guests and guards every message page', () => {
    // 铃铛是首页进入消息中心的唯一入口：未登录整节点不渲染（与代码注释口径一致）
    const searchBar = source('src/components/diy/diy-search-bar/index.vue')
    expect(searchBar).toMatch(/<view v-if="authStore\.isLoggedIn" class="search-bar-bell"/)

    // 消息中心（非白名单页，可被深链直达）：三个取数函数都不许为游客发请求，
    // 模板给游客态登录引导；否则一次进入就是 3+1 个并发 401
    const notice = source('src/sub-pages/message/notice/index.vue')
    expect(notice).toMatch(/async function loadNotices\([\s\S]{0,200}if \(!authStore\.isLoggedIn\)/)
    expect(notice).toMatch(/async function loadConversations\([\s\S]{0,200}if \(!authStore\.isLoggedIn\)/)
    expect(notice).toMatch(/async function refresh\([\s\S]{0,200}if \(!authStore\.isLoggedIn\)/)
    expect(notice).toContain("t('message.hub.loginRequired')")
    expect(notice).toContain("t('common.goLogin')")

    // 客服 / 通知详情：深链打开未登录 → 记录回跳并跳登录，不建 WS、不拉详情
    expectGuardAfter('src/sub-pages/message/chat/index.vue', 'onLoad(async (options)')
    const chat = source('src/sub-pages/message/chat/index.vue')
    expect(chat).toMatch(/onShow\(\(\) => \{[\s\S]{0,260}authStore\.isLoggedIn/)
    expectGuardAfter('src/sub-pages/message/notice/detail.vue', 'onLoad(async (options)')
  })

  it('guards goods-detail add-to-cart / collect / customer-service', () => {
    const goodsDetail = 'src/sub-pages/product/goods-detail/index.vue'
    // 单规格加购走 useQuickCart、SKU 面板在 goods-detail-sheet，均已自带守卫；
    // 这里只钉本页三条直连需登录接口的路径
    expectGuardAfter(goodsDetail, 'async function addCart(')
    expectGuardAfter(goodsDetail, 'async function handleCollect(')
    expectGuardAfter(goodsDetail, 'function toCustomerService(')
  })

  it('guards the shared-cart share-card landing page and keeps its query for re-entry', () => {
    const sharedCartFile = 'src/sub-pages/order/shared-cart/detail.vue'
    // onLoad 先按分享参数拼回跳路径再守卫，窗口放宽到整段函数体前半
    expectGuardAfter(sharedCartFile, 'onLoad((options)', 900)
    const sharedCart = source(sharedCartFile)

    // 回跳路径必须原样带 id/token：登录后 reLaunch 回本页才能自动完成 join
    const onLoadStart = sharedCart.indexOf('onLoad((options)')
    const onShareStart = sharedCart.indexOf('onShareAppMessage(')
    expect(onLoadStart).toBeGreaterThan(-1)
    expect(onShareStart).toBeGreaterThan(onLoadStart)
    const onLoadBody = sharedCart.slice(onLoadStart, onShareStart)
    expect(onLoadBody).toContain('redirectPath')
    expect(onLoadBody).toContain('token=')

    // onShow 的详情拉取同样要挡游客（onLoad 跳登录瞬间/取消登录返回时不再发 3 个请求）
    expect(sharedCart).toMatch(/onShow\(\(\) => \{[\s\S]{0,200}if \(!authStore\.isLoggedIn\)/)
  })

  it('guards group-buy open/join actions (share-link landing is public, actions are not)', () => {
    const groupBuy = 'src/sub-pages/promotion/group-buy/group-buy-detail/index.vue'
    expectGuardAfter(groupBuy, 'async function handleOpenGroup()')
    expectGuardAfter(groupBuy, 'async function handleJoinGroup(')
  })

  it('keeps the first-wave frequently-bought guard under contract', () => {
    const frequent = source('src/sub-pages/product/frequent/index.vue')
    expect(frequent).toContain('if (!authStore.isLoggedIn)')
    expect(frequent).not.toMatch(/\bonMounted\(/)
  })

  it('routes every remaining login jump through the shared guard', () => {
    // 旧式 `uni.navigateTo('/pages/login/index')` 只挡了请求、不记 redirectPath：
    // 游客登录成功会被兜底送回首页，丢掉当前商品/列表页。全部收口到统一守卫。
    const unified = [
      'src/composables/useQuickCart.ts',
      'src/components/goods-detail-sheet/index.vue',
      'src/sub-pages/order/shared-cart/list.vue',
      'src/sub-pages/order/shared-cart/import-entry.vue',
      'src/sub-pages/order/shared-cart/import.vue',
      'src/sub-pages/order/shared-cart/chain-import.vue',
      'src/sub-pages/vessel/bind/index.vue',
      'src/sub-pages/vessel/port-vehicles/index.vue',
    ]
    for (const file of unified) {
      const code = source(file)
      expect(code, `${file} 应接入统一登录守卫`).toContain('useRequireLogin')
      expect(code, `${file} 不应再裸跳登录页（会丢 redirectPath）`).not.toContain(
        "uni.navigateTo({ url: '/pages/login/index' })",
      )
    }
  })

  it('calls the guard with an explicit no-arg invocation in templates', () => {
    // 模板 `@tap="goLogin"` 会把事件对象当第一个实参传进去；守卫签名是
    // `goLogin(redirectPath?: string)`，事件对象会让 redirectPath.startsWith 抛 TypeError，
    // 游客点「去登录」直接无反应。统一写成 `goLogin()`。
    for (const file of [
      'src/sub-pages/message/notice/index.vue',
      'src/sub-pages/order/shared-cart/list.vue',
      'src/sub-pages/order/shared-cart/detail.vue',
      'src/sub-pages/vessel/bind/index.vue',
      'src/sub-pages/vessel/port-vehicles/index.vue',
    ]) {
      expect(source(file), `${file} 的模板绑定必须显式调用 goLogin()`).not.toMatch(/@(?:tap|click)="goLogin"/)
    }
  })
})
