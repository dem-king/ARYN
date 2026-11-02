/**
 * 会话失效（被顶下线 / 登录过期 / 被踢下线）的**纯逻辑层**：契约解析、设备行/时间格式化、文案组装。
 *
 * 为什么单独抽出来：响应处理器（handlers）、租户 preflight、session store、弹窗组件
 * 都要用同一份「wire 值 → 用户可见文案」映射，任何一处自己拼文案都会漂移。
 * 本模块不 import 请求层/store，避免 handlers ↔ instance ↔ store 的循环依赖。
 *
 * 契约来源：
 *  - docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md（reason/响应形态/文案）
 *  - 同目录《二期实现契约.md》（subReason 细分 + 单按钮规则）
 */

import { translate } from '@/locales'

/** 后端契约冻结的 reason wire 值（大小写敏感，不得增删改）。 */
export const SESSION_INVALID_REASONS = [
  'NOT_TOKEN',
  'TOKEN_INVALID',
  'TOKEN_EXPIRED',
  'BE_REPLACED',
  'KICK_OUT',
  'TOKEN_FREEZE',
  'NO_PREFIX',
] as const

export type SessionInvalidReason = (typeof SESSION_INVALID_REASONS)[number]

/**
 * 后端契约冻结的 subReason wire 值（二期；大小写敏感，不得增删改）。
 *
 * 只在 `reason=KICK_OUT` 时出现；未知值必须按「无 subReason」降级（不得报错/空白）。
 */
export const SESSION_INVALID_SUB_REASONS = [
  'ADMIN_KICK',
  'PASSWORD_CHANGED',
  'ACCOUNT_DISABLED',
  'ACCOUNT_DELETED',
  'TENANT_DISABLED',
  'MEMBER_DISABLED',
  'DELIVERY_REVOKED',
] as const

export type SessionInvalidSubReason = (typeof SESSION_INVALID_SUB_REASONS)[number]

/** 失效归属：商城（TOC）还是配送端（TOB）。两端 token 完全隔离。 */
export type SessionInvalidTarget = 'mall' | 'delivery'

/** i18n 的 t 函数最小签名，便于测试注入固定语言或替身。 */
export type TranslateFn = (key: string, named?: Record<string, unknown>) => string

/** 新设备信息（全部可空：旧后端/后端拿不到设备信息时整行隐藏）。 */
export interface SessionInvalidDevice {
  os?: string
  browser?: string
  location?: string
  loginTime?: string
}

/** 401 响应 `data` 里前端关心的全部字段。 */
export interface SessionInvalidPayload {
  reason: SessionInvalidReason | ''
  /** 二期：仅 KICK_OUT 携带；未知/非 KICK_OUT 一律置空 */
  subReason: SessionInvalidSubReason | ''
  invalidAt: string
  device: SessionInvalidDevice | null
}

/** 弹窗渲染所需的最终文案（已按 reason/subReason/target/语言解析）。 */
export interface SessionInvalidCopy {
  title: string
  /** 主句：BE_REPLACED 是「当前账号于 {time} 在另一台设备登录…」，其余 reason 是完整正文 */
  lead: string
  /** 设备行（含「设备：」前缀）；无设备信息时为 ''，弹窗整行隐藏 */
  deviceLine: string
  /** BE_REPLACED 的尾句；其余 reason 为 '' */
  tail: string
  relogin: string
  exit: string
  /**
   * 是否允许「重新登录」（二期按钮规则）：
   * false 时（账号停用/删除、租户停用、会员禁用）重登必然失败，弹窗只渲染一个按钮。
   */
  canRelogin: boolean
  /** canRelogin=false 时唯一按钮的文案（「返回登录」或「退出」）；true 时不使用 */
  singleActionText: string
}

/** 弹窗按钮的纯逻辑描述：渲染与点击分派都由它驱动，便于单测断言「单按钮=回登录页」。 */
export interface SessionInvalidAction {
  key: 'relogin' | 'exit' | 'single'
  text: string
  /**
   * 点击后执行的动作：
   *  - relogin：一期两按钮的「重新登录」（写 redirectPath → 登录页）
   *  - exit：一期两按钮的「退出」（清态→首页/配送登录页）
   *  - exitToLogin：二期 canRelogin=false 的单按钮（清态→登录页，不写 redirectPath）
   */
  action: 'relogin' | 'exit' | 'exitToLogin'
  /** 主按钮（样式 primary） */
  primary: boolean
}

export interface SessionInvalidCopyInput {
  reason?: SessionInvalidReason | ''
  subReason?: SessionInvalidSubReason | ''
  invalidAt?: string
  device?: SessionInvalidDevice | null
  target?: SessionInvalidTarget
  /** 便于测试固定「今天」，跨天判断不依赖真实时钟 */
  now?: number
}

