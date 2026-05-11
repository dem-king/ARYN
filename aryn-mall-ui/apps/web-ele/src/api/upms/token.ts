import { requestClient } from '#/api/request';

/**
 * 获取token分页
 */
export async function getPage(query: any) {
  return requestClient.get('/auth/token/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}
