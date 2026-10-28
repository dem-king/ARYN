import { requestClient } from '#/api/request';

/**
 * 会员专区自动数据源（装修多 Tab 的 `dataSource.mode='activity'`）。
 *
 * 运营侧统一在「船供活动」管理会员价活动（promotion_activity activityType=10），
 * 装修 JSON 只保存 `mode` 与 `activityId`，专区商品与顺序由活动决定，
 * 活动加减商品后 C 端自动同步、无需重新装修。本文件只服务装修画布预览取数。
 */
export interface AppMemberActivityGoodsVO {
  activityId: string;
  /** 活动名称（专区标题/运营核对用） */
  activityName: string;
  /** 活动结束时间（YYYY-MM-DD HH:mm:ss） */
  endTime: string;
  /** 活动配置商品对应的 SPU ID 列表（按活动 SKU 配置顺序去重保序） */
  spuIds: string[];
}

/**
 * 取会员价活动商品 SPU 列表（管理端画布预览用）。
 *
 * 走 C 端只读接口 `/promotion/app/member/activity-goods`（免登、无需权限）：
 * 画布必须与小程序看到同一份货盘，不能复用管理端活动列表再自行解析 rules。
 * - `activityId` 缺省/空：取当前进行中的会员价活动（跟随活动自动变）；
 * - 指定活动 ID：固定该活动（后端只校验存在 + 会员价类型，不校验时间窗）。
 *
 * 无活动 / 活动无商品 / 商品回填失败时后端返回 `null`，调用方按空货盘处理。
 */
export async function getMemberActivityGoods(activityId?: string) {
  return requestClient.get<AppMemberActivityGoodsVO | null>(
    '/promotion/app/member/activity-goods',
    { params: activityId ? { activityId } : undefined },
  );
}
