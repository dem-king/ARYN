/**
 * 类目角标（纯函数，便于单测）。
 *
 * 分类页左栏在二级类目名前置一个小方块（参考图：「荐」= 时令推荐、「热」= 热卖）。
 * 后端只存语义枚举 `goods_category.badge_type`，样式在这里内置 —— 运营在管理后台
 * 只能选「推荐 / 热卖 / 无」，同一语义在全租户下配色一致，不会因为各自传图而漂移。
 *
 * 与 [[category-icon]] 的分工：那个管「类目没有配图时用什么兜底」，
 * 这个管「类目有没有角标、角标长什么样」，互不影响。
 */

/** 角标语义 → 文案与配色。key 与后端 badge_type 取值一一对应 */
export const CATEGORY_BADGES = {
  /** 推荐（参考图「荐」，绿色） */
  1: { text: '荐', bgColor: '#00B578', color: '#FFFFFF' },
  /** 热卖（参考图「热」，红色） */
  2: { text: '热', bgColor: '#FF4D2D', color: '#FFFFFF' },
} as const

export interface CategoryBadge {
  /** 角标文案（单字） */
  text: string
  /** 角标底色 */
  bgColor: string
  /** 角标文字色 */
  color: string
}

/**
 * 按字符串键查找的视图。
 *
 * 后端 JSON 里 badge_type 可能是字符串 '1' 或数字 1，统一转成字符串再查；
 * 直接对 `CATEGORY_BADGES` 做 `as keyof typeof` 断言会被 TS 判为不安全的收窄。
 */
const BADGE_LOOKUP: Record<string, CategoryBadge> = CATEGORY_BADGES

/**
 * 解析类目角标；无角标时返回 null。
 *
 * 返回 null 而不是「返回一个空角标」：调用方用 `v-if` 控制渲染，
 * 无角标时**不产生任何节点**，避免左栏多出一层空盒子改变对齐。
 *
 * 容忍后端字段缺失与脏值（`undefined` / `'0'` / 未知值一律视为无角标），
 * 不在前端猜语义 —— 与 C 端「不造假数据」的一贯口径一致。
 */
export function resolveCategoryBadge(
  badgeType: string | number | null | undefined,
): CategoryBadge | null {
  if (badgeType === null || badgeType === undefined || badgeType === '')
    return null

  // 后端可能返回字符串 '1' 或数字 1（JSON 序列化差异），统一按字符串取
  return BADGE_LOOKUP[String(badgeType)] ?? null
}
