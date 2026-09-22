/**
 * 共享购物车 API（同船多海员分别加购，授权人员统一确认提交整船订单）。
 *
 * 后端契约（AppSharedCartController，C 端网关域 mall-order）：
 * - 创建后绑定船舶与靠港计划，不可变更；已提交/已关闭不可编辑
 * - 成员只能维护自己的明细；发起人可邀请成员、关闭购物车
 * - 确认人（默认发起人）可调整核定数量后统一提交，按购物车维度幂等
 */
import { alovaInstance } from '@/api/core/instance'

export interface SharedCart {
  id: string
  cartNo: string
  vesselId: string
  vesselName?: string
  vesselCallId: string
  portCode?: string
  portName?: string
  berth?: string
  deliveryWindowStart?: string
  deliveryWindowEnd?: string
  ownerUserId: string
  confirmerUserId: string
  /** 1草稿 2收集中 3待确认 4已提交 5已关闭 6已完成（送达归档） */
  status: string
  expiresAt?: string
  submitOrderId?: string
  submittedTime?: string
  remark?: string
  createTime?: string
  /** 查看者角色：1发起人 2成员 3确认人 */
  viewerRole?: string
  viewerCanEdit?: boolean
  viewerCanConfirm?: boolean
  viewerIsOwner?: boolean
  itemCount?: number
  memberCount?: number
  /** 仅创建接口返回：命中了该船已有的收集中购物车（被复用的实例，非新建） */
  adoptedExisting?: boolean
  /** 分享令牌（发起人可生成，随购物车过期失效） */
  shareToken?: string
}

export interface SharedCartMember {
  id: string
  cartId: string
  userId: string
  /** 1发起人 2成员 3确认人 */
  memberRole: string
  /** 成员展示姓名（加入时填写，配送贴标签用） */
  displayName?: string
  canEdit: string
  canConfirm: string
  joinedTime?: string
}

export interface SharedCartItem {
  id: string
  cartId: string
  userId: string
  spuId: string
  skuId: string
  requestedQuantity: number
  approvedQuantity?: number
  /**
   * 计划采购量（采购单位）；null/undefined 表示未设计划。
   *
   * 未设计划的行不参与进度统计，也不能回落成 requestedQuantity ——
   * 「成员报的需求量」与「本次计划采购多少」不是一回事（77 号脚本加列）。
   */
  plannedQuantity?: number | null
  /** 已采量（采购单位，收集期间由确认人回填）；存量行为 null 时按 0 展示 */
  fulfilledQuantity?: number | null
  memberRemark?: string
  /** 1待确认 2已确认 3已移除 */
  status: string
  createTime?: string
}

/** 单行进度：与后端 ReplenishProgressVO 同口径（未设计划时只剩 plannedQuantity 为空） */
export interface ReplenishRowProgress {
  plannedQuantity?: number | null
  fulfilledQuantity: number
  remainingQuantity?: number | null
  completed?: boolean | null
}

/** 整单进度：按项数算百分比，与数量单位无关 */
export interface ReplenishSummaryProgress {
  totalItems: number
  plannedItems: number
  fulfilledItems: number
  remainingItems: number
  unplannedItems: number
  /** 无任何计划时为 null -> 前端显示「—」，不是 0% */
  progressPercent?: number | null
  totalAmount: number
}

export interface SharedCartCreatePayload {
  vesselId: string
  vesselCallId: string
  expiresAt?: string
  remark?: string
}

export interface SharedCartItemPayload {
  spuId?: string
  skuId: string
  requestedQuantity: number
  memberRemark?: string
}

export interface SharedCartConfirmPayload {
  approvedQuantities?: Array<{ itemId: string, quantity: number }>
  recipientName?: string
  recipientPhone?: string
  agentName?: string
  agentPhone?: string
  remark?: string
}

/** 首页「今日补给单」卡片摘要（服务端一次聚合，避免首屏串行多请求） */
export interface SharedCartSummary {
  cart: SharedCart | null
  itemCount: number
  memberCount: number
  totalAmount: number
  /** 进度汇总（未设计划的行只计入 unplannedItems，不参与百分比） */
  progress?: ReplenishSummaryProgress | null
  previewItems: Array<{
    itemId: string
    spuId: string
    skuId: string
    spuName?: string
    specsInfo?: string
    quantity: number
    /** 计划量；null 表示未设计划 */
    plannedQuantity?: number | null
    /** 已采量 */
    fulfilledQuantity?: number | null
    /** 还差量（未设计划时为 null） */
    remainingQuantity?: number | null
    /** 是否已采满（未设计划时为 null） */
    completed?: boolean | null
    picUrl?: string
    amount: number
  }>
  previewTruncated: boolean
}

const BASE = '/mall-order/app/shared-cart'

/** 我参与的全部共享购物车（我发起或我被邀请），按创建时间倒序 */
export function getMySharedCarts() {
  return alovaInstance.Get<SharedCart[]>(`${BASE}/my`)
}

/**
 * 当前进行中的共享购物车摘要（首页补给单卡片）。
 *
 * 无进行中的购物车时 cart 为 null（首页渲染空态引导），不抛异常。
 * 进度来自 `shared_cart_item.planned_quantity / fulfilled_quantity`
 * （77 号双模式脚本），未设计划的行不参与百分比。
 */
