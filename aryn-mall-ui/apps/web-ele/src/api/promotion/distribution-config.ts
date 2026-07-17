import { requestClient } from '#/api/request';

export interface DistributionConfigRecord {
  commissionRate: number;
  commissionRateLevel2: number;
  configName: string;
  createTime?: string;
  id: string;
  minWithdrawAmount: number;
  settleCycleDays: number;
  status: '0' | '1';
}

export interface DistributionConfigQuery {
  configName?: string;
  current: number;
  desc?: string;
  size: number;
}

export interface DistributionConfigPage {
  current: number;
  records: DistributionConfigRecord[];
  size: number;
  total: number;
}

export type DistributionConfigPayload = Omit<
  DistributionConfigRecord,
  'createTime' | 'id'
> & { id?: string };

/**
 * 获取分销配置分页
 */
export async function getPage(query: DistributionConfigQuery) {
  return requestClient.get<DistributionConfigPage>(
    '/promotion/distribution/config/page',
    {
      params: query,
    },
  );
}

/**
 * 根据ID获取分销配置
 */
export async function getById(id: string) {
  return requestClient.get<DistributionConfigRecord>(
    `/promotion/distribution/config/${id}`,
  );
}

/**
 * 新增分销配置
 */
export async function addObj(data: DistributionConfigPayload) {
  return requestClient.post<boolean>('/promotion/distribution/config', data);
}

/**
 * 编辑分销配置
 */
export async function editObj(data: DistributionConfigPayload) {
  return requestClient.put<boolean>('/promotion/distribution/config', data);
}

/**
 * 删除分销配置
 */
export async function delObj(id: string) {
  return requestClient.delete<boolean>(`/promotion/distribution/config/${id}`);
}

/**
 * 启用分销配置
 */
export async function enableConfig(id: string) {
  return requestClient.put<boolean>(
    `/promotion/distribution/config/enable/${id}`,
  );
}
