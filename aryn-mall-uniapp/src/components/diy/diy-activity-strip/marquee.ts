/**
 * 活动入口条 / 公告条跑马灯的纯计算口径。
 *
 * 入口条一行最多三张卡，卡内还要放缩略图、角标与箭头，活动名/钩子只有
 * 百来 rpx 宽。运营把真实活动名（如「农夫山泉天然水整箱」）接进钩子后，
 * 单行省略会把信息吃掉；这里统一为「不换行、溢出才滚动」：
 * · 只有文本真实宽度超过容器宽度才启动，短文案保持静止，避免无意义晃动；
 * · 双份文本 + translateX(-50%) 无缝循环，轨道宽度必须显式算出来；
 * · 速度按 px/s 折算时长，长文案不会因为更长而跑得更快。
 *
 * 公告条是「恒定跑马灯」：文案不超宽也要持续滚动（运营配置了滚动速度），
 * 因此额外提供 ticker 口径——按容器宽度重复偶数份文案，滚动时可视区始终有字。
 *
 * 抽成纯函数是为了能单测钉死阈值、轨道宽度与时长，组件只负责测量与挂类。
 */

/** 两份文本之间的留白（px），避免首尾相接时糊成一句 */
export const MARQUEE_GAP_PX = 12

/** 跑马灯最少重复份数：双份才能用位移 -50% 无缝循环 */
export const MARQUEE_MIN_COPIES = 2

/** 滚动速度（px/s）：20rpx 字号下约每秒 3 个字，保证可读 */
export const MARQUEE_SPEED_PX_PER_SEC = 28

/** 最短循环时长（秒）：短文案也不至于一闪而过 */
export const MARQUEE_MIN_DURATION_SEC = 2.4

/** 溢出判定容差（px）：吸收 boundingClientRect 的亚像素误差 */
export const MARQUEE_OVERFLOW_EPSILON_PX = 1

function toNonNegative(value: number): number {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : 0
}

/** 轨道只会用偶数份（-50% 位移才落在整份上）；非法值回落到双份 */
function normalizeCopies(copies: number): number {
  if (!Number.isInteger(copies) || copies < MARQUEE_MIN_COPIES)
    return MARQUEE_MIN_COPIES
  return copies % 2 === 0 ? copies : copies + 1
}

/** 文本宽度是否真的超过容器：只多出亚像素时不滚动，免得来回抖 */
export function isMarqueeNeeded(
  contentWidth: number,
  viewportWidth: number,
): boolean {
  const content = toNonNegative(contentWidth)
  const viewport = toNonNegative(viewportWidth)
  if (!content || !viewport)
    return false
  return content > viewport + MARQUEE_OVERFLOW_EPSILON_PX
}

/**
 * 恒定跑马灯的文案重复份数（公告条这类「不超宽也要滚动」的场景）。
 *
 * 动画位移是轨道的一半（-50%），所以份数必须是偶数，接缝才落在一份的起点；
 * 同时半条轨道要够宽：`copies / 2 × 单元宽 ≥ 容器宽`，滚动过程中可视区
 * 才不会露出空白。
 */
export function marqueeCopies(
  contentWidth: number,
  viewportWidth: number,
  gap = MARQUEE_GAP_PX,
): number {
  const content = toNonNegative(contentWidth)
  const viewport = toNonNegative(viewportWidth)
  if (!content || !viewport)
    return MARQUEE_MIN_COPIES
  const unit = content + toNonNegative(gap)
  const needed = Math.ceil((viewport * 2) / unit)
  const even = needed % 2 === 0 ? needed : needed + 1
  return Math.max(MARQUEE_MIN_COPIES, even)
}

/**
 * 滚动轨道宽度（px）。
 *
 * 轨道里放 `copies` 份相同文本，每份右侧留 `gap` 空白；位移到 -50% 时
 * 第 `copies / 2 + 1` 份恰好落到第一份的起点，形成无缝循环。
 */
export function marqueeTrackWidth(
  contentWidth: number,
  gap = MARQUEE_GAP_PX,
  copies = MARQUEE_MIN_COPIES,
): number {
  return (toNonNegative(contentWidth) + toNonNegative(gap)) * normalizeCopies(copies)
}

/** 单圈滚动时长（秒）：位移距离 / 速度，并设最短时长；结果保留两位小数 */
export function marqueeDuration(
  contentWidth: number,
  gap = MARQUEE_GAP_PX,
  speed = MARQUEE_SPEED_PX_PER_SEC,
  copies = MARQUEE_MIN_COPIES,
): number {
  const distance = (toNonNegative(contentWidth) + toNonNegative(gap)) * (normalizeCopies(copies) / 2)
  const safeSpeed = toNonNegative(speed) || MARQUEE_SPEED_PX_PER_SEC
  const duration = Math.max(MARQUEE_MIN_DURATION_SEC, distance / safeSpeed)
  return Math.round(duration * 100) / 100
}
