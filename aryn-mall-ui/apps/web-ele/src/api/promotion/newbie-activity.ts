import { requestClient } from '#/api/request';

/**
 * 新人专享价（promotion_activity activityType=9）C 端楼层数据。
 *
 * 运营侧统一在「船供活动」管理（/promotion/ship-supply），不再提供独立管理页；
 * 本文件只服务装修画布的新人专享楼层预览。
 */
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
