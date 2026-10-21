import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品卡片运费标签契约。
 *
 * 2026-10-21 产品要求：全站商品卡片不再展示「免配送费」。
 * 运费口径统一以商详 / 结算页为准——卡片上写「免配送费」容易让用户误以为
 * 该商品一定包邮，而实际运费可能受运费模板 / 满额规则影响。
 *
 * 若后续有人把该 chip 加回任意一张卡片，本测试会失败，提醒先确认产品口径。
 */
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** C 端会渲染商品运费 chip 的卡片宿主（列表/分类、装修瀑布流、装修商品 Tab） */
const GOODS_CARD_SURFACES = [
  'src/components/goods-list-panel/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
  'src/components/diy/diy-goods-tabs/index.vue',
]

describe('商品卡片不展示运费标签', () => {
  it.each(GOODS_CARD_SURFACES)('%s 的卡片 chip 不含「免配送费」', (file) => {
    const code = source(file)
    expect(code).not.toMatch(/tags\.push\(['"]免配送费['"]\)/)
    expect(code).not.toContain('item.freeShipping === true')
  })
})
