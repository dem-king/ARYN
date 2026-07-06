import { requestClient } from '#/api/request';

/**
 * 获取会员等级分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/memberlevel/page', { params: query });
}

/**
 * 获取会员等级全量列表
 */
export async function getList() {
  return requestClient.get('/mall-user/memberlevel/list');
}

/**
 * 根据ID获取会员等级
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/memberlevel/${id}`);
}

/**
 * 添加会员等级
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/memberlevel', data);
}

/**
 * 编辑会员等级
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/memberlevel', data);
}

/**
 * 删除会员等级
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/memberlevel/${id}`);
}

/**
 * 获取等级变更记录分页
 */
export async function getRecordPage(query: any) {
  return requestClient.get('/mall-user/memberlevel/record/page', {
    params: query,
  });
}

/**
 * 获取付费会员订单分页
 */
export async function getPaidOrderPage(query: any) {
  return requestClient.get('/mall-user/member-paid-order/page', {
    params: query,
  });
}
