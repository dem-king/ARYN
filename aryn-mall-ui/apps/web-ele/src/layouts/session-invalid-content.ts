import type { Dayjs } from 'dayjs';

import type {
  SessionInvalidDevice,
  SessionInvalidReason,
  SessionInvalidSubReason,
} from '#/store/session';

import { preferences } from '@vben/preferences';

import dayjs from 'dayjs';

import { $t } from '#/locales';

/**
 * 会话失效弹窗的文案映射与设备行渲染（纯函数，便于表驱动单测）。
 *
 * 文案逐字取自《一期实现契约》§4 与《二期实现契约》§2，key 组织见 README §8.2：
 * `sessionInvalid.reason.<REASON[_SUBREASON]>.title/content` + `sessionInvalid.device.*`。
 * 不允许前端解析服务端 `msg`，原因只认 `data.reason` + `data.subReason`。
 */

type Translate = (key: string, params?: Record<string, unknown>) => string;

export const UNKNOWN_SESSION_INVALID_REASON = 'UNKNOWN';

/**
 * 管理端会展示的 KICK_OUT 细分原因（二期契约 §2）。
 *
 * `MEMBER_DISABLED` / `DELIVERY_REVOKED` 是移动端专用，管理端不出现；
 * 即使误下发也按「未知值」降级成 KICK_OUT 通用文案，不新增无意义界面。
 */
const KICK_OUT_ADMIN_SUB_REASONS: ReadonlySet<SessionInvalidSubReason> =
  new Set<SessionInvalidSubReason>([
    'ACCOUNT_DELETED',
    'ACCOUNT_DISABLED',
    'ADMIN_KICK',
    'PASSWORD_CHANGED',
    'TENANT_DISABLED',
  ]);

/** 这些细分原因重登必然失败：只渲染一个退出按钮（二期契约 §2 按钮规则）。 */
const NO_RELOGIN_SUB_REASONS: ReadonlySet<SessionInvalidSubReason> =
  new Set<SessionInvalidSubReason>([
    'ACCOUNT_DELETED',
    'ACCOUNT_DISABLED',
    'TENANT_DISABLED',
  ]);

/** 单按钮文案（可重新登录=false 时使用）。 */
const SINGLE_ACTION_TEXT_KEYS: Partial<
  Record<SessionInvalidSubReason, 'backToLogin' | 'exitShort'>
> = {
  ACCOUNT_DELETED: 'backToLogin',
  ACCOUNT_DISABLED: 'backToLogin',
  TENANT_DISABLED: 'exitShort',
};

export interface SessionInvalidViewState {
  /** 本端检测到失效的时间戳（缺 invalidAt/loginTime 时兜底展示） */
  detectedAt?: null | number;
  device?: null | SessionInvalidDevice;
  invalidAt?: null | string;
  reason?: null | SessionInvalidReason;
  /** 细分被踢原因（仅 reason=KICK_OUT 时有值） */
  subReason?: null | SessionInvalidSubReason;
}

export interface SessionInvalidView {
  /** 正文（含设备行，按契约格式拼接） */
  body: string;
  /** 是否允许「重新登录」；false 时只渲染一个退出按钮 */
  canRelogin: boolean;
  /** 设备行；无有效设备信息时为空串（整行不渲染） */
  deviceLine: string;
  /** 可重新登录=false 时唯一按钮的文案；true 时为 null */
  singleActionText: null | string;
  title: string;
}

const defaultTranslate: Translate = (key, params) => {
  return params ? $t(key, params) : $t(key);
};

