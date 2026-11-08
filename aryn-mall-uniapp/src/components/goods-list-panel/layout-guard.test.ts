import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品流两列布局守门。
 *
 * 这里踩过一次线上坑：卡片写成 `width: calc(50% - gap/2)` + 父级 `gap`，
 * 两张卡加间距正好等于容器 100%、零余量。小程序把 rpx 换算成整数 px 时多出的
 * 零头会让 flex-wrap 判定「放不下」，整屏退化成每行一张、右侧半屏空白——
 * 类型检查和单元测试都拦不住，只能在真机上肉眼发现。
 *
 * 现在改成交替瀑布流：两列容器 flex:1 均分（固定两条列），卡片宽度 100%、
 * 不再自带 50% 宽度，从根上避开半宽容差问题；列数（手机 2 / 宽屏 3）由
 * useResponsiveColumns 控制，分列由 splitIntoColumns 完成。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 只取组件最后一个 <style> 块，避开 <script> 里同名字符串 */
function styleBlock(file: string) {
  const matches = [...source(file).matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)]
  return matches.at(-1)?.[1] ?? ''
}

describe('商品流两列布局', () => {
  const panel = 'src/components/goods-list-panel/index.vue'

  it('两列容器用交替分列，而非 flex-wrap + 半宽容差为零的写法', () => {
    const code = source(panel)
    const style = styleBlock(panel)
    const grid = style.slice(style.indexOf('.goods-list--grid'))
    const rule = grid.slice(0, grid.indexOf('}'))

    expect(rule).toContain('display: flex')
    expect(rule).toContain('align-items: flex-start')
    expect(rule).not.toContain('flex-wrap')

    const column = style.slice(style.indexOf('.goods-column'))
    const columnRule = column.slice(0, column.indexOf('}'))
    expect(columnRule).toContain('flex: 1')
    expect(columnRule).toContain('min-width: 0')

    // 手机 2 列 / 宽屏 3 列，按索引交替入列
    expect(code).toContain('useResponsiveColumns(600, 2, 3)')
    expect(code).toContain('splitIntoColumns')
  })

  it('栅格卡片不再自带 50% 宽度（宽度由列容器决定）', () => {
    const style = styleBlock(panel)
    const grid = style.slice(style.indexOf('.goods-card--grid'))
    const rule = grid.slice(0, grid.indexOf('}'))

    expect(rule).not.toMatch(/width:\s*calc\(\s*50%/)
    expect(rule).not.toMatch(/width:\s*50%/)
  })
})
