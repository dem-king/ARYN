import { describe, expect, it } from 'vitest'
import {
  applyRouteOrder,
  buildPickRows,
  groupPickRows,
  isPickRowDone,
  moveItem,
  resolveTargetIndex,
  sortRowOffset,
} from './delivery-pick'

/**
 * 配货汇总与路线排序的口径守卫。
 *
 * 这些函数决定司机「拿多少货、按什么顺序送」，算错不会报错：
 * 合并少了就是漏拿货，顺序错了就是绕路。所以用单测钉死口径。
 */
describe('buildPickRows', () => {
  it('把整趟车所有订单的同款同规格合并为一行，数量求和', () => {
    const rows = buildPickRows([
      { itemList: [{ id: 'i1', spuName: '矿泉水', skuName: '550ml', quantity: 2, picked: '1' }] },
      { itemList: [{ id: 'i2', spuName: '矿泉水', skuName: '550ml', quantity: 3, picked: '0' }] },
    ])

    expect(rows).toHaveLength(1)
    expect(rows[0].quantity).toBe(5)
    // 逐项确认取货的粒度仍是明细，汇总行只做合计展示
    expect(rows[0].itemIds).toEqual(['i1', 'i2'])
    expect(rows[0].pickedCount).toBe(1)
    expect(isPickRowDone(rows[0])).toBe(false)
  })

  it('同名不同规格不合并（司机照规格拿货）', () => {
    const rows = buildPickRows([
      { itemList: [{ id: 'i1', spuName: '矿泉水', skuName: '550ml', quantity: 2 }] },
      { itemList: [{ id: 'i2', spuName: '矿泉水', skuName: '1.5L', quantity: 1 }] },
    ])

    expect(rows).toHaveLength(2)
    expect(rows.map(row => row.specsInfo)).toEqual(['550ml', '1.5L'])
  })

  it('规格为空按空串参与聚合，不因缺失拆成两行', () => {
    const rows = buildPickRows([
      { itemList: [{ id: 'i1', spuName: '压缩饼干', quantity: 1 }] },
      { itemList: [{ id: 'i2', spuName: '压缩饼干', skuName: '', quantity: 2 }] },
    ])

    expect(rows).toHaveLength(1)
    expect(rows[0].quantity).toBe(3)
  })

  it('不同分类的同名商品不合并（分类≈供应商批次）', () => {
    const rows = buildPickRows([
      { itemList: [{ id: 'i1', spuName: '毛巾', categoryName: '百货', quantity: 1 }] },
      { itemList: [{ id: 'i2', spuName: '毛巾', categoryName: '日化', quantity: 1 }] },
    ])

    expect(rows).toHaveLength(2)
  })

  it('图片取第一个有值的明细，避免合并后丢图', () => {
    const rows = buildPickRows([
      { itemList: [{ id: 'i1', spuName: '毛巾', quantity: 1 }] },
      { itemList: [{ id: 'i2', spuName: '毛巾', image: 'https://img/towel.png', quantity: 1 }] },
    ])

    expect(rows[0].picUrl).toBe('https://img/towel.png')
  })

  it('空任务与空明细返回空汇总', () => {
    expect(buildPickRows([])).toEqual([])
    expect(buildPickRows([{}, { itemList: [] }])).toEqual([])
  })
})

describe('groupPickRows', () => {
  it('按分类分组，未回填分类归「未分类」兜底组', () => {
    const rows = buildPickRows([
      { itemList: [{ id: 'i1', spuName: 'A', categoryName: '蔬菜', quantity: 1 }] },
      { itemList: [{ id: 'i2', spuName: 'B', quantity: 1 }] },
      { itemList: [{ id: 'i3', spuName: 'C', categoryName: '蔬菜', quantity: 1 }] },
    ])

    const groups = groupPickRows(rows)

    expect(groups.map(group => group.categoryName)).toEqual(['蔬菜', '未分类'])
    expect(groups[0].rows).toHaveLength(2)
  })
})

