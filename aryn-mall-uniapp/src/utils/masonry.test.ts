import { describe, expect, it } from 'vitest'
import { splitIntoColumns } from './masonry'

describe('splitIntoColumns', () => {
  it('按索引奇偶交替分列', () => {
    expect(splitIntoColumns([1, 2, 3, 4, 5], 2)).toEqual([[1, 3, 5], [2, 4]])
  })

  it('支持 3 列', () => {
    expect(splitIntoColumns(['a', 'b', 'c', 'd'], 3)).toEqual([['a', 'd'], ['b'], ['c']])
  })

  it('追加新项时已有项位置不变', () => {
    const first = splitIntoColumns([1, 2, 3, 4], 2)
    const appended = splitIntoColumns([1, 2, 3, 4, 5, 6], 2)
    expect(appended[0].slice(0, 2)).toEqual(first[0])
    expect(appended[1].slice(0, 2)).toEqual(first[1])
  })

  it('列数非法时回落单列，空列表返回对应列数的空数组', () => {
    expect(splitIntoColumns([1, 2], 0)).toEqual([[1, 2]])
    expect(splitIntoColumns([], 2)).toEqual([[], []])
  })
})
