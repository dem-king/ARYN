import { requestClient } from '#/api/request';

/** 销售业绩单状态：0 待确认 / 1 可发放 / 2 已发放 / 3 已全额退款 */
export type SalesOrderStatus = '0' | '1' | '2' | '3';

/** 销售业绩单（SalesOrderVO = SalesOrder + buyerNickname） */
export interface SalesOrderRecord {
  /** 买家昵称（列表回填） */
  buyerNickname?: string;
  buyerUserId: string;
  bizOrderId: string;
  /** 提成基数（实付不含运费） */
  commissionAmount?: number;
  commissionBase?: number;
  /** 支付时比例快照 */
  commissionRate?: number;
  createTime?: string;
  deptId?: string;
  freightAmount?: number;
  id: string;
  orderNo?: string;
  /** 实付（含运费） */
  paymentAmount?: number;
  payTime?: string;
  payoutId?: string;
  remark?: string;
  /** 累计退款基数 */
  refundedBase?: number;
  /** 累计扣回提成 */
  refundedCommission?: number;
  salesStaffId: string;
  staffName?: string;
  /** 计划可发放（风险观察期止） */
  settleAt?: string;
  /** 实际可发放 */
  settleTime?: string;
  status: SalesOrderStatus;
}

export interface SalesOrderQuery {
  buyerUserId?: string;
  current: number;
  deptId?: string;
  orderNo?: string;
  /** 支付时间结束（yyyy-MM-dd HH:mm:ss） */
  payTimeEnd?: string;
  /** 支付时间开始（yyyy-MM-dd HH:mm:ss） */
  payTimeStart?: string;
  payoutId?: string;
  salesStaffId?: string;
  size: number;
  staffName?: string;
  status?: '' | SalesOrderStatus;
}

export interface SalesOrderPage {
  current: number;
  records: SalesOrderRecord[];
  size: number;
  total: number;
}

/** 业绩单汇总卡片（/sales/order/statistics） */
export interface SalesOrderStatistics {
  commissionAmount: number;
  orderCount: number;
  refundAmount: number;
  tradeAmount: number;
}

/**
 * 获取销售业绩单分页
 */
export async function getPage(query: SalesOrderQuery) {
  return requestClient.get<SalesOrderPage>('/promotion/sales/order/page', {
    params: query,
  });
}

/**
 * 获取销售业绩单汇总（当前筛选条件下的单数/成交额/提成/退款）
 */
export async function getStatistics(query: Partial<SalesOrderQuery>) {
  return requestClient.get<SalesOrderStatistics>(
    '/promotion/sales/order/statistics',
    { params: query },
  );
}
