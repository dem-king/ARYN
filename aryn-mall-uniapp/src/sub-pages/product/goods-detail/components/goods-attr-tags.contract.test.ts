import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { GOODS_ATTR_TAG_ICON_KEYS } from '@/api/product/attr-tags'
import { describe, expect, it } from 'vitest'

/**
 * 属性卡接入的契约守门。
 *
 * 纯函数测试覆盖数据降级；这里按源码结构锁住「接在哪、怎么点、图标从哪来」：
 * 组件被挪出标题卡、popup 换成外链跳转、图标类写成运行时拼接（UnoCSS 扫不到，
 * 真机整块空白）——页面静态看都对，问题只在真机暴露，因此用结构断言兜住。
 */
const projectRoot = fileURLToPath(new URL('../../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const GOODS_INFO = 'src/sub-pages/product/goods-detail/components/GoodsInfo.vue'
const ATTR_TAGS = 'src/sub-pages/product/goods-detail/components/GoodsAttrTags.vue'

describe('goods attr tags wiring', () => {
  it('mounts inside the title card: after subtitle/free-shipping tags, before stock meta', () => {
    const info = source(GOODS_INFO)
    const tagsIndex = info.indexOf('goods-title-card__tags')
    const componentIndex = info.indexOf('<GoodsAttrTags')
    const metaIndex = info.indexOf('goods-title-card__meta')

    expect(tagsIndex).toBeGreaterThan(-1)
    expect(componentIndex).toBeGreaterThan(tagsIndex)
    expect(metaIndex).toBeGreaterThan(componentIndex)
  })

  it('feeds raw attrTags from the detail response without reshaping elsewhere', () => {
    const info = source(GOODS_INFO)
    expect(info).toContain(':attr-tags="goodsSpu?.attrTags"')
  })

  it('renders nothing when the payload is empty (backend not deployed stays unchanged)', () => {
    const component = source(ATTR_TAGS)
    expect(component).toContain('v-if="displayTags.length > 0"')
    expect(component).toContain('normalizeGoodsAttrTags')
  })

  it('uses a horizontal scroll-view with the documented card metrics', () => {
    const component = source(ATTR_TAGS)
    expect(component).toContain('scroll-x')
    expect(component).toContain(':show-scrollbar="false"')
    expect(component).toContain(':enable-flex="true"')
    expect(component).toMatch(/width:\s*176rpx/)
    expect(component).toMatch(/gap:\s*12rpx/)
  })

  it('opens popup content via wd-popup instead of an external URL', () => {
    const component = source(ATTR_TAGS)
    expect(component).toContain('<wd-popup')
    expect(component).toContain('position="bottom"')
    expect(component).toContain('popupContent')
  })

  it('navigates only through the shared internal-path guard', () => {
    const component = source(ATTR_TAGS)
    expect(component).toContain('uni.navigateTo')
    expect(component).toContain('isAllowedInternalPagePath(tag.linkUrl)')
  })

  it('maps every preset icon key to an already-installed iconify class', () => {
    const component = source(ATTR_TAGS)
    const used = new Set(Array.from(component.matchAll(/i-carbon:([a-z-]+)/g), match => match[1]))

    // carbon 集合与后端预置 key 同名，映射缺一个真机就少一个图标
    expect([...used].sort()).toEqual([...GOODS_ATTR_TAG_ICON_KEYS].sort())
  })

  it('does not hardcode the runtime mode (mp has no import.meta.env guarantee)', () => {
    expect(source(ATTR_TAGS)).not.toContain('import.meta.env')
  })
})
