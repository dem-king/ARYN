import { requestClient } from '#/api/request';

/**
 * 根据ID获取部门信息
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/sysdept/${id}`);
}

/**
 * 获取部门树列表
 */
export async function getTreeList() {
  return requestClient.get('/upms/sysdept/tree/list');
}

/**
 * 添加部门
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/sysdept', data);
}

/**
 * 编辑部门
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/sysdept', data);
}

/**
 * 删除部门
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/sysdept/${id}`);
}
