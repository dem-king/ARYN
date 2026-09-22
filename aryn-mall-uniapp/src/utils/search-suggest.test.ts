import { describe, expect, it } from 'vitest'

import { buildSearchSuggest, MAX_SUGGEST, suggestFromLabel } from './search-suggest'

const HOT = ['红富士苹果', '苹果醋', '苹果汁', '鲜牛奶 950ml', '船上补给包', '矿泉水整箱']

describe('buildSearchSuggest', () => {
  it('空前缀返回空数组（由调用方展示默认面板）', () => {
    expect(buildSearchSuggest('', ['苹果'], HOT)).toEqual([])
    expect(buildSearchSuggest('   ', ['苹果'], HOT)).toEqual([])
  })

  it('前缀命中优先于包含命中', () => {
    const result = buildSearchSuggest('苹果', [], HOT)

    // 「苹果醋/苹果汁」是前缀命中，应排在包含命中之前
    expect(result[0].word).toBe('苹果醋')
    expect(result[1].word).toBe('苹果汁')
    expect(result.map(i => i.word)).toContain('红富士苹果')
  })

  it('单字输入也能联想（苹 -> 苹果类词全部命中）', () => {
    const words = buildSearchSuggest('苹', [], HOT).map(i => i.word)

    expect(words).toContain('苹果醋')
    expect(words).toContain('苹果汁')
    expect(words).toContain('红富士苹果')
  })

  it('历史词排在热搜词之前', () => {
    const result = buildSearchSuggest('苹果', ['苹果礼盒'], HOT)

    expect(result[0]).toMatchObject({ word: '苹果礼盒', from: 'history' })
    expect(result[1].from).toBe('hot')
  })

  it('与输入完全相同的词不重复推荐', () => {
    const words = buildSearchSuggest('苹果醋', [], HOT).map(i => i.word)
    expect(words).not.toContain('苹果醋')
  })

  it('跨来源去重：历史与热搜同词只出现一次', () => {
    const result = buildSearchSuggest('苹果', ['苹果醋'], HOT)
    const words = result.map(i => i.word)

    expect(words.filter(w => w === '苹果醋')).toHaveLength(1)
    // 历史优先，因此该词来源是 history
    expect(result.find(i => i.word === '苹果醋')?.from).toBe('history')
  })

  it('结果条数有上限', () => {
    const many = Array.from({ length: 30 }, (_, i) => `苹果${i}`)
    expect(buildSearchSuggest('苹果', [], many).length).toBe(MAX_SUGGEST)
  })

  it('输入大小写不敏感', () => {
    const words = buildSearchSuggest('SKU', [], ['sku 编码', 'SKU 品名']).map(i => i.word)
    expect(words).toHaveLength(2)
  })

  it('忽略空词与纯空白词', () => {
    const result = buildSearchSuggest('苹果', ['', '   '], HOT)
    expect(result.every(i => i.word.trim().length > 0)).toBe(true)
  })

  it('无任何命中时返回空数组（不编造联想）', () => {
    expect(buildSearchSuggest('zzzz', [], HOT)).toEqual([])
  })
})

describe('suggestFromLabel', () => {
  it('区分历史与热搜来源', () => {
    expect(suggestFromLabel('history')).toBe('历史')
    expect(suggestFromLabel('hot')).toBe('热搜')
  })
})
