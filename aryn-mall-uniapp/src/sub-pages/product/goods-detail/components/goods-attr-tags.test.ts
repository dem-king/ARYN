import {
  GOODS_ATTR_TAG_ICON_KEYS,
  GOODS_ATTR_TAG_MAX,
} from '@/api/product/attr-tags'
import { describe, expect, it } from 'vitest'
import { isAllowedInternalPagePath, normalizeGoodsAttrTags } from './goods-attr-tags'

/**
 * 属性卡数据边界的纯函数测试。
 *
 * 重点不是「正常数据能渲染」，而是**脏数据/旧数据不能把首屏搞坏**：
 * 后端未部署时字段缺失要整块消失；运营配置的外链、危险协议、空弹层
 * 必须在进入模板/`uni.navigateTo` 之前被降级。这些分支是页面回归的高发点，
 * 因此逐条覆盖。
 */

function baseTag(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return { label: '系列', value: '地标', ...overrides }
}

describe('normalizeGoodsAttrTags', () => {
  it('字段缺失/非数组时返回空数组（后端未部署的降级底线）', () => {
    expect(normalizeGoodsAttrTags(undefined)).toEqual([])
    expect(normalizeGoodsAttrTags(null)).toEqual([])
    expect(normalizeGoodsAttrTags({})).toEqual([])
    expect(normalizeGoodsAttrTags('')).toEqual([])
    expect(normalizeGoodsAttrTags(6)).toEqual([])
    expect(normalizeGoodsAttrTags([])).toEqual([])
  })

  it('归一化正常字段并套用默认值', () => {
    const [tag] = normalizeGoodsAttrTags([baseTag()])

    expect(tag).toEqual({
      key: 'attr-1',
      label: '系列',
      value: '地标',
      tone: 'gray',
      linkType: 'none',
      iconKey: '',
      iconUrl: '',
      popupTitle: '',
      popupContent: '',
      linkUrl: '',
    })
  })

  it('trim 属性名与属性值', () => {
    const [tag] = normalizeGoodsAttrTags([baseTag({ value: '  地标  ', label: '  系列  ' })])
    expect(tag.value).toBe('地标')
    expect(tag.label).toBe('系列')
  })

  it('过滤空占位行、非对象项与缺一半字段的脏项', () => {
    const tags = normalizeGoodsAttrTags([
      null,
      'x',
      3,
      {},
      baseTag({ value: '' }),
      baseTag({ label: '   ' }),
      baseTag({ value: '  ', label: '系列' }),
      baseTag({ value: '地标', label: '' }),
      baseTag({ value: '冷藏', label: '储存条件' }),
    ])

    expect(tags).toHaveLength(1)
    expect(tags[0].value).toBe('冷藏')
  })

  it('超过 6 个截断，保留展示顺序', () => {
    const raw = Array.from({ length: 9 }, (_, index) => baseTag({ value: `值${index}`, label: `名${index}` }))
    const tags = normalizeGoodsAttrTags(raw)

    expect(GOODS_ATTR_TAG_MAX).toBe(6)
    expect(tags).toHaveLength(6)
    expect(tags.map(tag => tag.value)).toEqual(['值0', '值1', '值2', '值3', '值4', '值5'])
  })

  it('tone 只保留白名单，未知值降级 gray', () => {
    const tags = normalizeGoodsAttrTags([
      baseTag({ tone: 'blue' }),
      baseTag({ value: '冷藏', tone: 'purple' }),
      baseTag({ value: '上架', tone: ' GREEN ' }),
    ])

    expect(tags.map(tag => tag.tone)).toEqual(['blue', 'gray', 'gray'])
  })

  it('linkType 未知或缺失降级 none', () => {
    const tags = normalizeGoodsAttrTags([
      baseTag({ linkType: 'external' }),
      baseTag({ value: '冷藏', linkType: undefined }),
      baseTag({ value: '榜单', linkType: 'popup', popupContent: '说明' }),
    ])

    expect(tags.map(tag => tag.linkType)).toEqual(['none', 'none', 'popup'])
  })

  it('icon：预置 key 保留，http(s) 地址走图片，其余静默降级为无图标', () => {
    const tags = normalizeGoodsAttrTags([
      baseTag({ icon: 'location' }),
      baseTag({ value: '冷藏', icon: 'snowflake' }),
      baseTag({ value: '图片', icon: 'https://cdn.example.com/a.png' }),
      baseTag({ value: '未知', icon: 'rocket' }),
      baseTag({ value: '危险', icon: 'javascript:alert(1)' }),
      baseTag({ value: '空', icon: '   ' }),
    ])

    expect(tags[0]).toMatchObject({ iconKey: 'location', iconUrl: '' })
    expect(tags[1]).toMatchObject({ iconKey: 'snowflake', iconUrl: '' })
    expect(tags[2]).toMatchObject({ iconKey: '', iconUrl: 'https://cdn.example.com/a.png' })
    expect(tags[3]).toMatchObject({ iconKey: '', iconUrl: '' })
    expect(tags[4]).toMatchObject({ iconKey: '', iconUrl: '' })
    expect(tags[5]).toMatchObject({ iconKey: '', iconUrl: '' })
  })

  it('预置图标 key 白名单与后端契约一致', () => {
    expect([...GOODS_ATTR_TAG_ICON_KEYS]).toEqual([
      'location',
      'snowflake',
      'trophy',
      'package',
      'security',
      'time',
      'shopping-bag',
      'star-filled',
    ])
  })

  it('popup：内容必填，标题缺省取属性值', () => {
    const tags = normalizeGoodsAttrTags([
      baseTag({ linkType: 'popup', popupContent: '  0~4℃ 冷藏保存  ' }),
      baseTag({ value: '冷藏', linkType: 'popup', popupContent: '低温保存', popupTitle: ' 储存条件 ' }),
      // 没有正文的弹层等于点击无反馈：降级为纯展示
      baseTag({ value: '空弹层', linkType: 'popup', popupContent: '   ' }),
    ])

    expect(tags[0]).toMatchObject({
      linkType: 'popup',
      popupTitle: '地标',
      popupContent: '0~4℃ 冷藏保存',
      linkUrl: '',
    })
    expect(tags[1]).toMatchObject({ popupTitle: '储存条件', popupContent: '低温保存' })
    expect(tags[2]).toMatchObject({ linkType: 'none', popupTitle: '', popupContent: '' })
  })

  it('page：只放行 /pages/ 与 /sub-pages/ 站内路径', () => {
    const tags = normalizeGoodsAttrTags([
      baseTag({ linkType: 'page', linkUrl: '/pages/product/category/index' }),
      baseTag({ value: '榜单', linkType: 'page', linkUrl: '/sub-pages/product/goods-list/index?id=1' }),
      baseTag({ value: '外链', linkType: 'page', linkUrl: 'https://example.com/rank' }),
      baseTag({ value: '协议', linkType: 'page', linkUrl: 'javascript:alert(1)' }),
      baseTag({ value: '相对', linkType: 'page', linkUrl: 'pages/home/index' }),
      baseTag({ value: '越界', linkType: 'page', linkUrl: '/pages/../admin/index' }),
    ])

    expect(tags[0]).toMatchObject({ linkType: 'page', linkUrl: '/pages/product/category/index' })
    expect(tags[1]).toMatchObject({ linkType: 'page', linkUrl: '/sub-pages/product/goods-list/index?id=1' })
    expect(tags.slice(2).map(tag => tag.linkType)).toEqual(['none', 'none', 'none', 'none'])

    // linkType=page 但缺路径同样降级（不会带着空 URL 调 navigateTo）
    const [missing] = normalizeGoodsAttrTags([baseTag({ linkType: 'page' })])
    expect(missing).toMatchObject({ linkType: 'none', linkUrl: '' })
  })

  it('key 缺失时按顺序补 attr-N，重复 key 去重', () => {
    const tags = normalizeGoodsAttrTags([
      baseTag({ key: 'custom', value: '一' }),
      baseTag({ key: 'custom', value: '二' }),
      baseTag({ value: '三' }),
      baseTag({ key: '   ', value: '四' }),
    ])

    expect(tags.map(tag => tag.key)).toEqual(['custom', 'custom-2', 'attr-3', 'attr-4'])
  })

  it('不修改接口原对象（模板/请求缓存复用同一份数据）', () => {
    const raw = [baseTag({ value: '  地标  ', tone: ' PURPLE ' })]
    normalizeGoodsAttrTags(raw)

    expect(raw[0].value).toBe('  地标  ')
    expect(raw[0].tone).toBe(' PURPLE ')
  })
})

