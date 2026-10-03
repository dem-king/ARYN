/**
 * 配送方式（delivery_way）展示文案的单一来源。
 *
 * 后端取值：
 *   1 普通快递
 *   2 上门自提
 *   3 商城配送
 *   4 公司港口/船舶内部配送（船供采购强制使用，由公司司机按靠港计划送达）
 *
 * 约束：页面禁止再用 `way === '1' ? ... : way === '3' ? ... : '兜底'` 的三元链。
 * 历史上正是这种兜底写法把 way=4 显示成了「普通快递」「无需配送」「等待提货」
 * 三种互相矛盾的文案（结算页 / 订单详情配送方式 / 订单详情状态）。
 * 所有配送方式文案一律经 deliveryWayLabel() 获取。
 */

/** 普通快递 */
export const DELIVERY_WAY_EXPRESS = '1'
/** 上门自提 */
export const DELIVERY_WAY_SELF_PICKUP = '2'
/** 商城配送 */
export const DELIVERY_WAY_MALL_DELIVERY = '3'
/** 公司港口/船舶内部配送 */
export const DELIVERY_WAY_VESSEL_INTERNAL = '4'

export const DELIVERY_WAY_LABEL: Record<string, string> = {
  [DELIVERY_WAY_EXPRESS]: '普通快递',
  [DELIVERY_WAY_SELF_PICKUP]: '上门自提',
  [DELIVERY_WAY_MALL_DELIVERY]: '商城配送',
  [DELIVERY_WAY_VESSEL_INTERNAL]: '公司港口/船舶内部配送',
}

/**
 * 取配送方式展示文案。
 * 未识别取值返回「未知配送方式」而非静默兜底为普通快递，避免误导用户。
 */
export function deliveryWayLabel(way?: null | string): string {
  if (!way) return ''
  return DELIVERY_WAY_LABEL[way] ?? '未知配送方式'
}

/**
 * 结算页可选的配送方式。
 * 内部配送仅在当前已绑定船舶+靠港上下文时可选，否则用户选了也无法履约。
 */
export function deliveryWayOptions(options: { withVesselInternal?: boolean } = {}) {
  const base = [
    { value: DELIVERY_WAY_EXPRESS, name: DELIVERY_WAY_LABEL[DELIVERY_WAY_EXPRESS] },
    { value: DELIVERY_WAY_SELF_PICKUP, name: DELIVERY_WAY_LABEL[DELIVERY_WAY_SELF_PICKUP] },
    { value: DELIVERY_WAY_MALL_DELIVERY, name: DELIVERY_WAY_LABEL[DELIVERY_WAY_MALL_DELIVERY] },
  ]
  if (options.withVesselInternal) {
    base.unshift({
      value: DELIVERY_WAY_VESSEL_INTERNAL,
      name: DELIVERY_WAY_LABEL[DELIVERY_WAY_VESSEL_INTERNAL],
    })
  }
  return base
}
