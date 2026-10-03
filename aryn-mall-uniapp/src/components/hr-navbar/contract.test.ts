import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 全局导航栏渲染契约守门。
 *
 * 这类缺陷编译、类型检查、单测全绿，只在真机上表现为「整条导航栏空白」：
 *
 * 1. 命名插槽上的 `v-if` 是**编译期**登记的：小程序产物把插槽名静态写进 u-s
 *    （`u-s="{{['left','title','right']}}"`），与运行时条件无关。于是
 *    wd-navbar 里 `!$slots.title` 恒为 false，它自己的 title 文案分支被永久
 *    跳过；未传 backgroundColor 时插槽内容又是空的 —— 标题整条消失。
 *    购物车页的「管理」按钮同属这一条 navbar，一并不可见。
 *
 * 2. wd-navbar 把 right 插槽绝对定位在 right:0，正好压在微信原生胶囊
 *    「… ○」下面：文字被盖住看不见、点击也落在胶囊上。这是**只有真机
 *    才会出现**的遮挡，浏览器与单测都发现不了。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const NAVBAR = 'src/components/hr-navbar/index.vue'
const CART_PAGE = 'src/pages/user/shopping-cart/index.vue'

describe('hr-navbar rendering contract', () => {
  it('never puts v-if on a named slot template', () => {
    const navbar = source(NAVBAR)
    // 注释里会引用这个反例，先剥掉 HTML 注释再扫，避免把说明文字当成真实模板
    const markup = navbar.replace(/<!--[\s\S]*?-->/g, '')
    // 只看带 `#slotName` 的 template 开标签，检查是否混进了 v-if / v-else
    for (const tag of markup.match(/<template\b[^>]*>/g) ?? []) {
      if (!/#[a-z-]/.test(tag))
        continue
      expect(tag, `命名插槽被条件化会让插槽名在编译期永久登记：${tag}`).not.toMatch(/\sv-(if|else|else-if|show)=/)
    }
  })

  it('renders the title in an always-present title slot', () => {
    const navbar = source(NAVBAR)
    expect(navbar).toContain('<template #title>')
    expect(navbar).toContain('{{ title }}')
  })

  it('keeps the right slot clear of the wechat capsule', () => {
    const navbar = source(NAVBAR)
    // 真机上胶囊遮挡只靠避让量解决，必须读胶囊位置而不是写死像素
    expect(navbar).toContain('getMenuButtonBoundingClientRect')
    expect(navbar).toMatch(/rightInset/)
  })
})

describe('cart page manage entry contract', () => {
  it('keeps the manage toggle reachable', () => {
    const cart = source(CART_PAGE)
    // 管理入口一度放在导航栏右侧插槽，但小程序端那里要避让微信胶囊，
    // 空间不足以安稳放下文字；现改挂列表头（.manage-pill），此处只守「入口仍存在」。
    expect(cart).toContain('manage-pill')
    expect(cart).toContain('handleEdit')
    expect(cart).toContain('共 ')
  })

  it('keeps a delete path for both single row and batch selection', () => {
    const cart = source(CART_PAGE)
    // 单个删除（管理模式卡片右上角）与批量删除（底部操作条）必须都在
    expect(cart).toContain('function delOne')
    expect(cart).toContain('function delCart')
    expect(cart).toContain('delShoppingCart')
  })

  it('does not show a decorative shipping address row', () => {
    const cart = source(CART_PAGE)
    // 该行曾常驻在页面顶部，但它纯展示：state.address 不参与下单，
    // toSettlement 不带地址，结算页自己会重新取并允许改选。
    // 留着占位只会误导（船内配送 delivery_way=4 时地址根本不用）。
    expect(cart).not.toContain('请添加收货地址')
    expect(cart).not.toContain('getDefaultAddress')
    expect(cart).not.toContain('cart-header')
  })

  it('labels the current vessel group only when a call is actually selected', () => {
    const cart = source(CART_PAGE)
    // currentKey 为空时它与「无归属行」都是 ''，直接比 key === currentKey
    // 会把「未指定配送计划」标成「当前船舶：未命名」——用户有船却显示未命名
    expect(cart).toContain('key && key === currentKey')
    expect(cart).toContain('isCurrent: !!key && key === currentKey')
  })

  it('loads the ship context itself instead of relying on a home visit', () => {
    const cart = source(CART_PAGE)
    // shipContext 不持久化，冷启动为空；只有首页装修组件会写入它，
    // 没进过首页就直接点购物车 Tab 的用户会看到「未命名」
    expect(cart).toContain('useShipContextLoad')
    expect(cart).toContain('loadShipContext()')
  })
})
