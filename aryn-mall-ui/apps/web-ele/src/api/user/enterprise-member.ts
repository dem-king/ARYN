import { requestClient } from '#/api/request';

/** 企业等级列表 */
export async function getEnterpriseLevels(params?: any) {
  return requestClient.get('/mall-user/enterpriselevel/list', { params });
}

/** 新增企业等级 */
export async function addEnterpriseLevel(data: any) {
  return requestClient.post('/mall-user/enterpriselevel', data);
}

/** 修改企业等级 */
export async function updateEnterpriseLevel(id: string, data: any) {
  return requestClient.put(`/mall-user/enterpriselevel/${id}`, data);
}

/** 删除企业等级 */
export async function deleteEnterpriseLevel(id: string) {
  return requestClient.delete(`/mall-user/enterpriselevel/${id}`);
}

/** 企业权益列表 */
export async function getEnterpriseBenefits(params?: any) {
  return requestClient.get('/mall-user/enterprisebenefit/list', { params });
}

/** 权益挂载的企业等级ID集合 */
export async function getBenefitLevelRels(id: string) {
  return requestClient.get(`/mall-user/enterprisebenefit/${id}/level-rels`);
}

/** 新增企业权益（含挂载等级） */
export async function addEnterpriseBenefit(data: any) {
  return requestClient.post('/mall-user/enterprisebenefit', data);
}

/** 修改企业权益（含挂载等级） */
export async function updateEnterpriseBenefit(id: string, data: any) {
  return requestClient.put(`/mall-user/enterprisebenefit/${id}`, data);
}

/** 删除企业权益 */
export async function deleteEnterpriseBenefit(id: string) {
  return requestClient.delete(`/mall-user/enterprisebenefit/${id}`);
}
