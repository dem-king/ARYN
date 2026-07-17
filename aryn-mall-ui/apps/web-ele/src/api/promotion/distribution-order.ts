import { requestClient } from '#/api/request';

export interface DistributionOrderRecord {
  bizOrderId: string;
  buyerUserId: string;
  commissionAmount: number;
  commissionBaseAmount?: number;
  commissionLevel: number;
  createTime?: string;
  distributorUserId: string;
  freightAmount: number;
  id: string;
  orderAmount: number;
  refundedBaseAmount: number;
  refundedCommissionAmount: number;
  settleAt?: string;
  settleTime?: string;
  status: '0' | '1' | '2';
}

export interface DistributionOrderQuery {
  bizOrderId?: string;
  current: number;
  desc?: string;
  distributorUserId?: string;
  size: number;
}

export interface DistributionOrderPage {
  current: number;
  records: DistributionOrderRecord[];
  size: number;
  total: number;
}

/**
 * 获取分销订单分页
 */
export async function getPage(query: DistributionOrderQuery) {
  return requestClient.get<DistributionOrderPage>(
    '/promotion/distribution/order/page',
    {
      params: query,
    },
  );
}

/**
 * 根据ID获取分销订单
 */
export async function getById(id: string) {
  return requestClient.get<DistributionOrderRecord>(
    `/promotion/distribution/order/${id}`,
  );
}

/**
 * 手动触发结算
 */
export async function settleOrder(id: string) {
  return requestClient.post<boolean>(
    `/promotion/distribution/order/settle/${id}`,
  );
}
