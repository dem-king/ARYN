import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 多 Tab 商品流切 Tab 的页面位置守门。
 *
 * 线上表现：点 Tab 后页面直接跳回最顶部。根因有两层：
 *   1. z-paging 的 autoScrollToTopWhenReload 默认 true，reload 会 pageScrollTo(0)；
 *   2. reload 先清空列表，页面高度骤降，滚动位置被浏览器夹回顶部。
 * 这里锁住两处修复，避免后续把默认值放开或删掉高度占位。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const componentPath = 'src/components/diy/diy-goods-tabs/index.vue'

function source() {
  return readFileSync(resolve(projectRoot, componentPath), 'utf8')
}

describe('多Tab商品流切Tab页面位置', () => {
  it('关闭 z-paging reload 的自动回顶', () => {
    expect(source()).toContain(':auto-scroll-to-top-when-reload="false"')
  })

  it('切 Tab 期间保留旧列表高度，避免清空列表把滚动位置夹回顶部', () => {
    const text = source()

    expect(text).toContain('feedMinHeight')
    expect(text).toContain('minHeight')
  })

  it('仅在 Tab 栏已吸顶时滚回商品流起点', () => {
    const text = source()

    expect(text).toContain('barStuck')
    expect(text).toContain('barTop < rootTop')
    expect(text).toContain('uni.pageScrollTo(')
    // 不允许再出现「无条件滚到页面顶部」的写法
    expect(text).not.toContain('scrollTop: 0')
  })
})
