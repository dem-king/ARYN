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
import type { SharedCart, SharedCartSummary } from '@/api/order/sharedCart'

import { formatExpiryCountdown, remainingMsUntil } from '@/utils/vessel-call-time'

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
  if (!status)
    return ''
  return CART_STATUS_LABEL[status] ?? '未知状态'
}

export function cartStatusTheme(status?: null | string) {
  return CART_STATUS_THEME[status ?? ''] ?? { bg: '#F1EFE8', text: '#5F5E5A' }
}

export function memberRoleLabel(role?: null | string): string {
  if (!role)
    return ''
  return MEMBER_ROLE_LABEL[role] ?? '成员'
}

/** 仅「收集中」的购物车允许继续加购与编辑明细 */
export function isCartCollecting(status?: null | string): boolean {
  return status === CART_STATUS_COLLECTING
}

/** 已提交、已关闭或已完成的购物车不可编辑 */
export function isCartReadonly(status?: null | string): boolean {
  return (
    status === CART_STATUS_SUBMITTED
    || status === CART_STATUS_CLOSED
    || status === CART_STATUS_COMPLETED
  )
}

/**
 * 从「进行中摘要」里挑出可作为快捷加购目的地的购物车。
 *
 * 服务端 active-summary 已按「我是成员 + 收集中 + 未过期」过滤，这里再防御一次：
 * - 状态不是收集中（理论不会出现）不放行；
 * - viewerCanEdit=false（成员编辑权被收回）不放行，避免用户在弹层里选了共享车
 *   却被服务端拒绝，白弹一次。
 */
export function pickActiveCart(summary?: SharedCartSummary | null): SharedCart | null {
  const cart = summary?.cart
  if (!cart)
    return null
  if (!isCartCollecting(cart.status))
    return null
  if (cart.viewerCanEdit === false)
    return null
  return cart
}

/** 购物车位置文案：港口 + 泊位，缺失时回落船名（与首页补给单卡片同口径） */
export function sharedCartLocationLabel(cart?: SharedCart | null): string {
  if (!cart)
    return ''
  const parts = [cart.portName, cart.berth].filter(Boolean)
  if (parts.length > 0)
    return parts.join(' ')
  return cart.vesselName || ''
}

/* -------------------------------------------------------------------------
 * 列表页分区与排序
 *
 * 清单本身是一串平铺记录，但用户打开列表时要回答的问题只有一个：
 * 「现在有没有还需要我管的单」。因此按「是否已结束」分区，
 * 而不是按六个状态枚举平铺——后者会把已关闭的历史单和收集中混在一起，
 * 四张卡片长得一模一样，用户只能逐张点进去看。
 *
 * 刻意不做「待我确认」这个分区：后端 status=3（待确认）目前没有任何
 * 写入路径，只有查询在用。为它单独分一个区会让空分区永远不出现、
 * 而读者以为它有意义。等真正的状态流转落地后再加。
 * ---------------------------------------------------------------------- */

/** 列表分区：进行中（还能动）与历史（只看不写） */
export type SharedCartSectionKey = 'active' | 'history'

export interface SharedCartSection {
  key: SharedCartSectionKey
  /** 分区标题 */
  title: string
  /** 一句话说明这个分区在等什么，避免用户逐张卡片猜 */
  hint: string
  carts: SharedCart[]
}

/** 尚未结束的购物车：草稿与收集中都还能继续加购 */
function isActiveStatus(status?: null | string): boolean {
  return status === CART_STATUS_DRAFT || status === CART_STATUS_COLLECTING
}

/**
 * 收集窗口是否已过（仅对收集中有意义）。
 *
 * 截止时间缺失或无法解析时按「未过期」处理：宁可让用户点进去看到服务端
 * 的提示，也不要因为本地解析失败把仍在收集的单误标成已过期。
 */
export function isCartExpired(cart: SharedCart, now: Date = new Date()): boolean {
  if (!isCartCollecting(cart.status))
    return false
  const remaining = remainingMsUntil(cart.expiresAt, now)
  return remaining !== null && remaining <= 0
}

/**
 * 列表分区。
 *
 * 顺序固定为「进行中 → 历史」：用户打开这个页面的目的是继续采购，
 * 已结束的单只是留档。历史为空时整段不出现在界面上。
 */
export function groupSharedCarts(carts: SharedCart[], now: Date = new Date()): SharedCartSection[] {
  const active: SharedCart[] = []
  const history: SharedCart[] = []

  for (const cart of carts) {
    if (isActiveStatus(cart.status))
      active.push(cart)
    else
      history.push(cart)
  }

  // 收集中的单离截止越近越要紧，排前面；其余保持服务端的创建时间倒序
  active.sort((a, b) => deadlineRank(a, now) - deadlineRank(b, now))

  return [
    { key: 'active', title: '进行中', hint: '还没有提交，截止前随时可以加购', carts: active },
    { key: 'history', title: '历史', hint: '已提交或已关闭的单，仅供查阅', carts: history },
  ].filter(section => section.carts.length > 0) as SharedCartSection[]
}

/**
 * 收集中的排序权重：已过期排最后（服务端会兜底关单，但仍应让用户先看到能动的单），
 * 其余按截止时间升序，无法解析截止时间的排在最后。
 */
function deadlineRank(cart: SharedCart, now: Date): number {
  if (isCartExpired(cart, now))
    return Number.MAX_SAFE_INTEGER
  const remaining = remainingMsUntil(cart.expiresAt, now)
  return remaining === null ? Number.MAX_SAFE_INTEGER - 1 : remaining
}

/**
 * 卡片上的截止提示：倒计时 + 是否紧急。
 *
 * 只在收集中且有截止时间时给出；不足 1 小时转警示色，与详情页同一分档
 * （复用 formatExpiryCountdown，不在这里另写一套时间换算）。
 */
export function cartDeadlineView(
  cart: SharedCart,
  now: Date = new Date(),
): null | { expired: boolean, text: string, urgent: boolean } {
  if (!isCartCollecting(cart.status) || !cart.expiresAt)
    return null
  if (isCartExpired(cart, now))
    return { text: '已过截止时间', urgent: true, expired: true }
  const text = formatExpiryCountdown(cart.expiresAt, now)
  if (!text)
    return null
  return {
    text,
    urgent: text === '即将截止' || /还剩 \d+ 分/.test(text),
    expired: false,
  }
}

/**
 * 卡片主操作文案。
 *
 * 卡片上原来一律写「查看」，四张卡看不出差别：收集中该加货的单和已提交的
 * 归档单点进去要做的事完全不同。这里按状态与查看者权限给出动词，
 * 让用户不点进去就知道这一步会发生什么。
 *
 * 权限只信服务端下发的 viewer* 标记，前端不按 userId 猜。
 */
export function cartActionLabel(cart: SharedCart, now: Date = new Date()): string {
  if (cart.submitOrderId)
    return '查看订单'
  if (isCartCollecting(cart.status)) {
    if (isCartExpired(cart, now))
      return '查看'
    if (cart.viewerCanConfirm === true)
      return '排计划'
    if (cart.viewerCanEdit === true)
      return '去加货'
    return '查看'
  }
  if (cart.status === CART_STATUS_DRAFT)
    return cart.viewerCanEdit === true ? '去加货' : '查看'
  return '查看'
}
