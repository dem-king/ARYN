import { requestClient } from '#/api/request';

/**
 * 出车单状态：1-待配货 2-配货中 3-配送中 4-已完成
 */
export type DeliveryTripStatus = '1' | '2' | '3' | '4';

/**
 * 出车单查询参数
 */
export interface DeliveryTripPageQuery {
  current?: number;
  size?: number;
  asc?: string;
  desc?: string;
  /** 出车单号 */
  tripNo?: string;
  /** 配送员ID */
  staffId?: string;
  /** 状态 */
  status?: DeliveryTripStatus;
}

/**
 * 出车单实体
 */
export interface DeliveryTrip {
  id: string;
  /** 出车单号 */
  tripNo: string;
  /** 配送员ID */
  staffId: string;
  /** 配送员姓名 */
  staffName: string;
  /** 状态 */
  status: DeliveryTripStatus;
  /** 任务数 */
  taskCount: number;
  /** 仓库地址 */
  warehouseAddress?: string;
  /** 开始配货时间 */
  startLoadTime?: string;
  /** 出发时间 */
  departTime?: string;
  /** 完成时间 */
  completeTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 获取出车单分页列表
 */
export async function getDeliveryTripPage(params: DeliveryTripPageQuery) {
  return requestClient.get('/mall-order/delivery/trip/page', { params });
}

/**
 * 根据ID获取出车单详情（含关联配送任务列表、取货清单汇总）
 */
export async function getDeliveryTripDetail(id: string) {
  return requestClient.get(`/mall-order/delivery/trip/${id}`);
}
