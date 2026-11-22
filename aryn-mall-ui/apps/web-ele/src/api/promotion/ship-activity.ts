import { requestClient } from '#/api/request';

export interface ShipActivity {
  activityName: string;
  activityType: string;
  endTime: string;
  id?: string;
  priority?: number;
  purchaseScene?: string;
  rules: string;
  revision?: number;
  expectedRevision?: number;
  scopeType: string;
  scopeValue?: string;
  startTime: string;
  status?: string;
}

export async function getActivityPage(query: any) {
  return requestClient.get('/promotion/ship-activity/page', { params: query });
}

export async function getActivity(id: string) {
  return requestClient.get(`/promotion/ship-activity/${id}`);
}

export async function addActivity(data: Partial<ShipActivity>) {
  return requestClient.post('/promotion/ship-activity', data);
}

export async function updateActivity(id: string, data: Partial<ShipActivity>) {
  return requestClient.put(`/promotion/ship-activity/${id}`, data);
}

export async function getPublishConflicts(id: string) {
  return requestClient.get(`/promotion/ship-activity/${id}/conflicts`);
}

export async function publishActivity(
  id: string,
  expectedRevision: number,
  force = false,
) {
  return requestClient.post(`/promotion/ship-activity/${id}/publish`, null, {
    params: { expectedRevision, force },
  });
}

export async function pauseActivity(id: string, expectedRevision: number) {
  return requestClient.post(`/promotion/ship-activity/${id}/pause`, null, {
    params: { expectedRevision },
  });
}

export async function deleteActivity(id: string, expectedRevision: number) {
  return requestClient.delete(`/promotion/ship-activity/${id}`, {
    params: { expectedRevision },
  });
}

/** 命中试算：模拟购物车与上下文，返回命中活动与金额（不落库） */
export async function dryRunActivity(data: any) {
  return requestClient.post('/promotion/ship-activity/dry-run', data);
}

/** 靠港日历 */
export async function getCallCalendar(start: string, end: string) {
  return requestClient.get('/vessel/admin/calls/calendar', {
    params: { start, end },
  });
}

/** 港口配送看板 */
export async function getPortBoard(portCode: string, date?: string) {
  return requestClient.get('/mall-order/fulfillment/port-board', {
    params: { portCode, date },
  });
}

export async function copyActivity(id: string, expectedRevision: number) {
  return requestClient.post(`/promotion/ship-activity/${id}/copy`, null, {
    params: { expectedRevision },
  });
}
