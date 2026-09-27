import type { SectionCondition, SectionConditionRule } from './types'

/** 评估区块显示条件所需的运行时上下文（由调用方从登录态/会员态取值注入） */
export interface ConditionContext {
  isLoggedIn: boolean
  /** 当前登录用户的会员等级 ID，未登录或无等级时为空 */
  memberLevelId?: string
  /** 当前登录用户的用户标签 ID 列表 */
  userTags: string[]
  /** 供测试注入的当前时间；缺省取真实时钟 */
  now?: Date
}

/**
 * 空的 id 集合与解析不了的规则视为「未配置限制」，不拦截任何人。
 *
 * 管理端「添加规则」会先落一条空选项规则（历史上首页曾因
 * `memberLevelIds: []` 被保存发布，导致整页内容对所有人不可见只剩导航条），
 * 因此这里必须 fail-open；仅未知规则类型按不满足处理，防止旧端展示受限内容。
 */
function evaluateRule(rule: SectionConditionRule, context: ConditionContext): boolean {
  switch (rule.type) {
    case 'login':
      return context.isLoggedIn
    case 'guest':
      return !context.isLoggedIn
    case 'memberLevel': {
      if (!rule.memberLevelIds.length)
        return true
      if (!context.isLoggedIn)
        return false
      return !!context.memberLevelId && rule.memberLevelIds.includes(context.memberLevelId)
    }
    case 'userTag': {
      if (!rule.userTagIds.length)
        return true
      if (!context.isLoggedIn)
        return false
      return context.userTags.some(tag => rule.userTagIds.includes(tag))
    }
    case 'timeRange': {
      return isWithinTimeRange(rule.startTime, rule.endTime, context.now ?? new Date())
    }
    default:
      return false
  }
}

/**
 * 评估区块显示条件。字符串简写向后兼容旧数据；
 * 组合条件按 logic 聚合，rules 为空视为未配置直接放行。
 */
export function evaluateCondition(condition: SectionCondition, context: ConditionContext): boolean {
  if (typeof condition === 'string') {
    if (condition === 'login')
      return context.isLoggedIn
    if (condition === 'guest')
      return !context.isLoggedIn
    return true // 'always'
  }
  if (condition && typeof condition === 'object' && condition.rules) {
    if (!condition.rules.length)
      return true
    const results = condition.rules.map(rule => evaluateRule(rule, context))
    if (condition.logic === 'or')
      return results.some(Boolean)
    return results.every(Boolean)
  }
  return true
}

/** HH:mm 格式判断时刻是否在区间内，支持跨天（如 22:00~06:00）；格式异常时不阻断展示 */
function isWithinTimeRange(startTime: string, endTime: string, now: Date): boolean {
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const startMatch = /^(\d{2}):(\d{2})$/.exec(startTime)
  const endMatch = /^(\d{2}):(\d{2})$/.exec(endTime)
  if (!startMatch || !endMatch)
    return true
  const startMinutes = Number(startMatch[1]) * 60 + Number(startMatch[2])
  const endMinutes = Number(endMatch[1]) * 60 + Number(endMatch[2])
  if (startMinutes <= endMinutes)
    return nowMinutes >= startMinutes && nowMinutes <= endMinutes
  return nowMinutes >= startMinutes || nowMinutes <= endMinutes
}