describe('isPickRowDone', () => {
  it('全部明细确认取货才算汇总行完成', () => {
    expect(isPickRowDone({ key: '', categoryName: '', spuName: '', specsInfo: '', picUrl: '', quantity: 1, itemIds: ['a'], pickedCount: 1 })).toBe(true)
    expect(isPickRowDone({ key: '', categoryName: '', spuName: '', specsInfo: '', picUrl: '', quantity: 1, itemIds: ['a', 'b'], pickedCount: 1 })).toBe(false)
    // 没有明细的行（不该出现）不能算完成，否则进度会凭空跳满
    expect(isPickRowDone({ key: '', categoryName: '', spuName: '', specsInfo: '', picUrl: '', quantity: 0, itemIds: [], pickedCount: 0 })).toBe(false)
  })
})

describe('applyRouteOrder', () => {
  it('按给定顺序排列，未列出的站点追加末尾', () => {
    const tasks = [{ id: 'a' }, { id: 'b' }, { id: 'c' }]

    expect(applyRouteOrder(tasks, ['c', 'a', 'b']).map(t => t.id)).toEqual(['c', 'a', 'b'])
    // 旧快照只交了部分顺序：缺的不能被丢掉
    expect(applyRouteOrder(tasks, ['c']).map(t => t.id)).toEqual(['c', 'a', 'b'])
    expect(applyRouteOrder(tasks, []).map(t => t.id)).toEqual(['a', 'b', 'c'])
  })

  it('顺序表里的未知ID被忽略', () => {
    const tasks = [{ id: 'a' }, { id: 'b' }]

    expect(applyRouteOrder(tasks, ['zzz', 'b', 'a']).map(t => t.id)).toEqual(['b', 'a'])
  })
})

describe('moveItem', () => {
  it('把元素移到目标位置，其余保持相对顺序', () => {
    expect(moveItem(['a', 'b', 'c', 'd'], 0, 2)).toEqual(['b', 'c', 'a', 'd'])
    expect(moveItem(['a', 'b', 'c', 'd'], 3, 0)).toEqual(['d', 'a', 'b', 'c'])
  })

  it('越界落点收敛到两端，非法起点返回原顺序', () => {
    expect(moveItem(['a', 'b', 'c'], 2, 99)).toEqual(['a', 'b', 'c'])
    expect(moveItem(['a', 'b', 'c'], 1, -5)).toEqual(['b', 'a', 'c'])
    expect(moveItem(['a', 'b', 'c'], 9, 0)).toEqual(['a', 'b', 'c'])
  })
})

describe('resolveTargetIndex', () => {
  it('按固定行高把位移换算成目标槽位', () => {
    // 行高 100：往下拖 210px 跨过两行
    expect(resolveTargetIndex(0, 210, 100, 5)).toBe(2)
    expect(resolveTargetIndex(3, -150, 100, 5)).toBe(2)
    // 位移不足半行不换位
    expect(resolveTargetIndex(2, 40, 100, 5)).toBe(2)
  })

  it('落点收敛在列表范围内', () => {
    expect(resolveTargetIndex(0, -500, 100, 3)).toBe(0)
    expect(resolveTargetIndex(2, 500, 100, 3)).toBe(2)
    // 行高或列表异常时保持原位，不产生越界下标
    expect(resolveTargetIndex(1, 100, 0, 3)).toBe(1)
    expect(resolveTargetIndex(0, 100, 100, 0)).toBe(0)
  })
})

describe('sortRowOffset', () => {
  it('被拖的行跟手位移', () => {
    expect(sortRowOffset(1, 1, 3, 100, 88)).toBe(88)
  })

  it('下拖时中间行整体上移一格让位', () => {
    // 把 0 拖到 2：原本第 1、2 行各上移一行
    expect(sortRowOffset(1, 0, 2, 100, 0)).toBe(-100)
    expect(sortRowOffset(2, 0, 2, 100, 0)).toBe(-100)
    // 目标行之后的、以及被拖行之前的都不动
    expect(sortRowOffset(3, 0, 2, 100, 0)).toBe(0)
  })

  it('上拖时中间行整体下移一格让位', () => {
    expect(sortRowOffset(1, 3, 1, 100, 0)).toBe(100)
    expect(sortRowOffset(2, 3, 1, 100, 0)).toBe(100)
    expect(sortRowOffset(0, 3, 1, 100, 0)).toBe(0)
  })

  it('未发生跨行位移时所有行都不动', () => {
    expect(sortRowOffset(1, 2, 2, 100, 30)).toBe(0)
  })
})
