import { requestClient } from '#/api/request';

/**
 * 获取页面设计分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/pagedesign/page', { params: query });
}

/**
 * 根据ID获取页面设计
 */
export async function getById(id: string) {
  return requestClient.get(`/promotion/pagedesign/${id}`);
}

/**
 * 添加页面设计
 */
export async function addObj(data: any) {
  return requestClient.post('/promotion/pagedesign', data);
}

/**
 * 删除页面设计
 */
export async function delObj(id: string) {
  return requestClient.delete(`/promotion/pagedesign/${id}`);
}

/**
 * 编辑页面设计
 */
export async function editObj(data: any) {
  return requestClient.put('/promotion/pagedesign', data);
}
