import { requestClient } from '#/api/request';

/**
 * 配送员状态：1-在线 2-忙碌 3-离线
 */
export type DeliveryStaffStatus = '1' | '2' | '3';

/**
 * 配送员查询参数
 */
export interface DeliveryStaffPageQuery {
  current?: number;
  size?: number;
  asc?: string;
  desc?: string;
  /** 姓名或手机号模糊搜索 */
  keyword?: string;
  status?: DeliveryStaffStatus;
}

/**
 * 配送员实体
 */
export interface DeliveryStaff {
  id: string;
  /** 关联后台用户ID */
  userId?: string;
  /** 关联后台用户昵称 */
  userName?: string;
  /** 姓名 */
  staffName: string;
  /** 手机号 */
  staffPhone: string;
  /** 车辆信息 */
  vehicleInfo?: string;
  /** 状态：1-在线 2-忙碌 3-离线 */
  status: DeliveryStaffStatus;
  createTime?: string;
  updateTime?: string;
}

/**
 * 获取配送员分页列表
 */
export async function getDeliveryStaffPage(params: DeliveryStaffPageQuery) {
  return requestClient.get('/mall-order/delivery/staff/page', { params });
}

/**
 * 根据ID获取配送员详情
 */
export async function getDeliveryStaffById(id: string) {
  return requestClient.get(`/mall-order/delivery/staff/${id}`);
}

/**
 * 新增配送员
 */
export async function createDeliveryStaff(data: Partial<DeliveryStaff>) {
  return requestClient.post('/mall-order/delivery/staff', data);
}

/**
 * 编辑配送员
 */
export async function updateDeliveryStaff(data: Partial<DeliveryStaff>) {
  return requestClient.put('/mall-order/delivery/staff', data);
}

/**
 * 删除配送员
 */
export async function deleteDeliveryStaff(id: string) {
  return requestClient.delete(`/mall-order/delivery/staff/${id}`);
}

/**
 * 切换配送员状态
 */
export async function updateDeliveryStaffStatus(
  id: string,
  status: DeliveryStaffStatus,
) {
  const url = `/mall-order/delivery/staff/${id}/status`;
  return requestClient.put(`${url}?status=${status}`);
}

/**
 * 获取全部在线配送员（用于派单下拉选择）
 */
export async function getDeliveryStaffList(params?: {
  status?: DeliveryStaffStatus;
}) {
  return requestClient.get('/mall-order/delivery/staff/list', { params });
}
