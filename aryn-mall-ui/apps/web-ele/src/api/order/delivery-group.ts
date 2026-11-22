import { requestClient } from '#/api/request';

export interface DeliveryGroup {
  cutoff?: string;
  id: string;
  maxOrders: number;
  orderCount?: number;
  portCode: string;
  revision: number;
  status: string;
  unassignedCount?: number;
  vesselCallId: string;
  vesselId: string;
  warehouseId: string;
  windowEnd?: string;
  windowSequence: number;
  windowStart?: string;
}
export interface GroupOrder {
  id: string;
  orderId: string;
  revision: number;
  status: string;
  taskId?: string;
  waveId?: string;
}
export interface GroupCandidate {
  assignedGroupId?: string;
  id: string;
  orderNo?: string;
  purchaseScene: string;
  warehouseId?: string;
  windowEnd?: string;
  windowStart?: string;
}
export interface GroupOperation {
  action: string;
  actorId: string;
  createTime: string;
  id: string;
  outboxStatus: string;
  reason: string;
}
export interface GroupDetail {
  group: DeliveryGroup;
  operations: GroupOperation[];
  orders: GroupOrder[];
}
export interface GroupCreate {
  cutoff?: null | string;
  maxOrders: number;
  reason: string;
  requestId: string;
  vesselCallId: string;
  warehouseId: string;
  windowEnd?: null | string;
  windowSequence: number;
  windowStart?: null | string;
}
export interface GroupChange {
  action: 'CANCEL' | 'CLOSE' | 'COMPLETE' | 'JOIN' | 'REMOVE' | 'SCHEDULE';
  allowLate?: boolean;
  cutoff?: null | string;
  expectedRevision: number;
  maxOrders?: number;
  orderIds: string[];
  reason: string;
  requestId: string;
  windowEnd?: null | string;
  windowStart?: null | string;
}
const base = '/mall-order/delivery-groups';
export const getDeliveryGroups = (params: {
  after?: string;
  vesselCallId?: string;
}) => requestClient.get<DeliveryGroup[]>(base, { params });
export const getDeliveryGroup = (id: string) =>
  requestClient.get<GroupDetail>(`${base}/${encodeURIComponent(id)}`);
export const getGroupCandidates = (id: string) =>
  requestClient.get<GroupCandidate[]>(
    `${base}/${encodeURIComponent(id)}/candidates`,
  );
export const createDeliveryGroup = (data: GroupCreate) =>
  requestClient.post(base, data);
export const changeDeliveryGroup = (id: string, data: GroupChange) =>
  requestClient.post(`${base}/${encodeURIComponent(id)}/commands`, data);
