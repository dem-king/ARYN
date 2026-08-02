import { requestClient } from '#/api/request';

/**
 * 配送任务状态：
 * 1-待派单 2-待取货 3-配货中 4-待送达 5-已送达 6-已签收 7-已取消 8-异常 9-待退回
 */
export type DeliveryTaskStatus = '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9';

/**
 * 配送任务查询参数
 */
export interface DeliveryTaskPageQuery {
  current?: number;
  size?: number;
  asc?: string;
  desc?: string;
  taskNo?: string;
  orderNo?: string;
  recipientName?: string;
  recipientPhone?: string;
  status?: DeliveryTaskStatus;
  staffId?: string;
}

/**
 * 配送任务实体
 */
export interface DeliveryTask {
  id: string;
  taskNo: string;
  orderId: string;
  orderNo: string;
  recipientName: string;
  recipientPhone: string;
  recipientAddress?: string;
  status: DeliveryTaskStatus;
  staffId?: string;
  staffName?: string;
  assignTime?: string;
  pickUpTime?: string;
  arriveTime?: string;
  departTime?: string;
  signTime?: string;
  exceptionTime?: string;
  returnPendingTime?: string;
  returnConfirmTime?: string;
  closeTime?: string;
  attemptNo?: number;
  exceptionReason?: string;
  exceptionDesc?: string;
  remark?: string;
  tripId?: string;
  tripNo?: string;
  createTime?: string;
  updateTime?: string;
  itemList?: any[];
}

/**
 * 配送凭证实体
 */
export interface DeliveryEvidence {
  id: string;
  taskId: string;
  attemptNo: number;
  evidenceType: string;
  materialId?: string;
  materialUrl?: string;
  sortNo: number;
  uploadBy?: string;
  createTime?: string;
}

/**
 * 配送任务操作日志
 */
export interface DeliveryTaskLog {
  id: string;
  taskId: string;
  action: string;
  fromStatus?: string;
  toStatus?: string;
  attemptNo?: number;
  operatorType?: string;
  operatorId?: string;
  operatorName?: string;
  reasonCode?: string;
  reasonDesc?: string;
  createTime?: string;
}

export interface AssignDeliveryTasksPayload {
  taskIds: string[];
  staffId: string;
}

export interface ReassignDeliveryTaskPayload {
  staffId: string;
  reason?: string;
}

export async function getDeliveryTaskPage(params: DeliveryTaskPageQuery) {
  return requestClient.get('/mall-order/delivery/task/page', { params });
}

export async function getDeliveryTaskDetail(id: string) {
  return requestClient.get(`/mall-order/delivery/task/${id}`);
}

export async function assignDeliveryTasks(data: AssignDeliveryTasksPayload) {
  return requestClient.post('/mall-order/delivery/task/assign', data);
}

export async function reassignDeliveryTask(id: string, data: ReassignDeliveryTaskPayload) {
  const url = `/mall-order/delivery/task/${id}/reassign`;
  return requestClient.post(`${url}?staffId=${data.staffId}`);
}

/**
 * 关闭异常任务
 */
export async function closeDeliveryTask(id: string, reason: string) {
  return requestClient.post(`/mall-order/delivery/task/${id}/close?reason=${encodeURIComponent(reason)}`);
}

/**
 * 置为待退回
 */
export async function returnPendingDeliveryTask(id: string) {
  return requestClient.post(`/mall-order/delivery/task/${id}/return-pending`);
}

/**
 * 确认商品退回
 */
export async function returnConfirmDeliveryTask(id: string, remark?: string) {
  const url = `/mall-order/delivery/task/${id}/return-confirm`;
  return requestClient.post(remark ? `${url}?remark=${encodeURIComponent(remark)}` : url);
}

/**
 * 查询任务凭证
 */
export async function getDeliveryTaskEvidence(id: string) {
  return requestClient.get(`/mall-order/delivery/task/${id}/evidence`);
}

/**
 * 查询任务操作日志
 */
export async function getDeliveryTaskLogs(id: string) {
  return requestClient.get(`/mall-order/delivery/task/${id}/logs`);
}
