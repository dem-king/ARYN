import { requestClient } from '#/api/request';

/**
 * 获取用户所有菜单
 */
export async function getAllMenusApi() {
  return requestClient.get<any[]>('/upms/menu', {
    headers: {
      isSwitchTenant: false,
    },
  });
}
