import { requestClient } from '#/api/request';

/** 券包状态：0 上架 / 1 下架 */
export type DistributionCouponPackStatus = '0' | '1';

/** 券包内优惠券模板明细（DistributionCouponPackVO.couponInfos） */
export interface DistributionCouponPackItem {
  couponInfoId: string;
  /** 券模板面额 */
  faceAmount?: number;
  /** 券名称 */
  name?: string;
  /** 数量 */
  quantity?: number;
}

/** 券包视图（DistributionCouponPackVO = entity + couponInfos） */
export interface DistributionCouponPackRecord {
  couponInfos?: DistributionCouponPackItem[];
  createTime?: string;
  /** 券包面额 */
  faceAmount?: number;
  id: string;
  packName: string;
  /** 消耗佣金 */
  requiredCommission?: number;
  remark?: string;
  sort?: number;
  /** 库存；NULL=不限 */
  stock?: null | number;
  status: DistributionCouponPackStatus;
  /** 使用门槛 */
  thresholdAmount?: number;
  /** 有效期（天） */
  validDays?: number;
}

export interface DistributionCouponPackQuery {
  current: number;
  desc?: string;
  packName?: string;
  size: number;
  status?: '' | DistributionCouponPackStatus;
}

export interface DistributionCouponPackPage {
  current: number;
  records: DistributionCouponPackRecord[];
  size: number;
  total: number;
}

/** 券包保存请求（DistributionCouponPackSaveDTO） */
export interface DistributionCouponPackSavePayload {
  faceAmount: number;
  id?: string;
  items: { couponInfoId: string; quantity: number }[];
  packName: string;
  remark?: string;
  requiredCommission: number;
  sort?: number;
  status?: DistributionCouponPackStatus;
  /** 库存；null=不限 */
  stock?: null | number;
  thresholdAmount?: number;
  validDays?: number;
}

/**
 * 获取券包分页
 */
export async function getPage(query: DistributionCouponPackQuery) {
  return requestClient.get<DistributionCouponPackPage>(
    '/promotion/distribution/coupon/pack/page',
    { params: query },
  );
}

/**
 * 获取全部券包（下拉选择用，不分页）
 */
export async function getList() {
  return requestClient.get<DistributionCouponPackRecord[]>(
    '/promotion/distribution/coupon/pack/list',
  );
}

/**
 * 根据ID获取券包
 */
export async function getById(id: string) {
  return requestClient.get<DistributionCouponPackRecord>(
    `/promotion/distribution/coupon/pack/${id}`,
  );
}

/**
 * 新增券包
 */
export async function addObj(data: DistributionCouponPackSavePayload) {
  return requestClient.post<boolean>(
    '/promotion/distribution/coupon/pack/add',
    data,
  );
}

/**
 * 编辑券包
 */
export async function editObj(data: DistributionCouponPackSavePayload) {
  return requestClient.put<boolean>(
    '/promotion/distribution/coupon/pack/edit',
    data,
  );
}

/**
 * 券包上下架
 */
export async function updateStatus(data: {
  id: string;
  status: DistributionCouponPackStatus;
}) {
  return requestClient.post<boolean>(
    '/promotion/distribution/coupon/pack/status',
    data,
  );
}

/**
 * 逻辑删除券包
 */
export async function delObj(id: string) {
  return requestClient.delete<boolean>(
    `/promotion/distribution/coupon/pack/${id}`,
  );
}
