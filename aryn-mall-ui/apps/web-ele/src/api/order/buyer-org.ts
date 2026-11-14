import { requestClient } from '#/api/request';

/** 采购主体管理端 API（P1 审核页 / P2 批量处理）。 */
export interface BuyerOrg {
  id: string;
  orgName: string;
  orgCode?: string;
  orgType?: string;
  creditCode?: string;
  creditCodeMasked?: string;
  province?: string;
  city?: string;
  district?: string;
  address?: string;
  contactName?: string;
  contactPhone?: string;
  contactPhoneMasked?: string;
  auditStatus?: string;
  auditRemark?: string;
  ownerUserId?: string;
  status?: string;
  memberCount?: number;
  createTime?: string;
}

export interface BuyerOrgMember {
  id: string;
  buyerOrgId: string;
  userId: string;
  nickname?: string;
  phoneMasked?: string;
  memberRole?: string;
  canEdit?: string;
  canConfirm?: string;
  status?: string;
  bindSource?: string;
  joinTime?: string;
}

export interface BuyerOrgApply {
  id: string;
  applyNo: string;
  applyType: string;
  orgName: string;
  orgType?: string;
  status: string;
  auditRemark?: string;
  matchedOrgId?: string;
  matchedOrgName?: string;
  applicantUserId?: string;
  createTime?: string;
}

export async function getBuyerOrgPage(query: any) {
  return requestClient.get('/mall-order/buyer-org/page', { params: query });
}

export async function getBuyerOrgDetail(id: string) {
  return requestClient.get<BuyerOrg>(`/mall-order/buyer-org/${id}`);
}

export async function createBuyerOrg(data: Partial<BuyerOrg>) {
  return requestClient.post<BuyerOrg>('/mall-order/buyer-org', data);
}

export async function updateBuyerOrg(id: string, data: Partial<BuyerOrg>) {
  return requestClient.put<boolean>(`/mall-order/buyer-org/${id}`, data);
}

export async function auditBuyerOrg(
  id: string,
  data: { auditRemark?: string; auditStatus: string },
) {
  return requestClient.post<boolean>(`/mall-order/buyer-org/${id}/audit`, data);
}

/** 批量通过/驳回/停用；任一条不合法整体回滚。 */
export async function batchAuditBuyerOrg(data: {
  auditRemark?: string;
  auditStatus: string;
  ids: string[];
}) {
  return requestClient.post<number>('/mall-order/buyer-org/batch-audit', data);
}

export async function getBuyerOrgMembers(id: string) {
  return requestClient.get<BuyerOrgMember[]>(
    `/mall-order/buyer-org/${id}/members`,
  );
}

export async function addBuyerOrgMember(
  id: string,
  data: {
    canConfirm?: string;
    canEdit?: string;
    memberRole?: string;
    remark?: string;
    userId: string;
  },
) {
  return requestClient.post<BuyerOrgMember>(
    `/mall-order/buyer-org/${id}/members`,
    data,
  );
}

export async function removeBuyerOrgMember(id: string, userId: string) {
  return requestClient.delete<boolean>(
    `/mall-order/buyer-org/${id}/members/${userId}`,
  );
}

export async function createBuyerOrgInviteCode(
  id: string,
  data: { expireHours?: number; maxUses?: number; remark?: string } = {},
) {
  return requestClient.post<{
    code: string;
    expiresAt: string;
    id: string;
    maxUses?: number;
  }>(`/mall-order/buyer-org/${id}/invite-code`, data);
}

export async function getBuyerOrgApplyPage(query: any) {
  return requestClient.get('/mall-order/buyer-org/apply/page', {
    params: query,
  });
}

export async function auditBuyerOrgApply(
  id: string,
  data: { approved: boolean; auditRemark?: string; matchedOrgId?: string },
) {
  return requestClient.post<boolean>(
    `/mall-order/buyer-org/apply/${id}/audit`,
    data,
  );
}
