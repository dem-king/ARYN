import { requestClient } from '#/api/request';

/** 销售员状态：0 在职 / 1 离职 */
export type SalesStaffStatus = '0' | '1';

/** 销售员（SalesStaffVO） */
export interface SalesStaffRecord {
  /** 个人提成比例（0-1），为空取全局默认比例 */
  commissionRate?: null | number;
  createTime?: string;
  /** 名下客户数（仅分页列表回填） */
  customerCount?: number;
  deptId?: string;
  deptName?: string;
  id: string;
  leaveTime?: string;
  /** 销售本人会员头像（分页/详情回填，远程失败为空） */
  memberAvatar?: string;
  /** 销售本人会员昵称（分页/详情回填，远程失败为空） */
  memberNickname?: string;
  /** 销售本人的小程序会员 ID */
  memberUserId: string;
  phone?: string;
  remark?: string;
  staffName?: string;
  status: SalesStaffStatus;
  sysUserId: string;
  updateTime?: string;
}

export interface SalesStaffQuery {
  current: number;
  deptId?: string;
  desc?: string;
  phone?: string;
  size: number;
  staffName?: string;
  status?: '' | SalesStaffStatus;
}

export interface SalesStaffPage {
  current: number;
  records: SalesStaffRecord[];
  size: number;
  total: number;
}

/** 销售员登记/编辑请求（SalesStaffSaveDTO） */
export interface SalesStaffSavePayload {
  commissionRate?: null | number;
  id?: string;
  memberUserId: string;
  remark?: string;
  sysUserId: string;
}

/** 销售离职交接请求（SalesStaffResignDTO） */
export interface SalesStaffResignPayload {
  leaveTime?: string;
  reason?: string;
  /** 客户接收销售 ID（必填） */
  receiveStaffId: string;
  staffId: string;
}

/** 销售员状态变更请求（SalesStaffStatusDTO） */
export interface SalesStaffStatusPayload {
  id: string;
  status: SalesStaffStatus;
}

/**
 * 获取销售员分页
 */
export async function getPage(query: SalesStaffQuery) {
  return requestClient.get<SalesStaffPage>('/promotion/sales/staff/page', {
    params: query,
  });
}

/**
 * 根据ID获取销售员
 */
export async function getById(id: string) {
  return requestClient.get<SalesStaffRecord>(`/promotion/sales/staff/${id}`);
}

/**
 * 新增销售员
 */
export async function addObj(data: SalesStaffSavePayload) {
  return requestClient.post<boolean>('/promotion/sales/staff/add', data);
}

/**
 * 编辑销售员
 */
export async function editObj(data: SalesStaffSavePayload) {
  return requestClient.put<boolean>('/promotion/sales/staff/edit', data);
}

/**
 * 销售离职交接（必须指定客户接收人）
 */
export async function resign(data: SalesStaffResignPayload) {
  return requestClient.post<boolean>('/promotion/sales/staff/resign', data);
}

/**
 * 变更销售员状态（0 在职 / 1 离职）
 */
export async function updateStatus(data: SalesStaffStatusPayload) {
  return requestClient.post<boolean>('/promotion/sales/staff/status', data);
}

/**
 * 在职销售下拉选项（keyword 支持姓名/手机号模糊）
 */
export async function getOptions(params?: { keyword?: string }) {
  return requestClient.get<SalesStaffRecord[]>(
    '/promotion/sales/staff/options',
    { params },
  );
}
