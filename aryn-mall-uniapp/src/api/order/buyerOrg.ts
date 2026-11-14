/**
 * 采购主体 API（工厂/企业/园区餐厅）。
 *
 * 后端契约（AppBuyerOrgController，C 端网关域 mall-order）：
 * - 三条入驻路径：搜索加入（需厂长/运营审核）、邀请码免审加入、搜不到创建（待审核可先下单）；
 * - 首单权益按主体算一次，不按成员账号重复算；P0 只落主体与订单快照。
 */
import { alovaInstance } from '@/api/core/instance'

const BASE = '/mall-order/app/buyer-org'

/** 主体类型：企业 */
export const BUYER_ORG_TYPE_ENTERPRISE = '1'

/** 主体类型：工厂 */
export const BUYER_ORG_TYPE_FACTORY = '2'

/** 主体类型：园区餐厅 */
export const BUYER_ORG_TYPE_RESTAURANT = '3'

/** 成员角色：厂长/管理员 */
export const BUYER_ORG_ROLE_OWNER = '1'

/** 成员角色：采购员 */
export const BUYER_ORG_ROLE_BUYER = '2'

/** 成员角色：业务员 */
export const BUYER_ORG_ROLE_SALES = '3'

/** 成员角色：查看者 */
export const BUYER_ORG_ROLE_VIEWER = '4'

/** 审核状态：待审核（可下企业单，不享首单） */
export const BUYER_ORG_AUDIT_PENDING = '0'

/** 审核状态：已通过 */
export const BUYER_ORG_AUDIT_APPROVED = '1'

/** 审核状态：已驳回 */
export const BUYER_ORG_AUDIT_REJECTED = '2'

/** 审核状态：已停用 */
export const BUYER_ORG_AUDIT_DISABLED = '3'

export interface BuyerOrg {
  id: string
  orgCode?: string
  orgName: string
  /** 1企业 2工厂 3园区餐厅 */
  orgType?: string
  /** 脱敏信用代码 */
  creditCodeMasked?: string
  province?: string
  city?: string
  district?: string
  address?: string
  contactName?: string
  contactPhoneMasked?: string
  /** 0待审核 1已通过 2已驳回 3已停用 */
  auditStatus?: string
  auditRemark?: string
  ownerUserId?: string
  /** 1正常 0停用 */
  status?: string
  bindSource?: string
  createTime?: string
  memberCount?: number
  joined?: boolean
  viewerRole?: string
  viewerCanEdit?: boolean
  viewerCanConfirm?: boolean
  viewerIsOwner?: boolean
  /** 我的待审核申请状态 */
  myApplyStatus?: string
}

export interface BuyerOrgMember {
  id: string
  buyerOrgId: string
  userId: string
  nickname?: string
  avatarUrl?: string
  phoneMasked?: string
  /** 1厂长/管理员 2采购员 3业务员 4查看者 */
  memberRole?: string
  canEdit?: string
  canConfirm?: string
  /** 1正常 0已移除 */
  status?: string
  bindSource?: string
  joinTime?: string
  leaveTime?: string
}

export interface BuyerOrgInviteCode {
  id: string
  buyerOrgId: string
  code: string
  expiresAt: string
  maxUses?: number
  usedCount?: number
  /** 1有效 0已失效 */
  status?: string
}

export interface BuyerOrgApplyPayload {
  /** 1创建主体 2加入已有主体 */
  applyType: string
  /** applyType=2 必填 */
  buyerOrgId?: string
  /** applyType=1 必填 */
  orgName?: string
  orgType?: string
  creditCode?: string
  licenseUrl?: string
  province?: string
  city?: string
  district?: string
  address?: string
  contactName?: string
  contactPhone?: string
  applyRole?: string
  remark?: string
}

/** 我绑定的采购主体（含待审核/驳回/停用状态） */
export function getMyBuyerOrgs() {
  return alovaInstance.Get<BuyerOrg[]>(`${BASE}/my`)
}

/** 搜索已审核主体（名称/信用代码，信用代码脱敏） */
export function searchBuyerOrgs(keyword: string) {
  return alovaInstance.Get<BuyerOrg[]>(`${BASE}/search`, {
    params: { keyword },
  })
}

/** 提交创建/加入申请 */
export function applyBuyerOrg(data: BuyerOrgApplyPayload) {
  return alovaInstance.Post<{ id: string, status: string }>(`${BASE}/apply`, data)
}

/** 我的申请单 */
export function getMyBuyerOrgApplies() {
  return alovaInstance.Get<Array<{
    id: string
    applyNo: string
    applyType: string
    orgName: string
    status: string
    auditRemark?: string
    matchedOrgId?: string
    createTime?: string
  }>>(`${BASE}/apply/my`)
}

/** 主体详情（仅成员） */
export function getBuyerOrgDetail(id: string) {
  return alovaInstance.Get<BuyerOrg>(`${BASE}/${id}`)
}

/** 凭邀请码加入 */
export function joinBuyerOrgByCode(code: string) {
  return alovaInstance.Post<BuyerOrgMember>(`${BASE}/join`, undefined, {
    params: { code },
  })
}

/** 主体成员 */
export function getBuyerOrgMembers(id: string) {
  return alovaInstance.Get<BuyerOrgMember[]>(`${BASE}/${id}/members`)
}

/** 生成邀请码（厂长/管理员） */
export function createBuyerOrgInviteCode(
  id: string,
  data: { maxUses?: number, expireHours?: number, remark?: string } = {},
) {
  return alovaInstance.Post<BuyerOrgInviteCode>(`${BASE}/${id}/invite-code`, data)
}

/** 退出主体 */
export function leaveBuyerOrg(id: string) {
  return alovaInstance.Post<boolean>(`${BASE}/${id}/leave`)
}

/** 厂长移除成员 */
export function removeBuyerOrgMember(id: string, userId: string) {
  return alovaInstance.Delete<boolean>(`${BASE}/${id}/members/${userId}`)
}
