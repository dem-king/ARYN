/**
 * 后端 LocalDateTime 统一下发 `yyyy-MM-dd HH:mm:ss`（DateFormatConfig 的
 * NORM_DATETIME_PATTERN）。小程序 iOS 端对 `new Date('2026-09-22 09:43:40')`
 * / `Date.parse` 会得到 Invalid Date / NaN（V8 与部分安卓内核正常），因此
 * 这类时间串一律先经这里手工解析成本地时间的毫秒时间戳，再交给 Date。
 *
 * 与 vessel-call-time.ts 的 callTimeTimestamp 同一口径，此处额外放宽：
 * 时分秒可整体缺省（按本地零点算），供装修倒计时等目标时刻使用。
 */
const LOCAL_DATETIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?/

export function parseLocalDateTimeTimestamp(value?: string): number | null {
  const match = value?.match(LOCAL_DATETIME_PATTERN)
  if (!match)
    return null
  const timestamp = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    match[4] ? Number(match[4]) : 0,
    match[5] ? Number(match[5]) : 0,
    match[6] ? Number(match[6]) : 0,
  ).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

/**
 * 目标时刻解析：Date 实例直取时间戳，字符串走 parseLocalDateTimeTimestamp；
 * 解析不了返回 null，由调用方按「无效/已结束」处理，避免 NaN 渲染进页面。
 */
export function resolveTargetTimestamp(target: string | Date): number | null {
  if (target instanceof Date)
    return target.getTime()
  return parseLocalDateTimeTimestamp(target)
}