export function getActiveSharedCartSummary(vesselCallId?: string) {
  return alovaInstance.Get<SharedCartSummary>(`${BASE}/active-summary`, {
    params: { vesselCallId },
  })
}

/** 购物车详情（仅成员可见） */
export function getSharedCart(id: string) {
  return alovaInstance.Get<SharedCart>(`${BASE}/${id}`)
}

/** 成员列表 */
export function getSharedCartMembers(id: string) {
  return alovaInstance.Get<SharedCartMember[]>(`${BASE}/${id}/members`)
}

/** 有效明细列表 */
export function getSharedCartItems(id: string) {
  return alovaInstance.Get<SharedCartItem[]>(`${BASE}/${id}/items`)
}

/** 创建共享购物车（发起人） */
export function createSharedCart(data: SharedCartCreatePayload) {
  return alovaInstance.Post<SharedCart>(BASE, data)
}

/** 邀请成员（发起人）；memberRole: 2成员 3确认人 */
export function inviteSharedCartMember(id: string, memberUserId: string, memberRole = '2') {
  return alovaInstance.Post<SharedCartMember>(`${BASE}/${id}/members`, undefined, {
    params: { memberUserId, memberRole },
  })
}

/** 生成/复用分享令牌（发起人），用于转发到微信群 */
export function shareSharedCart(id: string) {
  return alovaInstance.Post<string>(`${BASE}/${id}/share`)
}

/** 凭分享令牌自助加入（点击群卡片后调用，服务端自动补建船舶成员关系） */
export function joinSharedCartByToken(token: string) {
  return alovaInstance.Post<string>(`${BASE}/join`, undefined, {
    params: { token },
  })
}

/** 设置我的展示姓名（加入时填写一次，配送贴标签用） */
export function setMyDisplayName(id: string, displayName: string) {
  return alovaInstance.Put<SharedCartMember>(`${BASE}/${id}/members/me/name`, { displayName })
}

/** 添加自己的明细 */
export function addSharedCartItem(id: string, data: SharedCartItemPayload) {
  return alovaInstance.Post<SharedCartItem>(`${BASE}/${id}/items`, data)
}

/** 修改自己的明细 */
export function updateSharedCartItem(id: string, itemId: string, data: SharedCartItemPayload) {
  return alovaInstance.Put<SharedCartItem>(`${BASE}/${id}/items/${itemId}`, data)
}

/**
 * 设置明细的计划量/已采量（确认人/发起人；补给单排计划与回填进度）。
 *
 * 两个数量的语义由 `clearPlanned` 区分，避免误清计划：
 * - `plannedQuantity = null` 且 `clearPlanned = true` -> 取消该行计划；
 * - `plannedQuantity = null` 且不传 `clearPlanned` -> 本次不改计划；
 * - `fulfilledQuantity = null` -> 本次不改已采量。
 */
export function updateSharedCartItemPlan(id: string, data: SharedCartPlanPayload) {
  return alovaInstance.Put<SharedCartItem>(`${BASE}/${id}/items/plan`, data)
}

/** 移除自己的明细 */
export function removeSharedCartItem(id: string, itemId: string) {
  return alovaInstance.Delete<void>(`${BASE}/${id}/items/${itemId}`)
}

/** 历史补给单复用入参（把历史明细复制到当前靠港计划下的购物车） */
export interface SharedCartReusePayload {
  /** 船舶ID（必须与源单一致；跨船复用无业务意义） */
  vesselId: string
  /** 本次采购的靠港计划ID（取当前上下文，不是源单那个历史靠港） */
  vesselCallId: string
  /** 备注；不传则沿用源单备注 */
  remark?: string
}

/** 历史补给单复用结果 */
export interface SharedCartReuseResult {
  cartId: string
  cartNo?: string
  /** 是否并入该船已有的进行中购物车（true 表示不是新建的） */
  adoptedExisting?: boolean
  /** 成功复用的明细项数（按 SKU 合并后） */
  reusedCount: number
  /** 跳过的项数 */
  skippedCount: number
  skipped: Array<{ skuId: string, reason: string }>
}

/** 计划量/已采量编辑入参（确认人操作，可操作任意成员的明细行） */
export interface SharedCartPlanPayload {
  itemId: string
  /** 计划采购量；null 表示取消计划（需配合 clearPlanned）或不改（不传） */
  plannedQuantity?: number | null
  /** 已采量；null/不传表示本次不改 */
  fulfilledQuantity?: number | null
  /** 显式清除计划量：区分「取消计划」与「本次不改已采量」 */
  clearPlanned?: boolean
}

/**
 * 历史补给单一键复用：把历史购物车的明细复制到当前靠港计划下的购物车。
 *
 * 复用只搬清单，不下单、不锁价、不校验库存 —— 用户确认后仍走正常的
 * 「改数量 → 提交整船订单」流程（与订单「再来一单」同口径）。
 */
export function reuseSharedCartFromHistory(id: string, data: SharedCartReusePayload) {
  return alovaInstance.Post<SharedCartReuseResult>(`${BASE}/${id}/reuse`, data)
}

/** 确认人统一提交，生成整船订单（幂等，返回订单ID） */
export function confirmSharedCart(id: string, data: SharedCartConfirmPayload) {
  return alovaInstance.Post<string>(`${BASE}/${id}/confirm`, data)
}

/** 发起人关闭购物车 */
export function closeSharedCart(id: string) {
  return alovaInstance.Post<void>(`${BASE}/${id}/close`)
}
