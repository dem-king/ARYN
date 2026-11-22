import { describe, expect, it } from 'vitest'
import { hasGoodsDetailSections, hasGoodsProfile, normalizeDetailSections, normalizeGoodsProfile } from './goods-profile'

describe('goods-profile', () => {
  it('空对象不产生商品档案入口', () => {
    expect(normalizeGoodsProfile(null)).toBeNull()
    expect(normalizeGoodsProfile({})).toBeNull()
    expect(normalizeGoodsProfile({ ingredients: '  ', nutrition: [], certifications: [] })).toBeNull()
    expect(hasGoodsProfile(null)).toBe(false)
  })

  it('保留配料、营养、过敏原、标准与认证，并折叠空营养行', () => {
    const profile = normalizeGoodsProfile({
      ingredients: ' 樱桃 ',
      nutrition: [
        { name: ' 能量 ', amount: '264', unit: 'kJ', nrv: '3%' },
        { name: '', amount: '1', unit: 'g' },
      ],
      allergens: '无',
      standard: '按企业标准',
      certifications: [' 产地直采 ', '冷链直供', '产地直采', ''],
    })

    expect(profile).toEqual({
      ingredients: '樱桃',
      nutrition: [{ name: '能量', amount: '264', unit: 'kJ', nrv: '3%' }],
      allergens: '无',
      standard: '按企业标准',
      certifications: ['产地直采', '冷链直供'],
    })
    expect(hasGoodsProfile(profile)).toBe(true)
  })

  it('只有标准/认证时仍展示档案入口', () => {
    const profile = normalizeGoodsProfile({ standard: 'GB/T 1', certifications: ['绿色食品'] })
    expect(profile?.standard).toBe('GB/T 1')
    expect(profile?.certifications).toEqual(['绿色食品'])
    expect(hasGoodsProfile(profile)).toBe(true)
  })

  it('结构化段落按录入顺序保留，空段整段丢弃', () => {
    const sections = normalizeDetailSections([
      { title: ' 产地 ', content: '烟台', imageUrl: '' },
      { title: '', content: '', imageUrl: '' },
      { title: '', content: '只有正文', imageUrl: '' },
    ])
    expect(sections).toEqual([
      { title: '产地', content: '烟台', imageUrl: '' },
      { title: '', content: '只有正文', imageUrl: '' },
    ])
    expect(hasGoodsDetailSections(sections)).toBe(true)
  })

  it('非数组/空数组不渲染详情段', () => {
    expect(normalizeDetailSections(null)).toEqual([])
    expect(normalizeDetailSections({})).toEqual([])
    expect(hasGoodsDetailSections([])).toBe(false)
  })
})
