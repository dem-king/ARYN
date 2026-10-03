/**
 * 订单列表折叠态的汇总口径。
 *
 * <p>列表页默认不平铺全部商品行，只展示首件商品 + 缩略图堆叠 +
 * 「共 N 件 · M 个分类」汇总；点击展开后才按 {@link ./category-group} 的
 * 分类分组口径渲染完整商品列表（与详情页、导出 Excel 同口径）。
 */
import { groupOrderItems } from './category-group';

/** 折叠态缩略图最多展示的张数，其余以 +N 收口 */
export const MAX_THUMBNAILS = 4;

export interface OrderListSummary {
  /** 折叠态主展示的商品行（首件） */
  firstItem: any;
  /** 首件之后的缩略图（最多 {@link MAX_THUMBNAILS} 张） */
  thumbnails: any[];
  /** 缩略图未覆盖、需以 +N 展示的商品数 */
  hiddenCount: number;
  /** 购买总件数（Σ buyQuantity） */
  totalQuantity: number;
  /** 分类组数（与展开后的分组标题条数量一致） */
  categoryCount: number;
  /** 是否需要折叠/展开开关（仅 1 件商品时直接平铺） */
  collapsible: boolean;
}

/**
 * 汇总一个订单的商品行，供列表页折叠态渲染。
 * 空订单返回 null，调用方按「—」占位。
 */
export function buildOrderSummary(items: any[]): null | OrderListSummary {
  const list = items || [];
  if (list.length === 0) return null;
  const rest = list.slice(1);
  const thumbnails = rest.slice(0, MAX_THUMBNAILS);
  return {
    firstItem: list[0],
    thumbnails,
    hiddenCount: rest.length - thumbnails.length,
    totalQuantity: list.reduce(
      (sum, item) => sum + (Number(item?.buyQuantity) || 0),
      0,
    ),
    categoryCount: groupOrderItems(list).length,
    collapsible: list.length > 1,
  };
}
