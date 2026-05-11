import { requestClient } from '#/api/request';

/**
 * 获取文件配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/storage-config/page', { params: query });
}

/**
 * 根据ID获取文件配置
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/storage-config/${id}`);
}

/**
 * 添加文件配置
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/storage-config', data);
}

/**
 * 编辑文件配置
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/storage-config', data);
}

/**
 * 删除文件配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/storage-config/${id}`);
}
