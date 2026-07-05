import { requestClient } from '#/api/request';

const BASE = '/admin/notify/notifytemplate';

/**
 * 获取消息模板分页
 */
export async function getPage(query: any) {
  return requestClient.get(`${BASE}/page`, { params: query });
}

/**
 * 根据ID获取消息模板
 */
export async function getById(id: string) {
  return requestClient.get(`${BASE}/${id}`);
}

/**
 * 新增消息模板
 */
export async function addObj(data: any) {
  return requestClient.post(BASE, data);
}

/**
 * 修改消息模板
 */
export async function editObj(data: any) {
  return requestClient.put(BASE, data);
}

/**
 * 删除消息模板
 */
export async function delObj(id: string) {
  return requestClient.delete(`${BASE}/${id}`);
}
