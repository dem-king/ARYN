/**
 * 共享购物车状态与角色文案的单一来源。
 *
 * 后端取值：
 * - 购物车状态 status：1草稿 2收集中 3待确认 4已提交 5已关闭 6已完成（送达归档）
 * - 成员角色 memberRole：1发起人 2成员 3确认人
 *
 * 约束：页面禁止自行用三元链兜底，一律经 cartStatusLabel() / memberRoleLabel() 获取，
 * 避免出现未知取值被静默显示成某个具体状态。
 */

/** 1 草稿 */
export const CART_STATUS_DRAFT = '1'
/** 2 收集中 */
export const CART_STATUS_COLLECTING = '2'
/** 3 待确认 */
export const CART_STATUS_WAITING_CONFIRM = '3'
/** 4 已提交 */
export const CART_STATUS_SUBMITTED = '4'
/** 5 已关闭 */
export const CART_STATUS_CLOSED = '5'
/** 6 已完成（订单送达后归档） */
export const CART_STATUS_COMPLETED = '6'

export const CART_STATUS_LABEL: Record<string, string> = {
  [CART_STATUS_DRAFT]: '草稿',
  [CART_STATUS_COLLECTING]: '收集中',
  [CART_STATUS_WAITING_CONFIRM]: '待确认',
  [CART_STATUS_SUBMITTED]: '已提交',
  [CART_STATUS_CLOSED]: '已关闭',
  [CART_STATUS_COMPLETED]: '本次采购已送达',
}

/** 状态主题色（浅底深字，用于标签） */
export const CART_STATUS_THEME: Record<string, { bg: string, text: string }> = {
  [CART_STATUS_DRAFT]: { bg: '#F1EFE8', text: '#5F5E5A' },
  [CART_STATUS_COLLECTING]: { bg: '#E6F1FB', text: '#185FA5' },
  [CART_STATUS_WAITING_CONFIRM]: { bg: '#FAEEDA', text: '#854F0B' },
  [CART_STATUS_SUBMITTED]: { bg: '#E1F5EE', text: '#0F6E56' },
  [CART_STATUS_CLOSED]: { bg: '#F1EFE8', text: '#888780' },
  [CART_STATUS_COMPLETED]: { bg: '#E1F5EE', text: '#0F6E56' },
}

/** 1 发起人 */
export const MEMBER_ROLE_OWNER = '1'
/** 2 成员 */
export const MEMBER_ROLE_MEMBER = '2'
/** 3 确认人 */
export const MEMBER_ROLE_CONFIRMATOR = '3'

export const MEMBER_ROLE_LABEL: Record<string, string> = {
  [MEMBER_ROLE_OWNER]: '发起人',
  [MEMBER_ROLE_MEMBER]: '成员',
  [MEMBER_ROLE_CONFIRMATOR]: '确认人',
}

export function cartStatusLabel(status?: null | string): string {
  if (!status) return ''
  return CART_STATUS_LABEL[status] ?? '未知状态'
}

export function cartStatusTheme(status?: null | string) {
  return CART_STATUS_THEME[status ?? ''] ?? { bg: '#F1EFE8', text: '#5F5E5A' }
}

export function memberRoleLabel(role?: null | string): string {
  if (!role) return ''
  return MEMBER_ROLE_LABEL[role] ?? '成员'
}

/** 仅「收集中」的购物车允许继续加购与编辑明细 */
export function isCartCollecting(status?: null | string): boolean {
  return status === CART_STATUS_COLLECTING
}

/** 已提交、已关闭或已完成的购物车不可编辑 */
export function isCartReadonly(status?: null | string): boolean {
  return (
    status === CART_STATUS_SUBMITTED ||
    status === CART_STATUS_CLOSED ||
    status === CART_STATUS_COMPLETED
  )
}
