/**
 * 靠港时间在首页状态条上的展示格式。
 *
 * 首页只需要回答「什么时候到港」。原先直接拼后端返回的
 * `2026-09-22 00:15:33`，秒级精度对用户只有阅读成本，
 * 因此按与「今天」的距离分档：
 *
 *   今天 00:15 / 明天 00:15 / 09-23 00:15
 *
 * 手工解析 yyyy-MM-dd HH:mm(:ss)，不经过 Date 字符串构造：
 * 小程序端对 `new Date('2026-09-22 00:15:33')` 的解析行为不一致（iOS 返回 Invalid Date）。
 */
const CALL_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/

interface ParsedCallTime {
  day: number
  hour: number
  minute: number
  month: number
  second: number
  time: string
  year: number
}

function parseCallTime(value?: string): ParsedCallTime | null {
  if (!value)
    return null
  const match = value.match(CALL_TIME_PATTERN)
  if (!match)
    return null
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: Number(match[4]),
    minute: Number(match[5]),
    // 秒可缺省（部分接口只给到分钟），缺省按 0
    second: match[6] ? Number(match[6]) : 0,
    time: `${match[4]}:${match[5]}`,
  }
}

/** 组装成本地时间的毫秒时间戳；不用 Date 字符串构造，避开小程序 iOS 解析差异 */
function toTimestamp(parsed: ParsedCallTime): number {
  return new Date(
    parsed.year,
    parsed.month - 1,
    parsed.day,
    parsed.hour,
    parsed.minute,
    parsed.second,
  ).getTime()
}

/** 两个日期相距的自然天数（按本地零点计算，避免跨时区/夏令时误差） */
function diffInDays(target: ParsedCallTime, now: Date): number {
  const targetMidnight = new Date(target.year, target.month - 1, target.day).getTime()
  const nowMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  return Math.round((targetMidnight - nowMidnight) / 86_400_000)
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/**
 * 时间选择器回传的时间戳 → 靠港申报接口所需口径 `yyyy-MM-dd HH:mm:ss`。
 *
 * 与展示用的 `formatCallTime` 分开：申报接口的 `LocalDateTime` 反序列化
 * 只认这一种格式（`DateFormatConfig` 的 `NORM_DATETIME_PATTERN`），
 * 少写一段就会被后端 400 拒掉，因此格式固定在这里并由单测守住。
 *
 * 取值一律走 Date 的本地 getter，不经过字符串解析——后者在小程序 iOS 端
 * 对 `yyyy-MM-dd HH:mm:ss` 会返回 Invalid Date（与 formatCallTime 同一口径）。
 */
export function formatDeclareTime(timestamp: number): string {
  const date = new Date(timestamp)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 解析 `yyyy-MM-dd HH:mm(:ss)` 为毫秒时间戳；无法解析返回 null。
 *
 * 与 formatCallTime 一样手工解析，不经过 `new Date('yyyy-MM-dd HH:mm:ss')`
 * —— 小程序 iOS 端对该格式返回 Invalid Date。供排序、过期判断复用，
 * 避免各页面自己写一份解析后在小程序上静默失效。
 */
export function callTimeTimestamp(value?: string): number | null {
  const parsed = parseCallTime(value)
  if (!parsed)
    return null
  const timestamp = toTimestamp(parsed)
  return Number.isFinite(timestamp) ? timestamp : null
}

/**
 * 距目标时刻的剩余毫秒：无法解析返回 null，已到期返回 <= 0 的数。
 *
 * 供「是否已过期」「是否进入紧急窗口」这类判断使用；展示文案仍走
 * formatExpiryCountdown，两者共用同一份解析，口径不会分叉。
 */
export function remainingMsUntil(value?: string, now: Date = new Date()): number | null {
  const timestamp = callTimeTimestamp(value)
  if (timestamp === null)
    return null
  return timestamp - now.getTime()
}

/**
 * 收集截止倒计时文案：`还剩 3 小时 12 分` / `还剩 8 分`。
 *
 * 共享购物车的有效期是「创建 +24h」，用户在详情页最关心的是「我还有多久」，
 * 而 `2026-09-28 22:23:26` 这种绝对时刻需要自己换算。分档与 formatCallTime
 * 同口径取整：不足 1 分钟说「即将截止」，避免显示成 `还剩 00:00`。
 *
 * 与 formatCallTime 一样手工解析，不经过 `new Date('yyyy-MM-dd HH:mm:ss')`
 * —— 小程序 iOS 端对该格式返回 Invalid Date。已过期或无法解析时返回空串，
 * 由调用方决定展示什么（本页在非收集中状态就不显示倒计时）。
 */
export function formatExpiryCountdown(value?: string, now: Date = new Date()): string {
  const parsed = parseCallTime(value)
  if (!parsed)
    return ''

  const remaining = toTimestamp(parsed) - now.getTime()
  if (!Number.isFinite(remaining) || remaining <= 0)
    return ''

  const totalMinutes = Math.floor(remaining / 60_000)
  if (totalMinutes < 1)
    return '即将截止'
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours <= 0)
    return `还剩 ${minutes} 分`
  if (minutes === 0)
    return `还剩 ${hours} 小时`
  return `还剩 ${hours} 小时 ${minutes} 分`
}

/**
 * 把后端靠港时间格式化为首页可读文案。
 *
 * 无法解析时原样返回，保证界面不出现空白；空值返回空串。
 */
export function formatCallTime(value?: string, now: Date = new Date()): string {
  const parsed = parseCallTime(value)
  if (!parsed)
    return value ?? ''

  const days = diffInDays(parsed, now)
  if (days === 0)
    return `今天 ${parsed.time}`
  if (days === 1)
    return `明天 ${parsed.time}`
  // 跨年时补年份，否则「01-05」无法判断是哪一年
  if (parsed.year !== now.getFullYear()) {
    return `${parsed.year}-${pad(parsed.month)}-${pad(parsed.day)} ${parsed.time}`
  }
  return `${pad(parsed.month)}-${pad(parsed.day)} ${parsed.time}`
}
