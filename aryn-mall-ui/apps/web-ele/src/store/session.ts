import { computed, ref } from 'vue';

import { useAccessStore } from '@vben/stores';

import { defineStore } from 'pinia';

/**
 * 会话失效原因。
 *
 * <p>wire 值与后端契约（`SessionInvalidReason`）逐字一致、大小写敏感，
 * 前端禁止解析服务端 `msg` 文案判断原因。详见
 * `docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md` §1。</p>
 */
export type SessionInvalidReason =
  | 'BE_REPLACED'
  | 'KICK_OUT'
  | 'NO_PREFIX'
  | 'NOT_TOKEN'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_FREEZE'
  | 'TOKEN_INVALID';

/**
 * 细分「被踢」原因（二期契约 §1）。
 *
 * <p>只在 `reason=KICK_OUT` 时下发；wire 值大小写敏感。前端未知值一律按
 * 「无 subReason」降级（走 KICK_OUT 通用文案），不得报错/空白。</p>
 */
export type SessionInvalidSubReason =
  | 'ACCOUNT_DELETED'
  | 'ACCOUNT_DISABLED'
  | 'ADMIN_KICK'
  | 'DELIVERY_REVOKED'
  | 'MEMBER_DISABLED'
  | 'PASSWORD_CHANGED'
  | 'TENANT_DISABLED';

/**
 * 被顶下线时新设备的信息（可空，缺字段即隐藏对应片段）。
 * 2026-11-01 契约增量：新增 `ip`（产品决策放开，帮助用户识别新登录设备）；
 * 仍不含 token、手机号等敏感字段。
 */
export interface SessionInvalidDevice {
  browser?: string;
  ip?: string;
  location?: string;
  loginTime?: string;
  os?: string;
}

/** 401 响应体 `data` 的会话失效信息（全部可空，兼容旧后端）。 */
export interface SessionInvalidPayload {
  /** 其它标签页广播时带过来的检测时间，用于保留首次失效时间 */
  detectedAt?: null | number;
  device?: null | SessionInvalidDevice;
  invalidAt?: null | string;
  reason?: null | SessionInvalidReason;
  /** 仅 `reason=KICK_OUT` 时有意义；未知值在归一化时丢弃 */
  subReason?: null | SessionInvalidSubReason;
}

const SESSION_INVALID_REASONS: readonly SessionInvalidReason[] = [
  'BE_REPLACED',
  'KICK_OUT',
  'NO_PREFIX',
  'NOT_TOKEN',
  'TOKEN_EXPIRED',
  'TOKEN_FREEZE',
  'TOKEN_INVALID',
];

const SESSION_INVALID_REASON_SET = new Set<string>(SESSION_INVALID_REASONS);

const SESSION_INVALID_SUB_REASONS: readonly SessionInvalidSubReason[] = [
  'ACCOUNT_DELETED',
  'ACCOUNT_DISABLED',
  'ADMIN_KICK',
  'DELIVERY_REVOKED',
  'MEMBER_DISABLED',
  'PASSWORD_CHANGED',
  'TENANT_DISABLED',
];

const SESSION_INVALID_SUB_REASON_SET = new Set<string>(
  SESSION_INVALID_SUB_REASONS,
);

/**
 * 把服务端下发的原因收敛成已知枚举；未知/缺省一律返回 null（走通用文案）。
 * 旧后端不带 `data.reason`、或后端将来新增原因时，不得因此报错或空白。
 */
export function normalizeSessionInvalidReason(
  value: unknown,
): null | SessionInvalidReason {
  return typeof value === 'string' && SESSION_INVALID_REASON_SET.has(value)
    ? (value as SessionInvalidReason)
    : null;
}

/**
 * 把服务端下发的 `subReason` 收敛成已知枚举；未知值返回 null（按无 subReason 降级）。
 */
