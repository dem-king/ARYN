import { requestClient } from '#/api/request';

/** 采购档期列表 */
export async function getTradeCalendars(params?: any) {
  return requestClient.get('/promotion/trade-calendar/list', { params });
}

/** 档期详情 */
export async function getTradeCalendarDetail(id: string) {
  return requestClient.get(`/promotion/trade-calendar/${id}`);
}

/** 新建档期 */
export async function addTradeCalendar(data: any) {
  return requestClient.post('/promotion/trade-calendar', data);
}

/** 修改档期 */
export async function updateTradeCalendar(id: string, data: any) {
  return requestClient.put(`/promotion/trade-calendar/${id}`, data);
}

/** 切换档期状态（targetStatus：1生效 2停用 0草稿） */
export async function changeTradeCalendarStatus(
  id: string,
  targetStatus: string,
) {
  return requestClient.put(
    `/promotion/trade-calendar/${id}/status/${targetStatus}`,
  );
}

/** 删除档期 */
export async function deleteTradeCalendar(id: string) {
  return requestClient.delete(`/promotion/trade-calendar/${id}`);
}
