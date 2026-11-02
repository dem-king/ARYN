/**
 * 「我的评价」操作可见性判定（纯函数）。
 *
 * 后端对追评/修改/删除有强校验（30 天窗口、追评与修改各仅一次、系统默认评价
 * 不可改删），前端这里只决定按钮是否展示：口径与后端保持一致，避免出现
 * 「按钮能点但接口必报错」的体验。时间参数以服务端 `createTime` 为准。
 */

/** 评价操作窗口：主评后 30 天内可追评/修改/删除（与后端契约一致） */
export const APPRAISE_ACTION_WINDOW_DAYS = 30
/** 平均单日毫秒数（窗口判定按天粒度，不做时区换算，与后端 LocalDateTime 口径一致） */
export const APPRAISE_ACTION_WINDOW_MS = APPRAISE_ACTION_WINDOW_DAYS * 24 * 60 * 60 * 1000
/** 客户端与服务端时钟允许的偏差：服务端时间略快时不至于把用户挡在窗口外 */
export const APPRAISE_CLOCK_SKEW_MS = 24 * 60 * 60 * 1000

export interface AppraiseActionRecord {
  createTime?: string | null
  advanceTime?: string | null
  advanceContent?: string | null
  modifyTime?: string | null
  appraiseType?: number | string | null
  isAnonymous?: number | string | null
}

/**
 * 解析后端时间字符串为时间戳；无法解析返回 null。
 *
 * 小程序 iOS 端 `new Date('yyyy-MM-dd HH:mm:ss')` 会得到 Invalid Date，
 * 这里手工把空格换成 `T` 后再解析；带时区的 ISO 字符串原样解析。
 */
export function parseAppraiseTime(value?: string | null): number | null {
  if (!value)
    return null
  const text = String(value).trim()
  if (!text)
    return null
  const normalized = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(text)
    ? text.replace(' ', 'T')
    : text
  const timestamp = Date.parse(normalized)
  return Number.isFinite(timestamp) ? timestamp : null
}

/** 系统默认评价（appraiseType=1）：只允许追评，不可修改/删除 */
export function isSystemDefaultAppraise(record?: AppraiseActionRecord | null): boolean {
  return Number(record?.appraiseType) === 1
}

export function isAnonymousAppraise(record?: AppraiseActionRecord | null): boolean {
  return Number(record?.isAnonymous) === 1
}

/** 是否已追评（追评仅一次，已追评不再展示入口） */
export function hasAdvancedAppraise(record?: AppraiseActionRecord | null): boolean {
  return Boolean(record?.advanceTime)
}

/** 是否已修改过（修改仅一次，已修改不再展示入口） */
export function hasModifiedAppraise(record?: AppraiseActionRecord | null): boolean {
  return Boolean(record?.modifyTime)
}

/**
 * 是否仍在评价操作窗口内：`0 <= now - createTime <= 30 天`。
 * 时钟偏差容错：服务端时间比客户端快不超过 1 天时仍按窗口内处理。
 */
export function isWithinAppraiseWindow(
  createTime?: string | null,
  now: number = Date.now(),
): boolean {
  const timestamp = parseAppraiseTime(createTime)
  if (timestamp === null)
    return false
  const diff = now - timestamp
  return diff <= APPRAISE_ACTION_WINDOW_MS && diff >= -APPRAISE_CLOCK_SKEW_MS
}

/** 追评按钮：未追评 + 30 天窗口内（系统默认评价同样可追评） */
export function canAdvanceAppraise(
  record?: AppraiseActionRecord | null,
  now: number = Date.now(),
): boolean {
  if (!record)
    return false
  return !hasAdvancedAppraise(record) && isWithinAppraiseWindow(record.createTime, now)
}

/** 修改按钮：非系统默认 + 未修改过 + 30 天窗口内 */
export function canModifyAppraise(
  record?: AppraiseActionRecord | null,
  now: number = Date.now(),
): boolean {
  if (!record || isSystemDefaultAppraise(record))
    return false
  return !hasModifiedAppraise(record) && isWithinAppraiseWindow(record.createTime, now)
}

/** 删除按钮：非系统默认 + 30 天窗口内 */
export function canDeleteAppraise(
  record?: AppraiseActionRecord | null,
  now: number = Date.now(),
): boolean {
  if (!record || isSystemDefaultAppraise(record))
    return false
  return isWithinAppraiseWindow(record.createTime, now)
}

/** 是否提供任意一个操作按钮（用于「无操作」占位展示） */
export function hasAnyAppraiseAction(
  record?: AppraiseActionRecord | null,
  now: number = Date.now(),
): boolean {
  return canAdvanceAppraise(record, now)
    || canModifyAppraise(record, now)
    || canDeleteAppraise(record, now)
}
