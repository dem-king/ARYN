/**
 * 船供活动类型共享口径。
 */

/**
 * 默认锁定券 / 积分叠加的活动类型：新人价(9) / 档期特供(12)。
 * 两者均为限量硬促销，默认关闭叠加防击穿（可按需放开）。
 */
export const STACKABLE_LOCKED_ACTIVITY_TYPES = ['9', '12'] as const;

/** 是否默认锁定叠加（新人价 / 档期特供）。 */
export function isStackableLockedActivityType(type: string): boolean {
  return (STACKABLE_LOCKED_ACTIVITY_TYPES as readonly string[]).includes(type);
}
