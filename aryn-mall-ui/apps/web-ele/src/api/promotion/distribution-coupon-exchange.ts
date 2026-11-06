import { requestClient } from '#/api/request';

/** 兑换状态：0 发放中 / 1 已发放 / 2 发放失败 */
export type DistributionCouponExchangeStatus = '0' | '1' | '2';

/** 券兑换记录视图（DistributionCouponExchangeVO = entity + userNickname） */
export interface DistributionCouponExchangeRecord {
  /** 消耗佣金 */
  commissionAmount?: number;
  /** 发放的 coupon_user 主键，逗号分隔 */
  couponUserIds?: string;
  createTime?: string;
  /** 兑换时间 */
  exchangeTime?: string;
  /** 券面额 */
  faceAmount?: number;
  id: string;
  nickname?: string;
  packId: string;
  packName?: string;
  remark?: string;
  status: DistributionCouponExchangeStatus;
  userId: string;
  /** 分销员当前昵称 */
  userNickname?: string;
}

export interface DistributionCouponExchangeQuery {
  createTimeEnd?: string;
  createTimeStart?: string;
  current: number;
  packId?: string;
  size: number;
  status?: '' | DistributionCouponExchangeStatus;
  userId?: string;
}

export interface DistributionCouponExchangePage {
  current: number;
  records: DistributionCouponExchangeRecord[];
  size: number;
  total: number;
}

/** 券兑换成本汇总（DistributionCouponCostVO） */
export interface DistributionCouponCost {
  /** 消耗佣金合计 */
  commissionAmount?: number;
  /** 兑换笔数 */
  exchangeCount?: number;
  /** 券面额合计（营销费用口径） */
  faceAmount?: number;
}

/**
 * 获取券兑换记录分页
 */
export async function getPage(query: DistributionCouponExchangeQuery) {
  return requestClient.get<DistributionCouponExchangePage>(
    '/promotion/distribution/coupon/exchange/page',
    { params: query },
  );
}

/**
 * 获取券兑换成本汇总（券面额/消耗佣金/笔数）
 */
export async function getStatistics(
  query: Partial<DistributionCouponExchangeQuery>,
) {
  return requestClient.get<DistributionCouponCost>(
    '/promotion/distribution/coupon/exchange/statistics',
    { params: query },
  );
}
