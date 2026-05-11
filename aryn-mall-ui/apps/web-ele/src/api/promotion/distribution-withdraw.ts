import { requestClient } from '#/api/request';

/**
 * 获取分销提现分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/distribution/withdraw/page', {
    params: query,
  });
}

/**
 * 根据ID获取分销提现
 */
export async function getById(id: string) {
  return requestClient.get(`/promotion/distribution/withdraw/${id}`);
}

/**
 * 审核分销提现
 */
export async function audit(data: any) {
  return requestClient.post('/promotion/distribution/withdraw/audit', data);
}
