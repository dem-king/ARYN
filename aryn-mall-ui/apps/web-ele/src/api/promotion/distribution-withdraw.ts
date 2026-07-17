import { requestClient } from '#/api/request';

export type DistributionWithdrawStatus = '0' | '1' | '2';

export interface DistributionWithdrawRecord {
  accountName?: string;
  accountNo?: string;
  accountType?: string;
  amount: number;
  auditBy?: string;
  auditTime?: string;
  createTime?: string;
  id: string;
  payoutBy?: string;
  payoutNo?: string;
  payoutTime?: string;
  rejectReason?: string;
  status: DistributionWithdrawStatus;
  userId: string;
  withdrawNo: string;
}

export interface DistributionWithdrawQuery {
  current: number;
  desc?: string;
  size: number;
  status?: '' | DistributionWithdrawStatus;
  userId?: string;
}

export interface DistributionWithdrawPage {
  current: number;
  records: DistributionWithdrawRecord[];
  size: number;
  total: number;
}

export interface DistributionWithdrawAuditPayload {
  id: string;
  payoutNo?: string;
  rejectReason?: string;
  status: Exclude<DistributionWithdrawStatus, '0'>;
}

/**
 * 获取分销提现分页
 */
export async function getPage(query: DistributionWithdrawQuery) {
  return requestClient.get<DistributionWithdrawPage>(
    '/promotion/distribution/withdraw/page',
    {
      params: query,
    },
  );
}

/**
 * 根据ID获取分销提现
 */
export async function getById(id: string) {
  return requestClient.get<DistributionWithdrawRecord>(
    `/promotion/distribution/withdraw/${id}`,
  );
}

/**
 * 审核分销提现
 */
export async function audit(data: DistributionWithdrawAuditPayload) {
  return requestClient.post<boolean>(
    '/promotion/distribution/withdraw/audit',
    data,
  );
}
