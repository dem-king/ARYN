import { requestClient } from '#/api/request';

const BASE = '/admin/notify/notifybroadcast';

/**
 * 发送群发消息
 */
export async function sendBroadcast(data: any) {
  return requestClient.post(`${BASE}/send`, data);
}

/**
 * 群发记录列表
 */
export async function getPage(query: any) {
  return requestClient.get(`${BASE}/page`, { params: query });
}

/**
 * 群发记录详情
 */
export async function getById(id: string) {
  return requestClient.get(`${BASE}/${id}`);
}
