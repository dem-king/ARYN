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
const CALL_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/

interface ParsedCallTime {
  day: number
  month: number
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
    time: `${match[4]}:${match[5]}`,
  }
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
