import { requestClient } from '#/api/request';

/**
 * 获取素材分组分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/materialgroup/page', { params: query });
}

/**
 * 获取素材分组列表
 */
export async function getList(query: any) {
  return requestClient.get('/upms/materialgroup/list', { params: query });
}

/**
 * 添加素材分组
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/materialgroup', data);
}

/**
 * 编辑素材分组
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/materialgroup', data);
}

/**
 * 删除素材分组
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/materialgroup/${id}`);
}