const REASON_SET = new Set<string>(SESSION_INVALID_REASONS)
const SUB_REASON_SET = new Set<string>(SESSION_INVALID_SUB_REASONS)
const MAX_FIELD_LENGTH = 64

const EMPTY_PAYLOAD: SessionInvalidPayload = {
  reason: '',
  subReason: '',
  invalidAt: '',
  device: null,
}

function cleanString(value: unknown, maxLength = MAX_FIELD_LENGTH): string {
  if (typeof value !== 'string')
    return ''
  const trimmed = value.trim()
  return trimmed ? trimmed.slice(0, maxLength) : ''
}

/** 设备字段专用清洗：额外把 UA 解析兜底值 Unknown 按缺失处理。 */
function cleanDeviceText(value: unknown): string {
  const cleaned = cleanString(value)
  return cleaned.toLowerCase() === 'unknown' ? '' : cleaned
}

/** 把「对象或 JSON 字符串」统一成普通对象；数组/标量/坏 JSON 一律返回 null。 */
function asRecord(value: unknown): Record<string, unknown> | null {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed.startsWith('{'))
      return null
    try {
      const parsed: unknown = JSON.parse(trimmed)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed as Record<string, unknown>
        : null
    }
    catch {
      return null
    }
  }
  if (value && typeof value === 'object' && !Array.isArray(value))
    return value as Record<string, unknown>
  return null
}

/** 未知/缺失 reason 统一降级为空串（弹窗按 TOKEN_INVALID 通用文案渲染）。 */
export function normalizeSessionInvalidReason(value: unknown): SessionInvalidReason | '' {
  const reason = cleanString(value)
  return REASON_SET.has(reason) ? reason as SessionInvalidReason : ''
}

/**
 * 未知/缺失 subReason 统一丢弃（二期契约 §1：未知值按「无 subReason」降级，不得报错/空白）。
 * 不复用 reason 的降级文案 —— KICK_OUT 无 subReason 的文案与「未知 subReason」相同，
 * 都是「登录状态已失效，请重新登录。」。
 */
export function normalizeSessionInvalidSubReason(value: unknown): SessionInvalidSubReason | '' {
  const subReason = cleanString(value)
  return SUB_REASON_SET.has(subReason) ? subReason as SessionInvalidSubReason : ''
}

/** invalidAt 只接受字符串；非法值置空，由文案层降级。 */
export function normalizeSessionInvalidTimestamp(value: unknown): string {
  return cleanString(value, 40)
}

export function normalizeSessionInvalidDevice(value: unknown): SessionInvalidDevice | null {
  const record = asRecord(value)
  if (!record)
    return null
  const device: SessionInvalidDevice = {}
  // UA 解析不出时后端在线记录里是 hutool 兜底值 Unknown，对用户无信息量，按缺失处理
  const os = cleanDeviceText(record.os)
  const browser = cleanDeviceText(record.browser)
  const location = cleanDeviceText(record.location)
  const loginTime = cleanString(record.loginTime, 40)
  if (os)
    device.os = os
  if (browser)
    device.browser = browser
  if (location)
    device.location = location
  if (loginTime)
    device.loginTime = loginTime
  return Object.keys(device).length > 0 ? device : null
}

/**
 * 从 401 响应体里提取 `{reason, subReason, invalidAt, device}`。
 *
 * 兼容三层真实形态（全部遇到过）：
 *  1. uni 适配器已把 JSON 解析成对象 → 直接拿到 payload；
 *  2. 整个业务信封是 JSON 字符串（网关 text/plain）→ parseApiResponse 已解析一层；
 *  3. 调用方传进来的是完整信封 `{code,msg,data}`（如 handleAlovaError 的 error.data），
 *     payload 还在 data 里，需要再拆一层。
 * 解析不到任何字段时返回空 payload（调用方按「无 reason」降级）。
 *
 * subReason 只在 reason=KICK_OUT 时有意义：即使后端错误地下发了其它组合，也丢弃，
 * 避免文案映射出现契约外的分支。
 */