describe('isAllowedInternalPagePath', () => {
  it('放行站内页面路径（含 query）', () => {
    expect(isAllowedInternalPagePath('/pages/home/index')).toBe(true)
    expect(isAllowedInternalPagePath('/sub-pages/product/goods-list/index?id=1')).toBe(true)
    expect(isAllowedInternalPagePath('  /pages/user/user-center/index  ')).toBe(true)
  })

  it('拒绝外链、协议、相对路径与越界写法', () => {
    expect(isAllowedInternalPagePath('https://example.com')).toBe(false)
    expect(isAllowedInternalPagePath('//evil.example.com/pages/x')).toBe(false)
    expect(isAllowedInternalPagePath('/Pages/home/index')).toBe(false)
    expect(isAllowedInternalPagePath('pages/home/index')).toBe(false)
    expect(isAllowedInternalPagePath('/pages/../admin/index')).toBe(false)
    expect(isAllowedInternalPagePath('/pages/a b/index')).toBe(false)
    expect(isAllowedInternalPagePath('/sub-pages/a\\b/index')).toBe(false)
    expect(isAllowedInternalPagePath(`/pages/${'a'.repeat(600)}`)).toBe(false)
  })

  it('非字符串输入一律拒绝', () => {
    expect(isAllowedInternalPagePath(undefined)).toBe(false)
    expect(isAllowedInternalPagePath(null)).toBe(false)
    expect(isAllowedInternalPagePath(123)).toBe(false)
    expect(isAllowedInternalPagePath('')).toBe(false)
    expect(isAllowedInternalPagePath('   ')).toBe(false)
  })
})
