import { requestClient } from '#/api/request';

/**
 * 新人专享价活动（promotion_activity activityType=9）。
 *
 * 复用船供营销活动的统一活动接口（/promotion/ship-activity），
 * 本文件只固定类型/范围等语义差异，避免页面层散落魔法值。
 */
export interface NewbieSkuPrice {
  /** SKU ID */
  skuId: string;
  /** 新人专享价 */
  price: number | string;
  /** SPU ID（rules 里一并留存，供编辑回填反查商品名/图） */
  spuId?: string;
  /** 前端展示用：商品名 */
  spuName?: string;
  /** 前端展示用：图片 */
  spuUrls?: string[];
  /** 前端展示用：划线原价（商品当前售价） */
  originalPrice?: number | string;
}

/** rules JSON 结构（与后端 ActivityPublishGovernanceService.validateNewbiePrice 对应） */
export interface NewbieRules {
  registerWithinDays?: number;
  limitPerUser: number;
  skus: NewbieSkuPrice[];
}

export interface NewbieActivity {
  activityName: string;
  /** 固定 '9' */
  activityType: string;
  endTime: string;
  id?: string;
  priority?: number;
  purchaseScene?: string;
  rules: string;
  /** 固定 '2'（指定商品） */
  scopeType: string;
  scopeValue: string;
  startTime: string;
  status?: string;
}

/** 活动类型与范围固定值：表单不暴露，由页面组装时写入 */
export const NEWBIE_ACTIVITY_TYPE = '9';
export const NEWBIE_SCOPE_TYPE = '2';

export async function getNewbiePage(query: any) {
  return requestClient.get('/promotion/ship-activity/page', {
    params: { ...query, activityType: NEWBIE_ACTIVITY_TYPE },
  });
}

export async function getNewbieDetail(id: string) {
  return requestClient.get(`/promotion/ship-activity/${id}`);
}

export async function addNewbieActivity(data: Partial<NewbieActivity>) {
  return requestClient.post('/promotion/ship-activity', data);
}

export async function updateNewbieActivity(
  id: string,
  data: Partial<NewbieActivity>,
) {
  return requestClient.put(`/promotion/ship-activity/${id}`, data);
}

export async function getNewbieConflicts(id: string) {
  return requestClient.get(`/promotion/ship-activity/${id}/conflicts`);
}

export async function publishNewbieActivity(id: string, force = false) {
  return requestClient.post(`/promotion/ship-activity/${id}/publish`, null, {
    params: { force },
  });
}

export async function pauseNewbieActivity(id: string) {
  return requestClient.post(`/promotion/ship-activity/${id}/pause`);
}

export async function deleteNewbieActivity(id: string) {
  return requestClient.delete(`/promotion/ship-activity/${id}`);
}

/** C端新人专享楼层 VO（管理端预览用，与小程序同接口） */
export interface AppNewbieFloorVO {
  activityId: string;
  activityName: string;
  endTime: string;
  goodsList: Array<{
    goodsImage: string;
    goodsName: string;
    newbiePrice: number;
    originalPrice: number;
    skuId: string;
    spuId: string;
  }>;
  limitPerUser: number;
  registerWithinDays: null | number;
}

/**
 * 取当前进行中的新人专享楼层（管理端预览用）。
 *
 * 走 C 端只读接口 `/promotion/app/newbie/current`（免登，无需权限），
 * 装修画布需要与小程序看到同一份数据，不能复用管理端活动列表。
 */
export async function getCurrentNewbieFloor() {
  return requestClient.get<AppNewbieFloorVO | null>(
    '/promotion/app/newbie/current',
  );
}
