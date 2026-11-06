import type { SalesOrderPage } from './sales-order';

import { requestClient } from '#/api/request';

/** 发放台账状态：0 待确认 / 1 已发放 */
export type SalesPayoutStatus = '0' | '1';

/** 月度发放台账（SalesPayoutVO = SalesPayout） */
export interface SalesPayoutRecord {
  /** 应发提成（Σ提成-已扣回） */
  commissionAmount?: number;
  confirmBy?: string;
  confirmTime?: string;
  createTime?: string;
  deptId?: string;
  id: string;
  orderCount?: number;
  /** 实发金额 = 应发 - 上期退款冲减 */
  payableAmount?: number;
  /** 台账月份（yyyy-MM） */
  payoutMonth: string;
  /** 上期退款冲减（正数） */
  refundDeductAmount?: number;
  remark?: string;
  salesStaffId: string;
  staffName?: string;
  status: SalesPayoutStatus;
  /** 成交额 = Σ(基数-已退基数) */
  tradeAmount?: number;
}

export interface SalesPayoutQuery {
  current: number;
  payoutMonth?: string;
  salesStaffId?: string;
  size: number;
  status?: '' | SalesPayoutStatus;
}

export interface SalesPayoutPage {
  current: number;
  records: SalesPayoutRecord[];
  size: number;
  total: number;
}

/** 月度台账生成请求（SalesPayoutGenerateDTO）：payoutMonth 为空取上月 */
export interface SalesPayoutGeneratePayload {
  payoutMonth?: string;
}

/** 台账确认发放请求（SalesPayoutConfirmDTO） */
export interface SalesPayoutConfirmPayload {
  id: string;
  /** 备注/凭证号 */
  remark?: string;
}

/**
 * 获取月度发放台账分页
 */
export async function getPage(query: SalesPayoutQuery) {
  return requestClient.get<SalesPayoutPage>('/promotion/sales/payout/page', {
    params: query,
  });
}

/**
 * 根据ID获取发放台账
 */
export async function getById(id: string) {
  return requestClient.get<SalesPayoutRecord>(`/promotion/sales/payout/${id}`);
}

/**
 * 获取台账批次内的业绩单分页
 */
export async function getOrderPage(
  id: string,
  query: { current: number; size: number },
) {
  return requestClient.get<SalesOrderPage>(
    `/promotion/sales/payout/${id}/order/page`,
    { params: query },
  );
}

/**
 * 手动生成月度台账（重复生成幂等跳过已存在批次），返回生成的批次数
 */
export async function generate(data: SalesPayoutGeneratePayload) {
  return requestClient.post<number>('/promotion/sales/payout/generate', data);
}

/**
 * 确认发放（财务核对后标记已发放）
 */
export async function confirm(data: SalesPayoutConfirmPayload) {
  return requestClient.post<boolean>('/promotion/sales/payout/confirm', data);
}
