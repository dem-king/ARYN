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
  memberRemark?: string
  /** 1待确认 2已确认 3已移除 */
  status: string
  createTime?: string
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

const BASE = '/mall-order/app/shared-cart'

/** 我参与的全部共享购物车（我发起或我被邀请），按创建时间倒序 */
export function getMySharedCarts() {
  return alovaInstance.Get<SharedCart[]>(`${BASE}/my`)
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

/** 移除自己的明细 */
export function removeSharedCartItem(id: string, itemId: string) {
  return alovaInstance.Delete<void>(`${BASE}/${id}/items/${itemId}`)
}

/** 确认人统一提交，生成整船订单（幂等，返回订单ID） */
export function confirmSharedCart(id: string, data: SharedCartConfirmPayload) {
  return alovaInstance.Post<string>(`${BASE}/${id}/confirm`, data)
}

/** 发起人关闭购物车 */
export function closeSharedCart(id: string) {
  return alovaInstance.Post<void>(`${BASE}/${id}/close`)
}
