import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品流「交替瀑布流」口径守门。
 *
 * 首页 Tab 流、商品分组、经典商品楼层、瀑布流楼层、商品列表页、推荐流
 * 必须统一为：按索引交替分列 + 按主图宽高锁占位比例。
 * 新增商品列表组件时把它加进 MASONRY_COMPONENTS，避免又回到等高栅格。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const MASONRY_COMPONENTS = [
  'src/components/diy/diy-goods-tabs/index.vue',
  'src/components/diy/diy-goods-group/index.vue',
  'src/components/diy/diy-goods/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
  'src/components/goods-list-panel/index.vue',
  'src/components/waterfall-goods/index.vue',
]

describe('商品流交替瀑布流口径', () => {
  it.each(MASONRY_COMPONENTS)('%s 按索引交替分列', (file) => {
    expect(source(file)).toContain('splitIntoColumns')
  })

  it.each(MASONRY_COMPONENTS)('%s 按主图宽高锁图片占位比例', (file) => {
    expect(source(file)).toContain('imageAspectPadding')
  })

  it('瀑布流楼层不再依赖 column-count 的列平衡（翻页会跳列）', () => {
    expect(source('src/components/diy/diy-goods-waterfall/index.vue')).not.toContain('columnCount')
  })

  it('列表页与推荐流在宽屏使用 3 列', () => {
    for (const file of [
      'src/components/goods-list-panel/index.vue',
      'src/components/waterfall-goods/index.vue',
    ]) {
      expect(source(file)).toContain('useResponsiveColumns(600, 2, 3)')
    }
  })
})
