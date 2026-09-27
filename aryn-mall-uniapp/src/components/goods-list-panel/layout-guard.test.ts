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
 * 因此两列必须走 grid 轨道均分（先扣 gap 再分轨道），卡片不再自带 50% 宽度。
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

  it('两列容器用 grid 轨道均分，而非 flex-wrap + 半宽容差为零的写法', () => {
    const style = styleBlock(panel)
    const grid = style.slice(style.indexOf('.goods-list--grid'))
    const rule = grid.slice(0, grid.indexOf('}'))

    expect(rule).toContain('display: grid')
    expect(rule).toMatch(/grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/)
  })

  it('栅格卡片不再自带 50% 宽度（宽度由 grid 轨道决定）', () => {
    const style = styleBlock(panel)
    const grid = style.slice(style.indexOf('.goods-card--grid'))
    const rule = grid.slice(0, grid.indexOf('}'))

    expect(rule).not.toMatch(/width:\s*calc\(\s*50%/)
    expect(rule).not.toMatch(/width:\s*50%/)
  })
})