export function normalizeSessionInvalidSubReason(
  value: unknown,
): null | SessionInvalidSubReason {
  return typeof value === 'string' && SESSION_INVALID_SUB_REASON_SET.has(value)
    ? (value as SessionInvalidSubReason)
    : null;
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/**
 * UA 解析不出时后端在线记录里会存 hutool 兜底值 `Unknown`（对用户无信息量），
 * 存量 Redis 记录里也有，这里按缺失处理，让设备片段隐藏。
 */
function normalizeDeviceText(value: unknown): string {
  const text = normalizeText(value);
  return text.toLowerCase() === 'unknown' ? '' : text;
}

/**
 * 清洗设备信息：只保留字符串字段、去空；一个有效字段都没有时整体返回 null，
 * 让弹窗整行隐藏（不展示「未知设备」）。
 */
export function normalizeSessionInvalidDevice(
  value: unknown,
): null | SessionInvalidDevice {
  if (!value || typeof value !== 'object') {
    return null;
  }
  const raw = value as Record<string, unknown>;
  const device: SessionInvalidDevice = {
    browser: normalizeDeviceText(raw.browser) || undefined,
    ip: normalizeDeviceText(raw.ip) || undefined,
    location: normalizeDeviceText(raw.location) || undefined,
    loginTime: normalizeText(raw.loginTime) || undefined,
    os: normalizeDeviceText(raw.os) || undefined,
  };
  const hasValue = Object.values(device).some(Boolean);
  return hasValue ? device : null;
}

/**
 * 会话失效状态机（应用级单例）。
 *
 * <p>「被顶下线 / 登录过期 / 被踢下线」共用同一状态：一旦进入失效态，
 * 弹窗只弹一次，后续并发 401 静默短路，避免叠弹窗、叠 toast、401 风暴。</p>
 *
 * <p>纯内存、**不持久化**：刷新后若 token 已失效，由路由守卫 / 首个请求重新触发；
 * 持久化弹窗状态会产生「永远弹不掉的弹窗」（见设计文档 §6.5）。</p>
 */
export const useSessionStore = defineStore('session', () => {
  /** 当前会话是否已判定失效 */
  const invalid = ref(false);
  /** 服务端下发的原因（可为空，兼容旧后端） */
  const reason = ref<null | SessionInvalidReason>(null);
  /** 细分被踢原因（仅 reason=KICK_OUT 时可能出现，可为空） */
  const subReason = ref<null | SessionInvalidSubReason>(null);
  /** 失效发生时间（ISO8601，可为空） */
  const invalidAt = ref<null | string>(null);
  /** 顶号设备信息（可为空） */
  const device = ref<null | SessionInvalidDevice>(null);
  /** 本端检测到失效的时间戳，用于缺时间时兜底展示 */
  const detectedAt = ref<null | number>(null);

  const isInvalid = computed(() => invalid.value);

  /**
   * 进入失效态。**幂等**：已失效时只保留首次记录的原因/设备，
   * 并发 401 不会互相覆盖、也不会重复触发弹窗。
   *
   * <p>进入失效态时若处于锁屏（`LockScreen` 是 `z-[2000]` 全屏层，会盖住
   * `z-index:1000` 的会话失效弹窗），先解锁再让调用方弹窗（二期契约 §3.3）。</p>
   *
   * @returns 本次调用是否真正发生了状态迁移
   */
  function invalidate(payload: SessionInvalidPayload = {}, now = Date.now()) {
    if (invalid.value) {
      return false;
    }
    const normalizedReason = normalizeSessionInvalidReason(payload.reason);
    invalid.value = true;
    reason.value = normalizedReason;
    // 契约：subReason 只在 reason=KICK_OUT 时出现；其它 reason 带过来也一律丢弃
    subReason.value =
      normalizedReason === 'KICK_OUT'
        ? normalizeSessionInvalidSubReason(payload.subReason)
        : null;
    invalidAt.value =
      typeof payload.invalidAt === 'string' && payload.invalidAt.trim()
        ? payload.invalidAt.trim()
        : null;
    device.value = normalizeSessionInvalidDevice(payload.device);
    detectedAt.value =
      typeof payload.detectedAt === 'number' &&
      Number.isFinite(payload.detectedAt)
        ? payload.detectedAt
        : now;

    const accessStore = useAccessStore();
    if (accessStore.isLockScreen) {
      // 已确认会话失效：解锁后弹窗才可见（不影响正常锁屏/解锁流程）
      accessStore.unlockScreen();
    }
    return true;
  }

  /** 清空失效态（重新登录成功 / 主动退出 / 重置 store 时调用），幂等。 */
  function reset() {
    invalid.value = false;
    reason.value = null;
    subReason.value = null;
    invalidAt.value = null;
    device.value = null;
    detectedAt.value = null;
  }

  // pinia 的 resetAllStores() 会遍历所有 store 调 $reset()；
  // setup store 默认没有 $reset，这里显式提供，避免退出登录时抛错中断。
  function $reset() {
    reset();
  }

  return {
    $reset,
    detectedAt,
    device,
    invalid,
    invalidAt,
    invalidate,
    isInvalid,
    reason,
    reset,
    subReason,
  };
});
