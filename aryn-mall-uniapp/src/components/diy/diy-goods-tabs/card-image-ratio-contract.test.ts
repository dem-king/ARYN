import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 商品卡图片占位契约。
 *
 * 商品图按 widthFix 直接按原图撑高时，图片加载完成前没有占位高度，
 * 列表会随图片逐张加载不断跳动。正确口径：列表接口回填主图宽高，
 * 卡片用宽高锁住 padding-top 占位比例，图片加载前后高度不变。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('商品卡图片占位', () => {
  const card = source('src/components/diy/diy-goods-tabs/index.vue')

  it('按宽高锁定占位比例，不再用 widthFix 撑高卡片', () => {
    expect(card).toContain('imageAspectPadding')
    expect(card).toContain('imageBoxStyle(item)')
    expect(card).not.toContain('mode="widthFix"')
  })

  it('图片区始终有占位高度（空图也不塌）', () => {
    expect(card).toContain('goods-tabs__image-box" :style="imageBoxStyle(item)"')
  })

  it('列表归一化透传后端回填的主图宽高', () => {
    const normalizer = source('src/components/diy/retail-normalizers.ts')
    expect(normalizer).toContain('picWidth')
    expect(normalizer).toContain('picHeight')
  })

  it('商品瀑布流楼层同样按宽高锁占位，不再用 widthFix 撑高', () => {
    const waterfall = source('src/components/diy/diy-goods-waterfall/index.vue')
    expect(waterfall).toContain('imageAspectPadding')
    expect(waterfall).not.toContain('mode="widthFix"')
  })
})
