import { requestClient } from '#/api/request';

/** 客户归属来源：SHARE 分享 / QRCODE 推广码 / ADMIN 后台指派 / TRANSFER 离职交接 / CLAIM 首单转交 */
export type SalesBindSource =
  'ADMIN' | 'CLAIM' | 'QRCODE' | 'SHARE' | 'TRANSFER';

/** 待转交认领状态：0 待首单 / 1 已转交 / 2 已失效 */
export type SalesClaimStatus = '0' | '1' | '2';

/** 客户归属（SalesCustomerVO） */
export interface SalesCustomerRecord {
  avatar?: string;
  bindSource?: SalesBindSource;
  bindTime?: string;
  /** 待转交认领失效时间 */
  claimExpireTime?: string;
  /** 待转交认领状态 */
  claimStatus?: SalesClaimStatus;
  customerUserId: string;
  id: string;
  nickname?: string;
  /** 待转交认领销售ID */
  pendingClaimStaffId?: string;
  /** 待转交认领销售姓名 */
  pendingClaimStaffName?: string;
  salesStaffId?: string;
  salesStaffName?: string;
}

export interface SalesCustomerQuery {
  bindSource?: '' | SalesBindSource;
  current: number;
  customerUserId?: string;
  salesStaffId?: string;
  size: number;
}

export interface SalesCustomerPage {
  current: number;
  records: SalesCustomerRecord[];
  size: number;
  total: number;
}

/** 客户归属手动指派/改绑请求（SalesCustomerAssignDTO） */
export interface SalesCustomerAssignPayload {
  customerUserId: string;
  /** 改绑原因（必填） */
  reason: string;
  salesStaffId: string;
}

/** 换绑日志（SalesCustomerBindLog，只追加） */
export interface SalesCustomerBindLogRecord {
  bindSource?: SalesBindSource;
  createTime?: string;
  customerUserId: string;
  id: string;
  newSalesStaffId?: string;
  newSalesStaffName?: string;
  oldSalesStaffId?: string;
  oldSalesStaffName?: string;
  operatorId?: string;
  operatorName?: string;
  reason?: string;
}

export interface SalesCustomerBindLogPage {
  current: number;
  records: SalesCustomerBindLogRecord[];
  size: number;
  total: number;
}

/** 待转交认领（SalesCustomerClaimVO） */
export interface SalesCustomerClaimRecord {
  claimTime?: string;
  customerUserId: string;
  expireTime?: string;
  id: string;
  nickname?: string;
  salesStaffId: string;
  source?: SalesBindSource;
  staffName?: string;
  status: SalesClaimStatus;
}

export interface SalesCustomerClaimQuery {
  current: number;
  salesStaffId?: string;
  size: number;
  status?: '' | SalesClaimStatus;
}

export interface SalesCustomerClaimPage {
  current: number;
  records: SalesCustomerClaimRecord[];
  size: number;
  total: number;
}

/** 客户转交奖励（SalesTransferRewardVO）：0 待结算 / 1 已入账 / 2 已作废 */
export interface SalesTransferRewardRecord {
  bizOrderId?: string;
  createTime?: string;
  customerUserId: string;
  distributorNickname?: string;
  distributorUserId?: string;
  firstOrderAmount?: number;
  id: string;
  nickname?: string;
  rewardAmount?: number;
  rewardRate?: number;
  salesStaffId?: string;
  settleTime?: string;
  status?: '0' | '1' | '2';
}

export interface SalesTransferRewardQuery {
  current: number;
  customerUserId?: string;
  salesStaffId?: string;
  size: number;
  status?: '0' | '1' | '2' | '';
}

export interface SalesTransferRewardPage {
  current: number;
  records: SalesTransferRewardRecord[];
  size: number;
  total: number;
}

/**
 * 获取客户归属分页
 */
export async function getPage(query: SalesCustomerQuery) {
  return requestClient.get<SalesCustomerPage>(
    '/promotion/sales/customer/page',
    {
      params: query,
    },
  );
}

/**
 * 手动指派/改绑客户归属（原因必填）
 */
export async function assign(data: SalesCustomerAssignPayload) {
  return requestClient.post<boolean>('/promotion/sales/customer/assign', data);
}

/**
 * 获取客户换绑日志分页
 */
export async function getBindLogPage(query: {
  current: number;
  customerUserId: string;
  size: number;
}) {
  return requestClient.get<SalesCustomerBindLogPage>(
    '/promotion/sales/customer/bind-log/page',
    { params: query },
  );
}

/**
 * 获取待转交认领分页
 */
export async function getClaimPage(query: SalesCustomerClaimQuery) {
  return requestClient.get<SalesCustomerClaimPage>(
    '/promotion/sales/claim/page',
    { params: query },
  );
}

/**
 * 获取客户转交奖励分页
 */
export async function getTransferRewardPage(query: SalesTransferRewardQuery) {
  return requestClient.get<SalesTransferRewardPage>(
    '/promotion/sales/transfer-reward/page',
    { params: query },
  );
}
