/**
 * 船舶上下文相关 API
 * 船舶域服务（Cloud 网关域：/vessel；Boot 模式由 rewriteBootUrl 改写）
 */
import { alovaInstance } from '@/api/core/instance'

/** 船舶档案（C 端返回本人作为成员的在营船舶） */
export function getMyVessels() {
  return alovaInstance.Get<any[]>('/vessel/app/my-vessels')
}

/** 船舶可用靠港计划（排除已过期/已完成/已取消） */
export function getVesselCalls(vesselId: string) {
  return alovaInstance.Get<any[]>(`/vessel/app/${vesselId}/calls`)
}

/** 船舶配送上下文（下一靠港、港口、泊位、时间窗） */
/**
 * 海员申报靠港。
 *
 * 公司无法与船舶公司对接船期，ETA/ETD 只有船上的人知道；
 * 因此由海员在下单时申报，运营收到后再排产（补配送时间窗、排波次、派车）。
 */
export function declareVesselCall(data: {
  vesselId: string
  portCode: string
  portName: string
  berth?: string
  eta: string
  etd: string
  remark?: string
}) {
  return alovaInstance.Post<any>('/vessel/app/calls/declare', data)
}

export function getVesselContext(vesselId: string) {
  return alovaInstance.Get<any>('/vessel/app/context', {
    params: { vesselId },
  })
}

// ---------------------------------------------------------------------------
// 船舶自助绑定
//
// 三条通道：业务员认领船舶（申请+审核）、成员现场拉人、邀请码自助加入。
// 此前 C 端没有任何自助绑定入口，未绑定用户进不了船供链路且无出路。
// ---------------------------------------------------------------------------

export interface VesselBindApply {
  id: string
  applyNo: string
  applyRole: string
  applyVesselName: string
  applyVesselImo?: string
  applyPortName?: string
  realName?: string
  phone?: string
  position?: string
  remark?: string
  /** 1待审核 2已通过 3已驳回 4已取消 */
  status: string
  matchedVesselId?: string
  matchedVesselName?: string
  auditRemark?: string
  auditTime?: string
  createTime?: string
}

export interface VesselMember {
  id: string
  userId: string
  nickname?: string
  phone?: string
  /** 1发起人 2普通船员 3采购确认人 4业务员 */
  memberRole: string
  status: string
  remark?: string
  joinTime?: string
}

export interface VesselInviteCode {
  id: string
  vesselId: string
  code: string
  ownerUserId: string
  maxUses: number
  usedCount: number
  expiresAt: string
  status: string
  remark?: string
}

export interface VesselBindApplyPayload {
  /** 2普通船员 4业务员（销售认领船舶） */
  applyRole: string
  applyVesselName: string
  applyVesselImo?: string
  applyPortName?: string
  realName?: string
  phone?: string
  position?: string
  remark?: string
}

/** 提交船舶绑定 / 认领申请 */
export function submitBindApply(data: VesselBindApplyPayload) {
  return alovaInstance.Post<VesselBindApply>('/vessel/app/bind-applies', data)
}

/** 我的绑定申请列表 */
export function getMyBindApplies() {
  return alovaInstance.Get<VesselBindApply[]>('/vessel/app/bind-applies/my')
}

/** 撤回我的待审核申请 */
export function cancelBindApply(id: string) {
  return alovaInstance.Post<void>(`/vessel/app/bind-applies/${id}/cancel`)
}

/** 船舶成员列表（仅该船成员可见） */
export function getVesselMembers(vesselId: string) {
  return alovaInstance.Get<VesselMember[]>(`/vessel/app/${vesselId}/members`)
}

/** 添加船舶成员（限发起人/采购确认人/业务员） */
export function addVesselMember(vesselId: string, data: { userId?: string, phone?: string, memberRole: string, remark?: string }) {
  return alovaInstance.Post<VesselMember>(`/vessel/app/${vesselId}/members`, data)
}

/** 移除船舶成员 */
export function removeVesselMember(vesselId: string, memberId: string) {
  return alovaInstance.Delete<void>(`/vessel/app/${vesselId}/members/${memberId}`)
}

/** 检索可添加的商城用户（手机号/昵称/用户ID）；status=1 表示已在船上 */
export function searchMemberCandidates(vesselId: string, keyword: string) {
  return alovaInstance.Get<VesselMember[]>(`/vessel/app/${vesselId}/member-candidates`, {
    params: { keyword },
  })
}

/** 生成邀请码 */
export function generateInviteCode(vesselId: string, params?: { maxUses?: number, expireHours?: number, remark?: string }) {
  return alovaInstance.Post<VesselInviteCode>(`/vessel/app/${vesselId}/invite-codes`, undefined, { params })
}

/** 我生成的有效邀请码 */
export function getMyInviteCodes(vesselId: string) {
  return alovaInstance.Get<VesselInviteCode[]>(`/vessel/app/${vesselId}/invite-codes`)
}

/** 撤销邀请码 */
export function revokeInviteCode(codeId: string) {
  return alovaInstance.Delete<void>(`/vessel/app/invite-codes/${codeId}`)
}

/** 使用邀请码加入船舶 */
export function redeemInviteCode(code: string) {
  return alovaInstance.Post<VesselMember>('/vessel/app/invite-codes/redeem', undefined, {
    params: { code },
  })
}
