import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品卡片子标题渲染契约。
 *
 * 后台商品有「商品子标题」（goods_spu.sub_title），列表接口也会返回；
 * 零售楼层与页面级推荐卡都必须在卡片里渲染该字段，
 * 否则运营填写的商品介绍在 C 端商品卡上全部消失。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const GOODS_CARDS = [
  'src/components/diy/diy-goods-group/index.vue',
  'src/components/diy/diy-goods-waterfall/index.vue',
  'src/components/diy/diy-goods-ranking/index.vue',
  'src/components/diy/diy-goods-recommend/index.vue',
  'src/components/diy/diy-goods-tabs/index.vue',
  'src/components/waterfall-goods/index.vue',
]

describe('商品卡片子标题', () => {
  it('零售商品归一化保留 subTitle', () => {
    expect(source('src/components/diy/retail-normalizers.ts')).toContain(
      'subTitle: toText(record.subTitle)',
    )
  })

  it.each(GOODS_CARDS)('%s 渲染商品子标题', (file) => {
    expect(source(file)).toContain('item.subTitle')
  })

  it.each(GOODS_CARDS)('%s 隐藏与商品名重复的子标题', (file) => {
    expect(source(file)).toContain('shouldShowSubtitle')
  })
})
