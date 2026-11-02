import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 内嵌装修块的背景隔离契约。
 *
 * 个人中心 / 分类页 / 商详页把 `DiyPage` 当页面中段的一段内容使用（`embedded`），
 * 宿主页面自己负责底色。内嵌块一旦把装修页的 `page.backgroundColor` /
 * `backgroundImage` 铺出来，宿主页面里就会多出一块异色矩形——
 * 页面引用过主题时这里读到的是发布固化的 `themeSnapshot.pageBackgroundColor`，
 * 商城换肤之后就是一条与当前主题无关的旧色带，看起来像「装修组件背景坏了」。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DIY_RENDERER = 'src/components/diy/index.vue'
const EMBEDDED_HOSTS = [
  'src/pages/user/user-center/index.vue',
  'src/components/category-browser/index.vue',
  'src/sub-pages/product/goods-detail/index.vue',
]

describe('内嵌装修块不铺页面底色', () => {
  const code = source(DIY_RENDERER)

  it('pageStyle 在内嵌模式下不下发页面底色与背景图', () => {
    // 底色与背景图都必须被 embedded 拦住：只拦一个仍会漏出色带
    expect(code).toMatch(
      /backgroundColor\S?: props\.embedded \? undefined : effectiveTheme\.value\.backgroundColor/,
    )
    expect(code).toContain('!props.embedded && document.value.page.backgroundImage')
  })

  it('内嵌修复不改变布局：内嵌块依旧不撑满 100vh', () => {
    // 背景是「画出来的一层矩形」，布局高度仍由 embedded 的 min-height: 0 控制，
    // 二者必须分开：只关背景不能顺手把内嵌块的 min-height 也改成 0/100vh 之外的语义
    expect(code).toContain('&--embedded')
    expect(code).toContain('min-height: 0')
  })

  it('三处内嵌宿主都走同一开关，保证修的是同一个渲染分支', () => {
    for (const host of EMBEDDED_HOSTS) {
      expect(source(host), `${host} 应以 embedded 方式渲染装修块`).toContain(
        'embedded',
      )
    }
  })
})
