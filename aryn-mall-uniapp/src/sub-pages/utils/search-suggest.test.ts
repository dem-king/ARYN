import { describe, expect, it } from 'vitest'

import { buildSearchSuggest, MAX_SUGGEST, mergeSuggestWords, suggestFromLabel } from './search-suggest'

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

  it('接口商品名联想不展示来源标签', () => {
    expect(suggestFromLabel('goods')).toBe('')
  })
})

describe('mergeSuggestWords', () => {
  it('接口词优先且标记 goods 来源', () => {
    const result = mergeSuggestWords(['苹果醋饮料', '苹果汁'], '苹果', ['苹果礼盒'], HOT)

    expect(result[0]).toMatchObject({ word: '苹果醋饮料', from: 'goods' })
    expect(result[1].from).toBe('goods')
  })

  it('接口词不足时用历史/热搜兜底补齐', () => {
    const result = mergeSuggestWords(['苹果醋饮料'], '苹果', ['苹果礼盒'], HOT)
    const words = result.map(i => i.word)

    expect(words).toContain('苹果醋饮料')
    expect(words).toContain('苹果礼盒')
    expect(result.find(i => i.word === '苹果礼盒')?.from).toBe('history')
    // 兜底词仍走本地匹配规则：前缀命中优先
    expect(result[result.length - 1].from).toBe('hot')
  })

  it('接口失败（空数组）时完全退化为本地联想', () => {
    const result = mergeSuggestWords([], '苹果', [], HOT)

    expect(result.length).toBeGreaterThan(0)
    expect(result.every(i => i.from !== 'goods')).toBe(true)
  })

  it('接口词与本地词跨来源去重', () => {
    const result = mergeSuggestWords(['苹果醋'], '苹果', ['苹果醋'], HOT)
    const words = result.map(i => i.word)

    expect(words.filter(w => w === '苹果醋')).toHaveLength(1)
    // 接口词优先，去重后保留 goods 来源
    expect(result.find(i => i.word === '苹果醋')?.from).toBe('goods')
  })

  it('接口词意外不含关键词时被丢弃（点了也搜不出的词不进面板）', () => {
    const result = mergeSuggestWords(['矿泉水'], '苹果', [], [])

    expect(result).toEqual([])
  })

  it('每个词都带命中区间且区间正确', () => {
    const result = mergeSuggestWords(['红富士苹果'], '苹果', ['苹果醋'], [])

    for (const item of result) {
      expect(item.hitStart).toBeDefined()
      expect(item.hitEnd).toBe((item.hitStart ?? 0) + '苹果'.length)
      expect(item.word.slice(item.hitStart ?? 0, item.hitEnd).toLowerCase()).toBe('苹果')
    }
    // 接口词在前：红富士苹果的「苹果」在下标 3；兜底词苹果醋的在下标 0
    expect(result[0].hitStart).toBe(3)
    expect(result[1].hitStart).toBe(0)
  })

  it('大小写不敏感命中与去重', () => {
    const result = mergeSuggestWords(['Red Apple Juice'], 'apple', ['Apple Pie'], [])

    expect(result.map(i => i.word)).toEqual(['Red Apple Juice', 'Apple Pie'])
  })

  it('总条数仍受 MAX_SUGGEST 约束', () => {
    const api = Array.from({ length: 20 }, (_, i) => `苹果味汽水${i}`)
    expect(mergeSuggestWords(api, '苹果', [], HOT).length).toBe(MAX_SUGGEST)
  })

  it('空关键词返回空数组', () => {
    expect(mergeSuggestWords(['苹果'], '', ['苹果'], HOT)).toEqual([])
    expect(mergeSuggestWords(['苹果'], '   ', ['苹果'], HOT)).toEqual([])
  })
})
