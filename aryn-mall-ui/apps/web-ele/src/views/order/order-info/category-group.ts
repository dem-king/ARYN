/**
 * 订单商品按分类分组的共享口径。
 *
 * <p>管理端三个展示位共用同一套分组规则：
 * 订单详情页（分类列纵向合并）、订单列表页（内嵌商品分组标题条）、
 * 导出 Excel（{@code OrderCategoryExportExcel}，后端同口径实现）：
 *
 * <ul>
 *   <li>组内保持商品行的原始顺序（共享采购按人拆行的归属语义不被打乱）；</li>
 *   <li>组间按分类名中文拼音序排序；</li>
 *   <li>分类名为空（商品被删或未归类）归入「未分类」并固定排最后。</li>
 * </ul>
 */

/** 无分类商品的归组名 */
export const UNCATEGORIZED = '未分类';

export interface OrderCategoryGroup {
  /** 分组键：分类名或「未分类」 */
  key: string;
  isUncategorized: boolean;
  items: any[];
}

/** 详情页分组行：携带分类列纵向合并所需的组信息 */
export interface GroupedOrderItem {
  categoryKey: string;
  categoryLabel: string;
  isUncategorized: boolean;
  /** 是否组首行（组首行 rowspan=组大小，其余行 0） */
  isFirstOfGroup: boolean;
  groupSize: number;
  item: any;
}

function groupItems(items: any[]): OrderCategoryGroup[] {
  const groups = new Map<string, { isUncategorized: boolean; items: any[] }>();
  for (const item of items || []) {
    const key = item.categoryName || UNCATEGORIZED;
    const group = groups.get(key) || {
      isUncategorized: key === UNCATEGORIZED,
      items: [],
    };
    group.items.push(item);
    groups.set(key, group);
  }
  return [...groups.entries()]
    .sort((a, b) => {
      if (a[0] === UNCATEGORIZED) return 1;
      if (b[0] === UNCATEGORIZED) return -1;
      return a[0].localeCompare(b[0], 'zh-Hans-CN');
    })
    .map(([key, group]) => ({
      key,
      isUncategorized: group.isUncategorized,
      items: group.items,
    }));
}

/**
 * 列表页内嵌商品行分组：每组渲染一条分类标题 + 组内商品行。
 */
export function groupOrderItems(items: any[]): OrderCategoryGroup[] {
  return groupItems(items);
}

/**
 * 详情页表格展示行：按分类分组重排并标注组首行/组大小，
 * 供 ElTable 的 span-method 合并「分类」列单元格。
 */
export function buildGroupedDisplayItems(items: any[]): GroupedOrderItem[] {
  const result: GroupedOrderItem[] = [];
  for (const group of groupItems(items)) {
    group.items.forEach((item, index) => {
      result.push({
        categoryKey: group.key,
        categoryLabel: group.key,
        isUncategorized: group.isUncategorized,
        isFirstOfGroup: index === 0,
        groupSize: group.items.length,
        item,
      });
    });
  }
  return result;
}
