import { requestClient } from '#/api/request';

/** 销售全局配置（sales_config，每租户单行） */
export interface SalesConfigRecord {
  createTime?: string;
  /** 全局默认提成比例（0-1），默认 0.05 */
  defaultCommissionRate?: number;
  id?: string;
  /** 每月台账生成日（1-28） */
  monthlyCutoffDay?: number;
  /** 认领有效期（天），默认 90 */
  claimExpireDays?: number;
  /** 退款风险观察期（天），默认 7 */
  refundRiskDays?: number;
  /** 转交奖励比例（0-1），0 表示不启用 */
  referralRewardRate?: number;
  status?: '0' | '1';
  updateTime?: string;
}

/**
 * 获取销售全局配置（不存在时后端返回默认值对象）
 */
export async function getConfig() {
  return requestClient.get<SalesConfigRecord>('/promotion/sales/config');
}

/**
 * 保存销售全局配置
 */
export async function saveConfig(data: SalesConfigRecord) {
  return requestClient.put<boolean>('/promotion/sales/config', data);
}
