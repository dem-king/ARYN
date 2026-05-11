import { requestClient } from '#/api/request';

/**
 * 获取角色分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/sysrole/page', {
    params: query,
  });
}

/**
 * 根据ID获取角色
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/sysrole/${id}`);
}

/**
 * 获取角色列表
 */
export async function getList(query: any) {
  return requestClient.get('/upms/sysrole/list', {
    params: query,
  });
}

/**
 * 添加角色
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/sysrole', data);
}

/**
 * 编辑角色
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/sysrole', data);
}

/**
 * 删除角色
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/sysrole/${id}`);
}

/**
 * 保存角色菜单
 */
export async function saveRoleMenu(data: any) {
  return requestClient.post('/upms/sysrole/role/menu', data);
}
