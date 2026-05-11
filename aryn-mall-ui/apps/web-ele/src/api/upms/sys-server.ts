import { requestClient } from '#/api/request';

/**
 * 获取系统服务信息
 */
export async function getServer() {
  return requestClient.get('/upms/sysserver', {
    headers: {
      isSwitchTenant: false,
    },
  });
}
