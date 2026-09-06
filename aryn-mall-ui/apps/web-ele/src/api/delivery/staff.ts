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

/**
 * 绑定状态：bound-已绑定 unbound-未绑定
 */
export type DeliveryBindingStatus = 'bound' | 'unbound';

/**
 * 权限状态：granted-已开通 missing-未开通 disabled-账号停用
 */
export type DeliveryPermissionStatus = 'disabled' | 'granted' | 'missing';

/**
 * 配送员管理列表行（含员工账号、商城绑定与权限摘要）
 */
export interface DeliveryStaffManager {
  id: string;
  /** 员工账号ID */
  userId?: string;
  staffName: string;
  staffPhone: string;
  /** 接单状态：1-在线 2-忙碌 3-离线 */
  status: DeliveryStaffStatus;
  vehicleInfo?: string;
  /** 员工账号昵称/用户名 */
  sysUserName?: string;
  /** 员工账号状态：0-正常 1-停用 */
  sysUserStatus?: string;
  /** 是否拥有配送执行权限 */
  deliveryPermission?: boolean;
  bindingStatus: DeliveryBindingStatus;
  mallUserId?: string;
  mallUserNickname?: string;
  mallUserPhone?: string;
  bindTime?: string;
  /** 进行中任务数 */
  pendingTaskCount?: number;
  createTime?: string;
}

/**
 * 配送员管理分页查询参数
 */
export interface DeliveryStaffManagerQuery {
  current?: number;
  size?: number;
  /** 姓名/手机号模糊 */
  keyword?: string;
  /** 绑定状态 */
  bindingStatus?: DeliveryBindingStatus;
  /** 接单状态 */
  status?: DeliveryStaffStatus;
}

/**
 * 配送员管理分页列表（含绑定与权限摘要）
 */
export async function getDeliveryStaffManagerPage(
  params: DeliveryStaffManagerQuery,
) {
  return requestClient.get('/mall-order/delivery/staff/manager-page', {
    params,
  });
}

/**
 * 向导式创建配送员请求
 */
export interface DeliveryOnboardData {
  /** 员工账号ID */
  userId: string;
  staffName?: string;
  staffPhone?: string;
  vehicleInfo?: string;
  /** 商城用户ID（选填，同时完成绑定） */
  mallUserId?: string;
  status?: DeliveryStaffStatus;
  /** 是否同时开通配送资格（默认 true） */
  grantQualification?: boolean;
}

/**
 * 向导式创建结果摘要
 */
export interface DeliveryOnboardResult {
  staffId: string;
  userId: string;
  /** BOUND已绑定 UNBOUND未绑定 */
  bindingStatus: 'BOUND' | 'UNBOUND';
  /** GRANTED已开通 PROCESSING开通处理中（后台自动重试） NOT_GRANTED未申请 GRANT_FAILED开通失败 */
  qualificationStatus:
    'GRANT_FAILED' | 'GRANTED' | 'NOT_GRANTED' | 'PROCESSING';
  qualificationMessage?: string;
}

/**
 * 向导式创建配送员（校验员工账号、创建资料、可选绑定商城账号与开通资格）
 */
export async function onboardDeliveryStaff(
  data: DeliveryOnboardData,
): Promise<DeliveryOnboardResult> {
  return requestClient.post('/mall-order/delivery/staff/onboard', data);
}

/**
 * 绑定商城账号
 */
export async function bindDeliveryStaffMallUser(
  id: string,
  mallUserId: string,
) {
  return requestClient.put(`/mall-order/delivery/staff/${id}/binding`, {
    mallUserId,
  });
}

/**
 * 解绑商城账号
 */
export async function unbindDeliveryStaffMallUser(id: string) {
  return requestClient.delete(`/mall-order/delivery/staff/${id}/binding`);
}

/**
 * 修改接单状态（离线的配送员不出现在默认派单候选中）
 */
export async function updateDeliveryStaffAvailability(
  id: string,
  status: DeliveryStaffStatus,
) {
  return requestClient.put(
    `/mall-order/delivery/staff/${id}/availability?status=${status}`,
  );
}

/**
 * 开通或停用配送资格（授予/回收配送员角色）
 */
export async function updateDeliveryStaffQualification(
  id: string,
  enabled: boolean,
) {
  return requestClient.put(
    `/mall-order/delivery/staff/${id}/qualification?enabled=${enabled}`,
  );
}

/**
 * 可绑定商城用户搜索结果
 */
export interface MallUserForBinding {
  mallUserId: string;
  nickname?: string;
  /** 脱敏手机号 */
  phone?: string;
  bindingStatus: DeliveryBindingStatus;
  /** 已绑定的配送员姓名（冲突提示用） */
  boundStaffName?: string;
}

/**
 * 按手机号/昵称/用户ID搜索可绑定的商城用户
 */
export async function searchMallUserForBinding(keyword?: string) {
  return requestClient.get(
    '/mall-order/delivery/staff/binding-mall-user-search',
    { params: { keyword } },
  );
}

/**
 * 可选员工账号搜索结果（向导式创建用）
 */
export interface SysUserForOnboard {
  userId: string;
  nickname?: string;
  username?: string;
  /** 脱敏手机号 */
  phone?: string;
  /** 是否已存在配送员资料（已存在不可重复选择） */
  deliveryExists: boolean;
  /** 是否已开通配送执行权限 */
  deliveryPermission: boolean;
}

/**
 * 按用户名/昵称/手机号搜索可选员工账号
 */
export async function searchSysUserForOnboard(keyword?: string) {
  return requestClient.get(
    '/mall-order/delivery/staff/onboard-sys-user-search',
    { params: { keyword } },
  );
}
