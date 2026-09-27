/**
 * 首页「领券」组件（showStyle=1 横向列表）的卡片宽度。
 *
 * 这里要同时躲开两个坑，两者都是线上实际出现过的：
 *   - 卡片 `flex: 1` 无上限 → 只剩 1 张券时被拉成整屏宽的横幅，金额区被拉扯变形；
 *   - 卡片写死固定宽度 → 只剩 1~2 张券时右侧留出大片空白，像是没渲染完。
 *
 * 规则：券数达到运营配置的展示数量时按配置均分；不足时让这几张长大把整行填满，
 * 但单张不超过半行，避免重新变成横幅。半行放不下时给最小宽度兜底并横向滚动。
 */

/** 单张券最大占行宽比例：超过半行就会显出「被拉扯铺满」的观感 */
const MAX_CARD_SHARE = 0.5

/** 卡片最小宽度（rpx）：与后台设计器预览的 110px 卡片对齐，避免配置大数量时挤成细条 */
const MIN_CARD_WIDTH_RPX = 220

/** 必须与 .coupon-list.list-style 的 gap 一致 */
const LIST_GAP_RPX = 16

export interface CouponCardLayout {
  /** 卡片内联样式 */
  style: Record<string, string>
  /** 卡片组是否铺满整行；未铺满时整行居中，避免空白全堆在右侧 */
  fillsRow: boolean
}

function trim(value: number) {
  return Number(value.toFixed(4))
}

export function couponCardLayout(count: number, showNum: number): CouponCardLayout {
  const visible = Math.max(1, Math.floor(count) || 0)
  const perRow = Math.max(1, Math.floor(showNum) || 1)
  const share = visible >= perRow
    ? 1 / perRow
    : Math.min(1 / visible, MAX_CARD_SHARE)
  // 均分时 gap 由这几张券共同承担，否则宽度之和会超出 100% 撑出横向滚动条
  const gapPerCard = trim(LIST_GAP_RPX * (visible - 1) / visible)
  const width = trim(share * 100)
  return {
    style: {
      flex: '0 0 auto',
      // 单张券时 gap 为 0；不能写成 calc(50% - 0rpx)，那会退化出裸 0
      // （calc 里单位无关的 0 是 number 而非 length，整条 width 会被浏览器丢弃）
      width: gapPerCard > 0 ? `calc(${width}% - ${gapPerCard}rpx)` : `${width}%`,
      minWidth: `${MIN_CARD_WIDTH_RPX}rpx`,
    },
    fillsRow: trim(share * visible) >= 1,
  }
}
