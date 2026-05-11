import { requestClient } from '#/api/request';

/**
 * 获取物流公司分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/logisticscompany/page', { params: query });
}

/**
 * 获取物流公司列表
 */
export async function getList(query: any) {
  return requestClient.get('/upms/logisticscompany/list', { params: query });
}

/**
 * 根据ID获取物流公司
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/logisticscompany/${id}`);
}

/**
 * 添加物流公司
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/logisticscompany', data);
}

/**
 * 编辑物流公司
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/logisticscompany', data);
}

/**
 * 删除物流公司
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/logisticscompany/${id}`);
}
