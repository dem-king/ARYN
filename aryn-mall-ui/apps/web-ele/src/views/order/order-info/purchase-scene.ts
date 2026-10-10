/**
 * 购买场景展示口径。
 *
 * <p>场景是订单固有属性：船供采购必走公司港口/船舶内部配送，但内部配送也可能是个人的到船订单，
 * 所以只有 purchase_scene='2' 才是船供采购，其余（含历史版本未落场景的空值）一律为个人购买。
 *
 * <p>管理端列表页、订单详情页与后端导出 Excel（{@code OrderCategoryExportExcel}）共用本口径，
 * 三处不得各自判断，否则同一张订单会出现「个人购买」与「—」两种显示。
 */

/** 船供采购 */
export const PURCHASE_SCENE_SHIP_SUPPLY = '2';

/** 海员个人购买（历史数据场景为空时同样按此展示） */
export const PURCHASE_SCENE_PERSONAL = '1';

/**
 * 购买场景文案：非船供采购一律展示个人购买。
 */
export function purchaseSceneText(scene?: string): string {
  return scene === PURCHASE_SCENE_SHIP_SUPPLY ? '船供采购' : '个人购买';
}
