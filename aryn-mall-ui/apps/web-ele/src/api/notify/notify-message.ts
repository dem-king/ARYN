import { requestClient } from '#/api/request';

const BASE = '/admin/notify/notifymessage';

/**
 * 获取消息记录分页
 */
export async function getPage(query: any) {
  return requestClient.get(`${BASE}/page`, { params: query });
}
