import { requestClient } from '#/api/request';

/**
 * 获取会员权益分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/memberbenefit/page', { params: query });
}

/**
 * 根据ID获取会员权益
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/memberbenefit/${id}`);
}

/**
 * 添加会员权益
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/memberbenefit', data);
}

/**
 * 编辑会员权益
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/memberbenefit', data);
}

/**
 * 删除会员权益
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/memberbenefit/${id}`);
}

/**
 * 绑定权益等级
 *
 * 后端签名为 bindLevels(@RequestParam benefitId, @RequestBody List<String> levelIds)：
 * body 必须是等级 ID 数组，benefitId 走查询参数。
 */
export async function bindLevels(benefitId: string, levelIds: string[]) {
  return requestClient.post('/mall-user/memberbenefit/bindLevels', levelIds, {
    params: { benefitId },
  });
}

/**
 * 获取等级权益列表
 */
export async function getLevelBenefits(levelId: string) {
  return requestClient.get('/mall-user/memberbenefit/levelBenefits', {
    params: { levelId },
  });
}
