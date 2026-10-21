export type IdPickerKind =
  | 'coupon'
  | 'discount-activity'
  | 'goods'
  | 'groupbuy-activity'
  | 'page'
  | 'seckill-session';

export interface IdPickerItem {
  id: string;
  image?: string;
  /** 源数据已删除或已不可查：仍展示可删除行，避免配置无声消失 */
  invalid?: boolean;
  subtitle?: string;
  title: string;
}

/**
 * 手选列表追加：按「已有顺序 + 新选顺序」去重拼接，超出上限截断。
 *
 * 选择器每次打开只回传本次会话勾选项（与既有 goods/setting.vue 的追加语义一致），
 * 重复勾选同一项由这里的去重兜底。
 */
export function mergePickedIds(
  existing: readonly string[],
  picked: readonly string[],
  limit?: number,
): string[] {
  const merged: string[] = [];
  for (const id of [...existing, ...picked]) {
    if (id && !merged.includes(id)) {
      merged.push(id);
    }
  }
  if (typeof limit === 'number' && limit > 0 && merged.length > limit) {
    return merged.slice(0, limit);
  }
  return merged;
}

/** 找出本地还没有展示信息的 ID（需要回源补齐名称/图片） */
export function missingIds(
  ids: readonly string[],
  known: ReadonlySet<string>,
): string[] {
  const missing: string[] = [];
  for (const id of ids) {
    if (id && !known.has(id)) {
      missing.push(id);
    }
  }
  return missing;
}

/** 已失效兜底行：回源查不到的 ID 以可删除行展示，而不是整行消失 */
export function invalidItem(id: string): IdPickerItem {
  return { id, invalid: true, title: `已失效（ID：${id}）` };
}
