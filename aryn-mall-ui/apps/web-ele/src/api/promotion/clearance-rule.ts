import { requestClient } from '#/api/request';

/** 出清折扣规则列表 */
export async function getClearanceRules() {
  return requestClient.get('/promotion/clearance-rule/list');
}

/** 新增出清折扣规则 */
export async function addClearanceRule(data: any) {
  return requestClient.post('/promotion/clearance-rule', data);
}

/** 修改出清折扣规则 */
export async function updateClearanceRule(id: string, data: any) {
  return requestClient.put(`/promotion/clearance-rule/${id}`, data);
}

/** 删除出清折扣规则 */
export async function deleteClearanceRule(id: string) {
  return requestClient.delete(`/promotion/clearance-rule/${id}`);
}
