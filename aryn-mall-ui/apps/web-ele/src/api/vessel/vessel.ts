import { requestClient } from '#/api/request';

/** 船舶档案 */
export interface VesselInfo {
  callSign?: string;
  crewCapacity?: number;
  dwt?: number;
  flagState?: string;
  id?: string;
  imoCode?: string;
  remark?: string;
  status?: string;
  vesselName: string;
  vesselNameEn?: string;
  vesselType?: string;
}

/** 船舶成员 */
export interface VesselMember {
  canConfirm?: string;
  canEdit?: string;
  id?: string;
  joinTime?: string;
  memberRole?: string;
  remark?: string;
  status?: string;
  userId: string;
  vesselId?: string;
}

/** 靠港计划 */
export interface VesselCall {
  berth?: string;
  deliveryWindowEnd?: string;
  deliveryWindowStart?: string;
  eta: string;
  etd: string;
  id?: string;
  portCode: string;
  portName: string;
  remark?: string;
  status?: string;
  vesselId: string;
  /** 来源：1运营维护 2海员申报 */
  source?: string;
  /** 申报人商城用户ID（海员申报时写入） */
  declaredBy?: string;
}

export async function getVesselPage(query: any) {
  return requestClient.get('/vessel/admin/page', { params: query });
}

export async function addVessel(data: Partial<VesselInfo>) {
  return requestClient.post('/vessel/admin', data);
}

export async function updateVessel(id: string, data: Partial<VesselInfo>) {
  return requestClient.put(`/vessel/admin/${id}`, data);
}

export async function getVesselMembers(vesselId: string) {
  return requestClient.get(`/vessel/admin/${vesselId}/members`);
}

export async function addVesselMember(
  vesselId: string,
  data: Partial<VesselMember>,
) {
  return requestClient.post(`/vessel/admin/${vesselId}/members`, data);
}

export async function getVesselCalls(vesselId: string) {
  return requestClient.get(`/vessel/admin/${vesselId}/calls`);
}

export async function addVesselCall(
  vesselId: string,
  data: Partial<VesselCall>,
) {
  return requestClient.post(`/vessel/admin/${vesselId}/calls`, data);
}

export async function updateVesselCall(
  callId: string,
  data: Partial<VesselCall>,
) {
  return requestClient.put(`/vessel/admin/calls/${callId}`, data);
}

/** 靠港计划变更影响面 */
/** 待处理靠港申报（海员申报且尚未排产） */
export async function getDeclaredVesselCalls() {
  return requestClient.get('/vessel/admin/calls/declared');
}

export async function getVesselCallImpact(vesselCallId: string) {
  return requestClient.get('/mall-order/fulfillment/vessel-call-impact', {
    params: { vesselCallId },
  });
}

/** 船舶绑定申请 */
export interface VesselBindApply {
  applyNo?: string;
  applyPortName?: string;
  applyRole?: string;
  applyVesselImo?: string;
  applyVesselName?: string;
  auditRemark?: string;
  auditTime?: string;
  createTime?: string;
  id?: string;
  matchedVesselId?: string;
  matchedVesselName?: string;
  phone?: string;
  position?: string;
  realName?: string;
  remark?: string;
  status?: string;
  userId?: string;
  userNickname?: string;
  userPhone?: string;
}

/** 绑定申请审核入参：三选一确定目标船舶 */
export interface VesselBindAudit {
  auditRemark?: string;
  matchedVesselId?: string;
  newVesselImo?: string;
  newVesselName?: string;
  newVesselType?: string;
  /** 审核通过后写入的成员角色，缺省沿用申请角色 */
  memberRole?: string;
}

export async function getBindApplyPage(query: any) {
  return requestClient.get('/vessel/admin/bind-applies/page', {
    params: query,
  });
}

/**
 * 审核通过。返回实际绑定的船舶ID。
 * 船未录入系统时传 newVesselName，服务端会先建船再绑定。
 */
export async function approveBindApply(id: string, data: VesselBindAudit) {
  return requestClient.post(`/vessel/admin/bind-applies/${id}/approve`, data);
}

export async function rejectBindApply(id: string, data: VesselBindAudit) {
  return requestClient.post(`/vessel/admin/bind-applies/${id}/reject`, data);
}
