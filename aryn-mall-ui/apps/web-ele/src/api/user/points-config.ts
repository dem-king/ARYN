import { requestClient } from '#/api/request';

/**
 * 积分全局规则（每租户一行，抵扣风控四参数）。
 *
 * 后端口径：deductMaxRatio 为百分数（20 表示 20%，区间 (0,100]，必填）；
 * validityDays 取值 0-3650（0 表示永不过期）；minUseAmount/maxDeductAmount
 * 单位为元，0 表示不限制。空值提交时统一转 0。
 */
export interface PointsGlobalConfig {
  id?: string;
  /** 积分有效天数，0=永不过期 */
  validityDays?: null | number;
  /** 单笔抵扣上限比例（百分数，20 表示 20%） */
  deductMaxRatio?: null | number;
  /** 最低使用门槛（元），0=无门槛 */
  minUseAmount?: null | number;
  /** 单笔抵扣金额封顶（元），0=不封顶 */
  maxDeductAmount?: null | number;
}

/**
 * 积分排除清单项：命中类目/商品不参与积分抵扣与返积分（黑名单）。
 */
export interface PointsExclude {
  id?: string;
  /** 排除类型：1-类目；2-商品（SPU） */
  targetType: string;
  /** 类目ID / 商品（SPU）ID */
  targetId: string;
  /** 名称快照（展示用，必填便于运营辨认） */
  targetName: string;
}

/**
 * 获取积分配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/pointsconfig/page', { params: query });
}

/**
 * 根据ID获取积分配置
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/pointsconfig/${id}`);
}

/**
 * 添加积分配置
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-user/pointsconfig', data);
}

/**
 * 编辑积分配置
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-user/pointsconfig', data);
}

/**
 * 删除积分配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-user/pointsconfig/${id}`);
}

/**
 * 获取积分全局规则（有效期/抵扣上限/门槛/封顶）
 */
export async function getGlobalConfig() {
  return requestClient.get<PointsGlobalConfig>(
    '/mall-user/pointsconfig/global',
  );
}

/**
 * 保存积分全局规则（每租户一行）
 */
export async function saveGlobalConfig(data: PointsGlobalConfig) {
  return requestClient.put<boolean>('/mall-user/pointsconfig/global', data);
}

/**
 * 获取积分排除清单
 */
export async function listExclude() {
  return requestClient.get<PointsExclude[]>(
    '/mall-user/pointsconfig/exclude/list',
  );
}

/**
 * 新增积分排除规则
 */
export async function addExclude(data: PointsExclude) {
  return requestClient.post<boolean>('/mall-user/pointsconfig/exclude', data);
}

/**
 * 移除积分排除规则
 */
export async function delExclude(id: string) {
  return requestClient.delete<boolean>(`/mall-user/pointsconfig/exclude/${id}`);
}

/**
 * 积分兑换项：积分兑换优惠券（消耗侧「兑券」形态）。
 */
export interface PointsExchange {
  id?: string;
  /** 兑换项标题（如「5 元无门槛券」） */
  title: string;
  /** 图标URL（可选） */
  iconUrl?: null | string;
  /** 所需积分 */
  pointsCost: number;
  /** 优惠券模板ID（coupon_info.id） */
  couponId: string;
  /** 每人限兑次数，0=不限 */
  perUserLimit?: null | number;
  /** 状态：0-启用；1-下架 */
  status?: null | string;
  /** 排序号（小在前） */
  sortOrder?: null | number;
}

/**
 * 获取积分兑换项列表（含下架）
 */
export async function listExchange() {
  return requestClient.get<PointsExchange[]>(
    '/mall-user/pointsconfig/exchange/list',
  );
}

/**
 * 保存积分兑换项（新增/编辑）
 */
export async function saveExchange(data: PointsExchange) {
  return requestClient.put<boolean>('/mall-user/pointsconfig/exchange', data);
}

/**
 * 删除积分兑换项
 */
export async function delExchange(id: string) {
  return requestClient.delete<boolean>(
    `/mall-user/pointsconfig/exchange/${id}`,
  );
}

/**
 * 积分负债日报行（财务递延负债口径：发放确认/消耗结转/过期核销）。
 */
export interface PointsLiabilityDaily {
  statDate: string;
  grantPoints: number;
  refundReturnPoints: number;
  deductPoints: number;
  exchangePoints: number;
  expirePoints: number;
  adminDeductPoints: number;
  /** 新发放折算金额（元，面值口径），null=汇率未配置不折算 */
  grantAmount?: null | number;
  deductAmount?: null | number;
  exchangeAmount?: null | number;
  expireAmount?: null | number;
}

/**
 * 积分负债报表：区间日报 + 全量累计 + 期末负债。
 */
export interface PointsLiabilityReport {
  pointsPerYuan: number;
  days: PointsLiabilityDaily[];
  balancePoints: number;
  balanceAmount?: null | number;
  totalGrantPoints: number;
  totalDeductPoints: number;
  totalExchangePoints: number;
  totalExpirePoints: number;
  totalRefundReturnPoints: number;
  rangeGrantPoints: number;
  rangeConsumePoints: number;
  rangeExpirePoints: number;
}

/**
 * 获取积分负债报表（日期参数 yyyy-MM-dd，缺省不限）
 */
export function getLiabilityReport(params: {
  beginDate?: string;
  endDate?: string;
}) {
  return requestClient.get<PointsLiabilityReport>(
    '/mall-user/pointsconfig/liability-report',
    { params },
  );
}