/** 设备行分隔符：中文用全角竖线，英文用 " | "（与契约的中文格式保持一致）。 */
function resolveDeviceSeparator() {
  return preferences.app.locale === 'en-US' ? ' | ' : '｜';
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/**
 * 把 reason + subReason 收敛成文案 key：
 * - `KICK_OUT` + 管理端可展示的 subReason → `KICK_OUT_<SUBREASON>`；
 * - 其它（含未知 subReason / 移动端专用 subReason）→ 一期原有 key 或 `UNKNOWN`。
 */
export function resolveSessionInvalidContentKey(
  reason: null | SessionInvalidReason | undefined,
  subReason: null | SessionInvalidSubReason | undefined,
): string {
  if (reason === 'KICK_OUT') {
    return subReason && KICK_OUT_ADMIN_SUB_REASONS.has(subReason)
      ? `KICK_OUT_${subReason}`
      : 'KICK_OUT';
  }
  return reason ?? UNKNOWN_SESSION_INVALID_REASON;
}

/**
 * 是否允许原地重登（二期契约 §2）：
 * 账号停用 / 账号删除 / 租户停用重登必然失败，只给退出按钮，避免用户白试。
 */
export function canSessionInvalidRelogin(
  reason: null | SessionInvalidReason | undefined,
  subReason: null | SessionInvalidSubReason | undefined,
): boolean {
  if (reason !== 'KICK_OUT' || !subReason) {
    return true;
  }
  return !NO_RELOGIN_SUB_REASONS.has(subReason);
}

/**
 * 时间展示：当天 `HH:mm`，跨天 `MM-DD HH:mm`（契约 §4）。
 * 非法/缺失时间返回空串，由调用方决定降级。
 */
export function formatSessionInvalidTime(
  value: null | number | string | undefined,
  now: Dayjs = dayjs(),
): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const date = dayjs(value);
  if (!date.isValid()) {
    return '';
  }
  return date.isSame(now, 'day')
    ? date.format('HH:mm')
    : date.format('MM-DD HH:mm');
}

/**
 * 设备行（仅 `data.device` 存在时渲染，缺失整行隐藏，不显示「未知设备」）：
 * 管理端格式 `登录设备：{browser} · {os}｜地点：{location}｜IP：{ip}｜时间：{HH:mm}`；
 * `browser`/`os` 任一为空则省略该片段，`location`/`ip`/`loginTime` 为空省略对应片段
 * （ip 为 2026-11-01 契约增量，旧后端无此字段时自然隐藏）。
 */
export function resolveSessionInvalidDeviceLine(
  device: null | SessionInvalidDevice | undefined,
  t: Translate = defaultTranslate,
  now: Dayjs = dayjs(),
): string {
  if (!device) {
    return '';
  }
  const browser = normalizeText(device.browser);
  const os = normalizeText(device.os);
  const location = normalizeText(device.location);
  const ip = normalizeText(device.ip);
  const time = formatSessionInvalidTime(device.loginTime, now);

  const parts: string[] = [];
  const hardware = [browser, os].filter(Boolean).join(' · ');
  if (hardware) {
    parts.push(t('sessionInvalid.device.loginDevice', { value: hardware }));
  }
  if (location) {
    parts.push(t('sessionInvalid.device.location', { value: location }));
  }
  if (ip) {
    parts.push(t('sessionInvalid.device.ip', { value: ip }));
  }
  if (time) {
    parts.push(t('sessionInvalid.device.time', { value: time }));
  }
  return parts.join(resolveDeviceSeparator());
}

/** 正文里的 `{time}`：优先失效时间，其次新设备登录时间，最后本端检测时间。 */
function resolveInvalidTime(
  state: SessionInvalidViewState,
  now: Dayjs,
): string {
  return (
    formatSessionInvalidTime(state.invalidAt, now) ||
    formatSessionInvalidTime(state.device?.loginTime, now) ||
    formatSessionInvalidTime(state.detectedAt, now) ||
    formatSessionInvalidTime(now.valueOf(), now)
  );
}

/**
 * 解析弹窗标题/正文/设备行/按钮能力。
 *
 * <p>未知 reason、未知 subReason（含旧后端不带 `data.reason`）统一降级，
 * 不报错、不留空。设备行以独立行插入正文，只影响展示换行，不改变契约文案字符。</p>
 */
export function resolveSessionInvalidView(
  state: SessionInvalidViewState,
  t: Translate = defaultTranslate,
  now: Dayjs = dayjs(),
): SessionInvalidView {
  const contentKey = resolveSessionInvalidContentKey(
    state.reason,
    state.subReason,
  );
  const deviceLine = resolveSessionInvalidDeviceLine(state.device, t, now);
  const body = t(`sessionInvalid.reason.${contentKey}.content`, {
    deviceLine: deviceLine ? `\n${deviceLine}\n` : '',
    time: resolveInvalidTime(state, now),
  })
    .replaceAll(/\n{2,}/g, '\n')
    .trim();

  const canRelogin = canSessionInvalidRelogin(state.reason, state.subReason);
  const singleActionKey =
    state.reason === 'KICK_OUT' && state.subReason
      ? SINGLE_ACTION_TEXT_KEYS[state.subReason]
      : undefined;

  return {
    body,
    canRelogin,
    deviceLine,
    singleActionText:
      canRelogin || !singleActionKey
        ? null
        : t(`sessionInvalid.${singleActionKey}`),
    title: t(`sessionInvalid.reason.${contentKey}.title`),
  };
}