export function extractSessionInvalidPayload(value: unknown, depth = 0): SessionInvalidPayload {
  if (depth > 3)
    return { ...EMPTY_PAYLOAD }
  const record = asRecord(value)
  if (!record)
    return { ...EMPTY_PAYLOAD }
  const reason = normalizeSessionInvalidReason(record.reason)
  const subReason = reason === 'KICK_OUT' ? normalizeSessionInvalidSubReason(record.subReason) : ''
  const invalidAt = normalizeSessionInvalidTimestamp(record.invalidAt)
  const device = normalizeSessionInvalidDevice(record.device)
  if (reason || subReason || invalidAt || device)
    return { reason, subReason, invalidAt, device }
  // 本层没有契约字段：可能是信封（{code,msg,data}），继续拆 data；
  // 不是契约 payload 的其它对象最终落到空 payload。
  return extractSessionInvalidPayload(record.data, depth + 1)
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

/**
 * 时间展示：当天 `HH:mm`，跨天 `MM-dd HH:mm`（契约 §4）。
 * 无法解析返回 ''，调用方据此降级文案（不显示「未知时间」）。
 */
export function formatSessionInvalidTime(value: unknown, now: number = Date.now()): string {
  if (typeof value !== 'string' || !value.trim())
    return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime()))
    return ''
  const reference = new Date(now)
  const clock = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
  const sameDay = date.getFullYear() === reference.getFullYear()
    && date.getMonth() === reference.getMonth()
    && date.getDate() === reference.getDate()
  return sameDay ? clock : `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${clock}`
}

/**
 * 移动端设备片段：`{os} · {browser}｜{location}｜{HH:mm}`。
 *
 * 契约 §4（2026-11-01 增量）：新增地点段（不展示 IP，IP 仅管理端渲染）；
 * `browser`/`os` 任一为空则省略该片段；所有片段都为空时整行隐藏（不显示「未知设备」）。
 */
export function buildSessionInvalidDeviceLine(
  device: SessionInvalidDevice | null | undefined,
  now: number = Date.now(),
): string {
  if (!device)
    return ''
  const identity = [device.os, device.browser].filter(Boolean).join(' · ')
  const time = formatSessionInvalidTime(device.loginTime, now)
  const parts = [identity, device.location?.trim() || '', time].filter(Boolean)
  return parts.join('｜')
}

/**
 * KICK_OUT + subReason 的冻结文案与按钮规则（二期契约 §2，移动端正文逐字）。
 *
 * `singleAction`：canRelogin=false 时唯一按钮的文案取「返回登录」还是「退出」；
 * 两者底层动作相同（清凭证 + 回登录页），仅文案不同。
 */
const KICK_OUT_SUB_REASON_COPY: Record<SessionInvalidSubReason, {
  titleKey: string
  contentKey: string
  canRelogin: boolean
  singleAction?: 'backToLogin' | 'exit'
}> = {
  ADMIN_KICK: {
    titleKey: 'sessionInvalid.title.adminKick',
    contentKey: 'sessionInvalid.content.adminKick',
    canRelogin: true,
  },
  PASSWORD_CHANGED: {
    titleKey: 'sessionInvalid.title.passwordChanged',
    contentKey: 'sessionInvalid.content.passwordChanged',
    canRelogin: true,
  },
  ACCOUNT_DISABLED: {
    titleKey: 'sessionInvalid.title.accountDisabled',
    contentKey: 'sessionInvalid.content.accountDisabled',
    canRelogin: false,
    singleAction: 'backToLogin',
  },
  ACCOUNT_DELETED: {
    titleKey: 'sessionInvalid.title.accountDeleted',
    contentKey: 'sessionInvalid.content.accountDeleted',
    canRelogin: false,
    singleAction: 'backToLogin',
  },
  TENANT_DISABLED: {
    titleKey: 'sessionInvalid.title.tenantDisabled',
    contentKey: 'sessionInvalid.content.tenantDisabled',
    canRelogin: false,
    singleAction: 'exit',
  },
  MEMBER_DISABLED: {
    titleKey: 'sessionInvalid.title.memberDisabled',
    contentKey: 'sessionInvalid.content.memberDisabled',
    canRelogin: false,
    singleAction: 'backToLogin',
  },
  DELIVERY_REVOKED: {
    titleKey: 'sessionInvalid.title.deliveryRevoked',
    contentKey: 'sessionInvalid.content.deliveryRevoked',
    canRelogin: true,
  },
}

/**
 * 组装弹窗文案（一期契约 §4 + 二期契约 §2 逐字）。
 *
 * 优先级：KICK_OUT+subReason（二期细分）> BE_REPLACED（含配送端标题）> TOKEN_EXPIRED > 通用。
 * - BE_REPLACED：正文 = 主句（含 {time}）+ 设备行 + 尾句；
 * - invalidAt 缺失时用 device.loginTime 兜底；两者都没有时降级为无时间版主句
 *   （避免出现「当前账号于  在另一台设备登录」的空洞文案）；
 * - KICK_OUT 无 subReason / 未知 subReason：通用文案，可重新登录。
 */
