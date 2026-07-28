import { requestClient } from '#/api/request';

export type DeliveryTaskStatus =
  | 'ASSIGNED'
  | 'CLOSED'
  | 'DELIVERED'
  | 'DELIVERING'
  | 'EXCEPTION'
  | 'PICKING'
  | 'RETURN_PENDING'
  | 'WAITING_ASSIGNMENT';

export interface DeliveryEvidence {
  attemptNo: number;
  bindingStatus: string;
  evidenceType: string;
  id: string;
  materialId: string;
  sortNo: number;
}

export interface DeliveryTask {
  assigneeId?: string;
  assigneeMobile?: string;
  assigneeName?: string;
  attemptNo: number;
  closedAt?: string;
  createTime: string;
  deliveredAt?: string;
  evidences?: DeliveryEvidence[];
  exceptionCode?: string;
  exceptionSummary?: string;
  id: string;
  logs?: Array<Record<string, any>>;
  orderId: string;
  orderNo: string;
  pickedUpAt?: string;
  pickingStartedAt?: string;
  remark?: string;
  returnPendingAt?: string;
  returnedAt?: string;
  status: DeliveryTaskStatus;
  taskNo: string;
  updateTime: string;
  version: number;
}

export interface DeliveryStaff {
  avatar?: string;
  deptId?: string;
  id: string;
  nickname: string;
  phone?: string;
}

export interface AssignmentForm {
  assigneeId: string;
  description?: string;
  reasonCode?: string;
  remark?: string;
  requestId: string;
  version: number;
}

export async function getDeliveryTaskPage(query: Record<string, any>) {
  return requestClient.get('/mall-order/delivery/admin/tasks', {
    params: query,
  });
}

export async function getDeliveryTask(id: string) {
  return requestClient.get<DeliveryTask>(
    `/mall-order/delivery/admin/tasks/${id}`,
  );
}

export async function getDeliveryStaffCandidates(
  query: Record<string, any> = {},
) {
  return requestClient.get('/mall-order/delivery/admin/staff/candidates', {
    params: query,
  });
}

export async function assignDeliveryTask(id: string, data: AssignmentForm) {
  return requestClient.post(
    `/mall-order/delivery/admin/tasks/${id}/assign`,
    data,
  );
}

export async function reassignDeliveryTask(id: string, data: AssignmentForm) {
  return requestClient.post(
    `/mall-order/delivery/admin/tasks/${id}/reassign`,
    data,
  );
}

export async function closeDeliveryTask(id: string, data: Record<string, any>) {
  return requestClient.post(
    `/mall-order/delivery/admin/tasks/${id}/close`,
    data,
  );
}

export async function markDeliveryReturnPending(
  id: string,
  data: Record<string, any>,
) {
  return requestClient.post(
    `/mall-order/delivery/admin/tasks/${id}/return-pending`,
    data,
  );
}

export async function confirmDeliveryReturn(
  id: string,
  data: Record<string, any>,
) {
  return requestClient.post(
    `/mall-order/delivery/admin/tasks/${id}/return-confirm`,
    data,
  );
}

export async function refreshEvidenceAccess(
  taskId: string,
  evidenceId: string,
) {
  return requestClient.get(
    `/mall-order/delivery/admin/tasks/${taskId}/evidences/${evidenceId}/access`,
  );
}

export function getOrderFulfillmentAction(order: {
  deliveryWay?: string;
  status?: string;
}) {
  if (order.deliveryWay === '3' && ['2', '7'].includes(order.status || '')) {
    return 'ASSIGN';
  }
  if (order.deliveryWay === '1' && ['2', '7'].includes(order.status || '')) {
    return 'SHIP';
  }
  if (order.deliveryWay === '2' && order.status === '3') return 'PICKUP';
  return 'NONE';
}

export function buildAssignmentPayload(
  mode: 'ASSIGN' | 'REASSIGN',
  form: AssignmentForm,
) {
  const base = {
    assigneeId: form.assigneeId,
    requestId: form.requestId,
    version: form.version,
  };
  return mode === 'REASSIGN'
    ? {
        ...base,
        description: form.description,
        reasonCode: form.reasonCode,
      }
    : { ...base, remark: form.remark };
}

export function isEvidenceAccessExpired(expiresAt?: string, now = new Date()) {
  return !expiresAt || new Date(expiresAt).getTime() <= now.getTime();
}
