import { requestClient } from '#/api/request';

/**
 * 折扣活动管理 API
 * 对应后端：/discount/activity
 */

/** 折扣活动状态：0未开始 1进行中 2已结束 3已暂停 */
export type DiscountActivityStatus = 0 | 1 | 2 | 3;

/** 折扣类型：1打折 2满减 3一口价 */
export type DiscountType = 1 | 2 | 3;

/** 适用范围：1全部商品 2指定商品 */
export type DiscountScope = 1 | 2;

/** 折扣活动分页查询参数 */
export interface DiscountActivityQuery {
  /** 活动名称（模糊匹配） */
  activityName?: string;
  /** 活动状态 */
  status?: number | string;
  /** 当前页码 */
  current?: number;
  /** 每页条数 */
  size?: number;
  /** 排序字段 */
  desc?: string;
}

/** 折扣商品表单 */
export interface DiscountGoodsForm {
  /** 商品 SPU ID */
  spuId: string;
  /** 商品 SKU ID */
  skuId: string;
  /** 商品名称 */
  spuName?: string;
  /** 商品图片地址列表 */
  spuUrls?: string[];
}

/** 折扣活动表单 */
export interface DiscountActivityForm {
  /** 活动 ID（编辑时存在） */
  id?: string;
  /** 活动名称 */
  activityName: string;
  /** 活动描述 */
  description?: string;
  /** 活动时间范围（前端绑定） */
  datatimes?: string[];
  /** 活动开始时间 */
  startTime?: string;
  /** 活动结束时间 */
  endTime?: string;
  /** 折扣类型 */
  discountType: DiscountType;
  /** 折扣值 */
  discountValue: number;
  /** 适用范围 */
  scope: DiscountScope;
  /** 参与折扣的商品列表（scope=2 时使用） */
  goodsList: DiscountGoodsForm[];
}

/** 折扣活动列表记录 */
export interface DiscountActivityRecord {
  /** 活动 ID */
  id: string;
  /** 活动名称 */
  activityName: string;
  /** 活动开始时间 */
  startTime: string;
  /** 活动结束时间 */
  endTime: string;
  /** 折扣类型 */
  discountType: DiscountType;
  /** 折扣值 */
  discountValue: number;
  /** 适用范围 */
  scope: DiscountScope;
  /** 活动状态 */
  status: DiscountActivityStatus;
  /** 创建时间 */
  createTime?: string;
}

/** 分页响应 */
export interface DiscountActivityPageResponse {
  /** 当前页记录 */
  records: DiscountActivityRecord[];
  /** 总数 */
  total: number;
}

/** 分页查询折扣活动 */
export async function getPage(
  query: DiscountActivityQuery,
): Promise<DiscountActivityPageResponse> {
  return requestClient.get('/promotion/discount/activity/page', {
    params: query,
  });
}

/** 根据 ID 获取折扣活动详情（含商品列表） */
export async function getById(id: string): Promise<DiscountActivityForm> {
  return requestClient.get(`/promotion/discount/activity/${id}`);
}

/** 新增折扣活动 */
export async function addObj(
  data: DiscountActivityForm,
): Promise<DiscountActivityForm> {
  return requestClient.post('/promotion/discount/activity', data);
}

/** 修改折扣活动 */
export async function editObj(
  data: DiscountActivityForm,
): Promise<DiscountActivityForm> {
  return requestClient.put('/promotion/discount/activity', data);
}

/** 删除折扣活动 */
export async function delObj(id: string): Promise<void> {
  return requestClient.delete(`/promotion/discount/activity/${id}`);
}

/** 更新折扣活动状态（0未开始 1进行中 2已结束 3已暂停） */
export async function updateStatus(id: string, status: number): Promise<void> {
  return requestClient.put(`/promotion/discount/activity/${id}/status`, {
    status,
  });
}
