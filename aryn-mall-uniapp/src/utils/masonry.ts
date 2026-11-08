/**
 * 瀑布流分列：按索引奇偶交替入列（0→第一列、1→第二列…）。
 *
 * 相比 CSS column-count 的「先填满一列再填下一列」，交替分列是真正的左右交替，
 * 且翻页追加时新项继续交替入列，已有卡片不会因为重新平衡列而跳位。
 */
export function splitIntoColumns<T>(items: T[], columnCount: number): T[][] {
  const count = Math.max(1, Math.floor(columnCount) || 1)
  const columns: T[][] = Array.from({ length: count }, () => [])
  items.forEach((item, index) => {
    columns[index % count].push(item)
  })
  return columns
}