export function resolveSessionInvalidCopy(
  input: SessionInvalidCopyInput = {},
  t: TranslateFn = translate,
): SessionInvalidCopy {
  const reason = input.reason ?? ''
  const target: SessionInvalidTarget = input.target === 'delivery' ? 'delivery' : 'mall'
  const now = input.now ?? Date.now()
  const relogin = t('sessionInvalid.relogin')
  const exit = t('sessionInvalid.exit')
  const backToLogin = t('sessionInvalid.backToLogin')

  // 二期：KICK_OUT + 已知 subReason 走细分文案/按钮规则
  if (reason === 'KICK_OUT' && input.subReason) {
    const definition = KICK_OUT_SUB_REASON_COPY[input.subReason]
    if (definition) {
      return {
        title: t(definition.titleKey),
        lead: t(definition.contentKey),
        deviceLine: '',
        tail: '',
        relogin,
        exit,
        canRelogin: definition.canRelogin,
        singleActionText: definition.singleAction === 'exit' ? exit : backToLogin,
      }
    }
  }

  let titleKey = 'sessionInvalid.title.invalid'
  if (reason === 'BE_REPLACED')
    titleKey = target === 'delivery' ? 'sessionInvalid.title.beReplacedDelivery' : 'sessionInvalid.title.beReplaced'
  else if (reason === 'TOKEN_EXPIRED')
    titleKey = 'sessionInvalid.title.tokenExpired'

  const title = t(titleKey)

  if (reason === 'BE_REPLACED') {
    const time = formatSessionInvalidTime(input.invalidAt, now)
      || formatSessionInvalidTime(input.device?.loginTime, now)
    const lead = time
      ? t('sessionInvalid.content.beReplaced', { time })
      : t('sessionInvalid.content.beReplacedNoTime')
    const deviceText = buildSessionInvalidDeviceLine(input.device, now)
    return {
      title,
      lead,
      deviceLine: deviceText ? t('sessionInvalid.deviceLine', { device: deviceText }) : '',
      tail: t('sessionInvalid.content.beReplacedTail'),
      relogin,
      exit,
      canRelogin: true,
      singleActionText: backToLogin,
    }
  }

  return {
    title,
    lead: reason === 'TOKEN_EXPIRED'
      ? t('sessionInvalid.content.tokenExpired')
      : t('sessionInvalid.content.invalid'),
    deviceLine: '',
    tail: '',
    relogin,
    exit,
    canRelogin: true,
    singleActionText: backToLogin,
  }
}

/**
 * 弹窗按钮计划（纯函数）：渲染与点击分派唯一来源。
 *
 * - canRelogin=true：[主「重新登录」(action=relogin), 次「退出」(action=exit)]
 * - canRelogin=false：单主按钮 action=exitToLogin（不渲染重新登录，避免必然失败的白试；
 *   落点是登录页而不是首页——这些账号重登必然失败，首页属「登录后内容」语义不符）
 */
export function resolveSessionInvalidActions(copy: SessionInvalidCopy): SessionInvalidAction[] {
  if (!copy.canRelogin) {
    return [{ key: 'single', text: copy.singleActionText, action: 'exitToLogin', primary: true }]
  }
  return [
    { key: 'relogin', text: copy.relogin, action: 'relogin', primary: true },
    { key: 'exit', text: copy.exit, action: 'exit', primary: false },
  ]
}

/**
 * WS 主动推送事件类型（三期契约 §1.1）：服务端 → 客户端的会话失效事件。
 *
 * 复用现有消息通道；客户端对未知 eventType 必须忽略（继续按原逻辑处理普通消息）。
 */
export const SESSION_INVALID_PUSH_EVENT = 'SESSION_INVALID'

/**
 * 解析 WS 推送消息：命中 `SESSION_INVALID` 时返回契约 payload，否则返回 null。
 *
 * - PONG / 非 JSON / 未知 eventType → null（调用方保持原有消息处理逻辑）；
 * - `event.data` 可能是 JSON 字符串（微信/支付宝）也可能是已解析对象（部分宿主），两种都兼容；
 * - reason/subReason 的降级规则与 HTTP 401 完全一致（未知值丢弃、subReason 仅 KICK_OUT）；
 * - `recipientId/recipientType/tenantId` 是服务端路由字段，前端不使用。
 */
export function parseSessionInvalidPushEvent(raw: unknown): SessionInvalidPayload | null {
  const record = asRecord(raw)
  if (!record || record.eventType !== SESSION_INVALID_PUSH_EVENT)
    return null
  const reason = normalizeSessionInvalidReason(record.reason)
  const subReason = reason === 'KICK_OUT' ? normalizeSessionInvalidSubReason(record.subReason) : ''
  return {
    reason,
    subReason,
    invalidAt: normalizeSessionInvalidTimestamp(record.invalidAt),
    device: normalizeSessionInvalidDevice(record.device),
  }
}

/** 请求层抛错时只需要标题，不必组装整份文案。 */
export function resolveSessionInvalidTitle(
  input: SessionInvalidCopyInput = {},
  t: TranslateFn = translate,
): string {
  return resolveSessionInvalidCopy(input, t).title
}
