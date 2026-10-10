/**
 * 出车单「配货汇总」与「路线排序」的纯计算口径。
 *
 * 司机的真实动作是：把一趟车里 N 个订单的货一次配齐、装车，再按顺序送。
 * 订单维度（一单一卡）的清单会让人在仓库里来回折返，所以配货视图要把
 * 整趟车的明细按「分类 → 商品 + 规格」合并成一张汇总单：同一件货只跑一趟，
 * 数量是所有订单的合计。
 *
 * 抽成纯函数是为了能单测：合并口径写错不会报错，只会让司机少拿货。
 */

/** 配货汇总行：一件货在一趟车里的合计 */
export interface PickRow {
  /** 聚合键（分类 + 商品 + 规格） */
  key: string
  /** 分类名（空串按「未分类」参与聚合与展示） */
  categoryName: string
  spuName: string
  specsInfo: string
  picUrl: string
  /** 跨订单合计数量 */
  quantity: number
  /** 归属的明细ID（逐项确认取货的粒度仍是明细） */
  itemIds: string[]
  /** 已确认取货的明细数 */
  pickedCount: number
}

/** 配货汇总分组：同名分类的汇总行聚在一起 */
export interface PickRowGroup {
  categoryName: string
  rows: PickRow[]
}

interface PickSourceTask {
  itemList?: Array<{
    id: string
    spuName?: string
    skuName?: string
    image?: string
    quantity?: number
    categoryName?: string
    picked?: string
  }>
}

/**
 * 把一趟车所有订单的明细合并成配货汇总行。
 *
 * 聚合键用分类 + 商品名 + 规格：同名不同规格必须分开（司机照规格拿货），
 * 规格为空按空串参与聚合，避免同一件货因「无规格」和「空规格」拆两行。
 */
export function buildPickRows(tasks: PickSourceTask[]): PickRow[] {
  const rows: PickRow[] = []
  const rowByKey = new Map<string, PickRow>()
  for (const task of tasks ?? []) {
    for (const item of task.itemList ?? []) {
      const categoryName = item.categoryName ?? ''
      const spuName = item.spuName ?? ''
      const specsInfo = item.skuName ?? ''
      const key = `${categoryName}\u0000${spuName}\u0000${specsInfo}`
      let row = rowByKey.get(key)
      if (!row) {
        row = {
          key,
          categoryName,
          spuName,
          specsInfo,
          picUrl: item.image ?? '',
          quantity: 0,
          itemIds: [],
          pickedCount: 0,
        }
        rowByKey.set(key, row)
        rows.push(row)
      }
      row.quantity += item.quantity ?? 0
      row.itemIds.push(item.id)
      if (item.picked === '1') {
        row.pickedCount += 1
      }
      // 图片取第一个有值的：不同订单可能只有部分明细带图
      if (!row.picUrl && item.image) {
        row.picUrl = item.image
      }
    }
  }
  return rows
}

/** 汇总行按分类分组，保持汇总行首次出现的顺序（分类≈供应商批次，按批次走仓库） */
export function groupPickRows(rows: PickRow[]): PickRowGroup[] {
  const groups: PickRowGroup[] = []
  const groupByName = new Map<string, PickRowGroup>()
  for (const row of rows) {
    const categoryName = row.categoryName || '未分类'
    let group = groupByName.get(categoryName)
    if (!group) {
      group = { categoryName, rows: [] }
      groupByName.set(categoryName, group)
      groups.push(group)
    }
    group.rows.push(row)
  }
  return groups
}

/** 汇总行的取货确认状态：全部确认才算完成 */
export function isPickRowDone(row: PickRow): boolean {
  return row.itemIds.length > 0 && row.pickedCount >= row.itemIds.length
}

/**
 * 按给定顺序排列站点；未出现在顺序表里的站点追加在末尾。
 *
 * 司机端可能基于旧快照提交顺序，缺项时不能让它们悄悄消失或顶到队首。
 */
export function applyRouteOrder<T extends { id: string }>(tasks: T[], orderedIds: string[]): T[] {
  const taskById = new Map(tasks.map(task => [task.id, task]))
  const ordered: T[] = []
  for (const id of orderedIds) {
    const task = taskById.get(id)
    if (task) {
      ordered.push(task)
      taskById.delete(id)
    }
  }
  for (const task of tasks) {
    if (taskById.has(task.id)) {
      ordered.push(task)
    }
  }
  return ordered
}

/**
 * 拖拽落点计算：把 from 位置的元素移动到 to 位置后的顺序。
 *
 * 越界落点收敛到两端；from 与 to 相同返回原顺序（调用方据此跳过网络提交）。
 */
export function moveItem<T>(items: T[], from: number, to: number): T[] {
  const list = [...items]
  if (from < 0 || from >= list.length) {
    return list
  }
  const target = Math.min(list.length - 1, Math.max(0, to))
  const [moved] = list.splice(from, 1)
  list.splice(target, 0, moved)
  return list
}

/**
 * 按下位置换算成目标槽位：行高固定时，位移跨过几行就往那个方向让几格。
 *
 * 排序列表用固定行高（避免变高卡片让落点判定漂移），因此这里只需行高与位移。
 */
export function resolveTargetIndex(fromIndex: number, deltaY: number, rowHeight: number, total: number): number {
  if (total <= 0 || rowHeight <= 0) {
    return Math.max(0, fromIndex)
  }
  const shift = Math.round(deltaY / rowHeight)
  return Math.min(total - 1, Math.max(0, fromIndex + shift))
}

/**
 * 排序态下每一行的位移：被拖的行跟手，其余行让出空位。
 *
 * 让位规则是「被拖行跨过谁，谁就往反方向退一格」，视觉上等价于实时插入。
 */
export function sortRowOffset(
  index: number,
  fromIndex: number,
  toIndex: number,
  rowHeight: number,
  dragOffset: number,
): number {
  if (index === fromIndex) {
    return dragOffset
  }
  if (fromIndex < toIndex && index > fromIndex && index <= toIndex) {
    return -rowHeight
  }
  if (fromIndex > toIndex && index >= toIndex && index < fromIndex) {
    return rowHeight
  }
  return 0
}
