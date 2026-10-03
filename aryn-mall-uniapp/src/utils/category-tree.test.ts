import type { CategoryTreeNode } from './category-tree'

import { describe, expect, it } from 'vitest'

import { filterActiveCategoryTree, locateCategoryInTree } from './category-tree'

/**
 * 导航展示位的停用过滤契约。
 *
 * 后端 tree 接口把 status 塞在节点上原样下发（停用也下发），
 * 过滤必须在客户端完成——见 api/product/category.ts 的 getActiveTree。
 */
describe('filterActiveCategoryTree', () => {
  const tree = [
    {
      children: [
        { id: 's1', name: '启用二级', status: '0' },
        { id: 's2', name: '停用二级', status: '1' },
        { id: 's3', name: '无状态二级' },
      ],
      id: 'f1',
      name: '启用一级',
      status: '0',
    },
    { id: 'f2', name: '停用一级', status: '1', children: [{ id: 's4', name: '其下二级', status: '0' }] },
    { id: 'f3', name: '无状态一级（视为启用）' },
  ]

  it('一级停用项整支剔除（含其子级）', () => {
    const result = filterActiveCategoryTree(tree)
    expect(result.map(node => node.id)).toEqual(['f1', 'f3'])
  })

  it('二级停用项剔除，启用与无状态保留', () => {
    const result = filterActiveCategoryTree(tree)
    expect(result[0]?.children).toEqual([
      { id: 's1', name: '启用二级', status: '0' },
      { id: 's3', name: '无状态二级' },
    ])
  })

  it('其余字段原样保留（badgeType/sort 等被 extra 拍平在节点上）', () => {
    const node = {
      badgeType: '2',
      children: [],
      id: 'x',
      name: '热卖分类',
      sort: 3,
      status: '0',
    }
    const result = filterActiveCategoryTree([node])
    expect(result).toEqual([node])
  })

  it('children 缺省时输出为空数组而非 undefined', () => {
    const result = filterActiveCategoryTree<CategoryTreeNode>([
      { id: 'leaf', name: '叶子' },
    ])
    expect(result[0]?.children).toEqual([])
  })
})

/**
 * 「全部分类」跨页定位的 id → 下标翻译契约。
 *
 * 商品列表页跳分类页（switchTab 带不了 query）后，分类页用这个函数把
 * store 中转来的目标分类翻成左栏/侧栏选中项，见 pages/product/category/index.vue。
 */
describe('locateCategoryInTree', () => {
  const tree: CategoryTreeNode[] = [
    { id: 'f1', children: [{ id: 's1' }, { id: 's2' }] },
    { id: 'f2', children: [{ id: 's3' }] },
    { id: 'f3' },
  ]

  it('二级 id 命中：同时返回一级与二级下标', () => {
    expect(locateCategoryInTree(tree, { categorySecondId: 's3' })).toEqual({ firstIndex: 1, secondIndex: 0 })
  })

  it('一级 id 命中：二级下标为 -1，由调用方落到该一级下第一项', () => {
    expect(locateCategoryInTree(tree, { categoryFirstId: 'f2' })).toEqual({ firstIndex: 1, secondIndex: -1 })
  })

  it('二级命中优先于一级（两个 id 同时携带且不一致时，以更精确的二级为准）', () => {
    expect(locateCategoryInTree(tree, { categoryFirstId: 'f1', categorySecondId: 's3' })).toEqual({ firstIndex: 1, secondIndex: 0 })
  })

  it('无 children 的一级节点排在目标前也不影响二级命中', () => {
    const childlessFirst: CategoryTreeNode[] = [{ id: 'f0' }, ...tree]
    expect(locateCategoryInTree(childlessFirst, { categorySecondId: 's1' })).toEqual({ firstIndex: 1, secondIndex: 0 })
  })

  it('都未命中（已删/停用被过滤）返回 -1/-1', () => {
    expect(locateCategoryInTree(tree, { categoryFirstId: 'gone' })).toEqual({ firstIndex: -1, secondIndex: -1 })
    expect(locateCategoryInTree(tree, { categorySecondId: 'gone' })).toEqual({ firstIndex: -1, secondIndex: -1 })
    expect(locateCategoryInTree(tree, {})).toEqual({ firstIndex: -1, secondIndex: -1 })
  })

  it('id 数字与字符串互通（树上 id 类型不保证一致）', () => {
    const numericTree = [{ id: 1, children: [{ id: 11 }] }] as CategoryTreeNode[]
    expect(locateCategoryInTree(numericTree, { categorySecondId: '11' })).toEqual({ firstIndex: 0, secondIndex: 0 })
    expect(locateCategoryInTree(numericTree, { categoryFirstId: '1' })).toEqual({ firstIndex: 0, secondIndex: -1 })
  })
})
