/**
 * 购买场景展示口径。
 *
 * <p>场景是订单固有属性：船供采购（2）必走公司港口/船舶内部配送；企业采购（3）是
 * 岸上客户经共享购物车合并采购、走公司配送（岸上）的订单；其余（含历史版本未落
 * 场景的空值）一律为个人购买。
 *
 * <p>管理端列表页、订单详情页与后端导出 Excel（{@code OrderCategoryExportExcel}）共用本口径，
 * 三处不得各自判断，否则同一张订单会出现「个人购买」与「—」两种显示。
 */

/** 船供采购 */
export const PURCHASE_SCENE_SHIP_SUPPLY = '2';

/** 海员个人购买（历史数据场景为空时同样按此展示） */
export const PURCHASE_SCENE_PERSONAL = '1';

/** 企业采购（岸上共享购物车合并采购） */
export const PURCHASE_SCENE_ENTERPRISE = '3';

/**
 * 购买场景文案：空值与未知值按个人购买展示（历史口径不变）。
 */
export function purchaseSceneText(scene?: string): string {
  if (scene === PURCHASE_SCENE_SHIP_SUPPLY) return '船供采购';
  if (scene === PURCHASE_SCENE_ENTERPRISE) return '企业采购';
  return '个人购买';
}
