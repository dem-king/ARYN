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

  it('首次浏览某个 Tab 且 Tab 栏已吸顶时，滚回商品流起点', () => {
    const text = source()

    expect(text).toContain('barStuck')
    // sticky 元素只会被往下钳制：吸顶时栏顶**大于**根节点自然位置。
    // 写成小于永远不成立，首次切 Tab 会继承上一个 Tab 的滚动位置。
    expect(text).toContain('barTop > rootTop')
    expect(text).toContain('uni.pageScrollTo(')
    // 不允许再出现「无条件滚到页面顶部」的写法
    expect(text).not.toContain('scrollTop: 0')
  })
})

/**
 * 三个 Tab 各自记住自己的浏览位置。
 *
 * 三个 Tab 共用同一个页面滚动条，只换列表数据：不按 Tab 各存一份离开时的位置，
 * 切到别的 Tab 会继承上一个 Tab 的滚动高度、切回来也回不到原来的位置。
 * 这里锁住「按 Tab 记录 + 切回时恢复」的骨架，避免后续把位置记忆退化成全局单份。
 */
describe('多Tab商品流各Tab独立记录浏览位置', () => {
  it('feed 状态里按 Tab 存浏览位置', () => {
    const text = source()

    expect(text).toContain('viewportTop?: number')
    expect(text).toContain('leavingState.viewportTop = feedTop')
  })

  it('切回浏览过的 Tab 时恢复该 Tab 的位置', () => {
    const text = source()

    expect(text).toContain('pendingScrollRestore')
    expect(text).toContain('restoreScrollPosition(')
    // 恢复必须针对发起切换的那个 Tab，不能串到别的 Tab 上
    expect(text).toContain('restore.tabId !== tabId')
  })

  it('锚点用商品流内容区，不受 Tab 级权益条显隐影响', () => {
    const text = source()

    expect(text).toContain(`query.select('.goods-tabs__inner')`)
    expect(text).toContain(`.select('.goods-tabs__inner')`)
  })

  it('恢复发生在列表渲染与占位高度释放之后', () => {
    const text = source()
    const finallyBlock = text.slice(text.indexOf('finally {'))

    // 先释放 feedMinHeight 再恢复：占位高度会让恢复值被随后收缩的页面夹回
    expect(finallyBlock.indexOf('feedMinHeight.value = 0')).toBeLessThan(
      finallyBlock.indexOf('restoreScrollPosition('),
    )
  })
})
