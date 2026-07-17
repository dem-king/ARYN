import { requestClient } from '#/api/request';

export interface DistributionUserRecord {
  availableCommission: number;
  avatar?: string;
  commissionDebt: number;
  createTime?: string;
  frozenCommission: number;
  id: string;
  inviterUserId?: string;
  nickname?: string;
  pendingCommission: number;
  status: '0' | '1';
  subordinateCount: number;
  totalCommission: number;
  userId: string;
  withdrawnCommission: number;
}

export interface DistributionUserQuery {
  current: number;
  inviterUserId?: string;
  size: number;
  userId?: string;
}

export interface DistributionUserPage {
  current: number;
  records: DistributionUserRecord[];
  size: number;
  total: number;
}

export interface DistributionUserRegisterPayload {
  avatar?: string;
  inviterUserId?: string;
  nickname?: string;
  userId: string;
}

/**
 * 获取分销用户分页
 */
export async function getPage(query: DistributionUserQuery) {
  return requestClient.get<DistributionUserPage>(
    '/promotion/distribution/user/page',
    {
      params: query,
    },
  );
}

/**
 * 根据ID获取分销用户
 */
export async function getById(id: string) {
  return requestClient.get<DistributionUserRecord>(
    `/promotion/distribution/user/${id}`,
  );
}

/**
 * 注册分销用户
 */
export async function register(data: DistributionUserRegisterPayload) {
  return requestClient.post<DistributionUserRecord>(
    '/promotion/distribution/user/register',
    data,
  );
}

/**
 * 启用分销用户
 */
export async function enable(userId: string) {
  return requestClient.put<boolean>(
    `/promotion/distribution/user/enable/${userId}`,
  );
}

/**
 * 禁用分销用户
 */
export async function disable(userId: string) {
  return requestClient.put<boolean>(
    `/promotion/distribution/user/disable/${userId}`,
  );
}

/**
 * 删除分销用户
 */
export async function delObj(id: string) {
  return requestClient.delete<boolean>(`/promotion/distribution/user/${id}`);
}
