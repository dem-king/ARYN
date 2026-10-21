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

/**
 * 绑定来源字典（与后端 `VesselMemberBindSource` 一一对应）。
 *
 * `MANUAL` 只是历史数据兜底（DB 默认值），不代表任何业务路径；
 * 管理端成员列表据此展示来源标签，运营才能识别并回收 `SHARE_CART`
 * 这类「任何拿到链接的人都会被自动补建」的低信任关系。
 */
export type VesselMemberBindSourceCode =
  | 'ADMIN_ADD'
  | 'APPLY_AUDIT'
  | 'APPLY_AUDIT_MISMATCH'
  | 'INVITE_CODE'
  | 'MANUAL'
  | 'MEMBER_ADD'
  | 'SHARE_CART';

/** 船舶成员 */
export interface VesselMember {
  /** 绑定补充原因（审核偏离理由等），与来源一起构成可追溯证据 */
  bindReason?: string;
  /** 绑定来源码值，见后端 VesselMemberBindSource */
  bindSource?: string;
  /** 绑定来源中文名，优先用服务端下发的 label（未知值回退「手工/历史」） */
  bindSourceLabel?: string;
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
  /** 申请单填写的呼号（与 IMO 分列采集，不再混装） */
  applyVesselCallSign?: string;
  /** 申请单填写的 IMO 编号（不再混装呼号） */
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
  /**
   * 申请船名与最终绑定船名不一致（归一化后仍不同）。
   *
   * 由服务端在列表接口里算好：前端不做二次判定，避免两边归一化口径漂移后
   * 出现「列表说一致、服务端说不一致」的错位。
   */
  vesselMismatch?: boolean;
}

/** 绑定申请审核入参：三选一确定目标船舶 */
export interface VesselBindAudit {
  auditRemark?: string;
  /** 系统内已存在同名在营船舶时，新建必须显式确认，否则服务端拒绝 */
  confirmDuplicateName?: boolean;
  /**
   * 已确认「申请船名与所选船舶不一致」（不一致时必须为 true）。
   *
   * 服务端判据是客观身份键：IMO/呼号相等或船名归一化后相等即视为同一条船；
   * 都不命中时必须显式背书 + 填写 `auditRemark`，否则拒绝——这条链路此前
   * 能把「悦航8号」的申请静默绑到「悦航7号」。
   */
  confirmVesselMismatch?: boolean;
  matchedVesselId?: string;
  newVesselCallSign?: string;
  newVesselImo?: string;
  newVesselName?: string;
  newVesselType?: string;
  /** 审核通过后写入的成员角色，缺省沿用申请角色 */
  memberRole?: string;
}

/** 审核弹窗的候选船舶（只带认船需要的字段） */
export interface VesselBindVesselOption {
  callSign?: string;
  id?: string;
  imoCode?: string;
  /** 命中原因：IMO一致 / 呼号一致 / 船名相同 / 船名相近；普通检索为空 */
  matchReason?: string;
  vesselName?: string;
}

/** 审核预检：打开弹窗即知道「申请里的船在不在系统内」 */
export interface VesselBindPrecheck {
  applyId?: string;
  applyVesselImo?: string;
  applyVesselName?: string;
  candidates?: VesselBindVesselOption[];
  suggestedMode?: string;
  suggestedVesselId?: string;
  suggestion?: string;
}

export async function getBindApplyPage(query: any) {
  return requestClient.get('/vessel/admin/bind-applies/page', {
    params: query,
  });
}

/**
 * 审核预检。按申请的 IMO/呼号与船名查在营船舶，返回建议与候选。只读。
 */
export async function getBindApplyPrecheck(id: string) {
  return requestClient.get<VesselBindPrecheck>(
    `/vessel/admin/bind-applies/${id}/precheck`,
  );
}

/** 候选船舶检索（按船名/IMO/呼号，仅在营船） */
export async function getBindApplyVesselOptions(keyword?: string) {
  return requestClient.get<VesselBindVesselOption[]>(
    '/vessel/admin/bind-applies/vessel-options',
    { params: { keyword } },
  );
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

/**
 * 解绑成员（误绑纠错，绑定申请页专用入口）。
 * 原因会随站内信告知本人；解绑走逻辑删除，之后仍可重新绑定。
 *
 * 该入口挂在绑定审核权限下（`vessel:bindapply:audit`），只服务「审核绑错」这一场景；
 * 船舶档案页的成员回收请用 {@link unbindVesselMember}（走 `vessel:member:save`）。
 */
export async function unbindVesselMemberByApply(data: {
  reason?: string;
  userId: string;
  vesselId: string;
}) {
  return requestClient.post('/vessel/admin/bind-applies/unbind', data);
}

/**
 * 管理端回收船舶成员（船舶档案 → 成员弹窗）。
 *
 * 与「绑定申请 → 解绑」的区别：这里是运营在成员列表里看到来源异常
 * （例如 `SHARE_CART` 分享链接自动补建）后的主动回收，权限沿用页面本身的
 * `vessel:member:save`，服务端委托既有 `adminUnbindMember` 统一执行。
 * 原因必填——它既是站内信正文，也是审计表里的 `reason`。
 */
export async function unbindVesselMember(
  vesselId: string,
  userId: string,
  reason: string,
) {
  return requestClient.post(
    `/vessel/admin/${vesselId}/members/${userId}/unbind`,
    { reason },
  );
}
