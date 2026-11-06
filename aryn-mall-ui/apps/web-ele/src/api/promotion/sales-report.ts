import { requestClient } from '#/api/request';

/** 销售业绩报表行（SalesReportVO） */
export interface SalesReportRecord {
  /** 应发提成 */
  commissionAmount?: number;
  deptName?: string;
  orderCount?: number;
  /** 已发放提成 */
  paidAmount?: number;
  /** 退款额 */
  refundAmount?: number;
  salesStaffId: string;
  staffName?: string;
  /** 成交额 */
  tradeAmount?: number;
}

/** 销售业绩趋势行（SalesTrendVO） */
export interface SalesTrendRecord {
  commissionAmount?: number;
  /** 月份（yyyy-MM） */
  month: string;
  orderCount?: number;
  tradeAmount?: number;
}

export interface SalesReportQuery {
  deptId?: string;
  /** 月份（yyyy-MM）；为空时按后端默认口径统计 */
  payoutMonth?: string;
  salesStaffId?: string;
}

/**
 * 获取销售业绩汇总报表
 */
export async function getSummary(query: SalesReportQuery) {
  return requestClient.get<SalesReportRecord[]>(
    '/promotion/sales/report/summary',
    { params: query },
  );
}

/**
 * 获取近 N 个月业绩趋势
 */
export async function getTrend(query: {
  months?: number;
  salesStaffId?: string;
}) {
  return requestClient.get<SalesTrendRecord[]>(
    '/promotion/sales/report/trend',
    { params: query },
  );
}
