import type { ResponseInterceptorConfig } from '@vben/request';

import type {
  SessionInvalidDevice,
  SessionInvalidPayload,
} from '#/store/session';

import { LOGIN_PATH } from '@vben/constants';

import {
  normalizeSessionInvalidReason,
  normalizeSessionInvalidSubReason,
  useSessionStore,
} from '#/store/session';
import { broadcastSessionInvalid } from '#/store/session-sync';

/**
 * 会话失效分流（管理端）。
 *
 * 职责边界：
 * - 本模块只负责「看响应、写状态、给出重认证动作」，不负责弹窗 UI、不负责登出；
 * - 弹窗与登出分别由 `#/layouts/session-invalid-modal.vue` 与 `#/store/auth.ts` 承担，
 *   方便单测覆盖 401 分流矩阵而不必渲染整棵应用。
 *
 * 契约见 `docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md`。
 */

/** 请求配置上的内部标记：免登请求（`isToken: false`）不参与会话失效分流。 */
export const SESSION_INVALID_SKIP_FLAG = '__skipSessionInvalid';

interface SessionInvalidRequestConfig {
  [SESSION_INVALID_SKIP_FLAG]?: boolean;
}

/** 图形验证码遮罩的 z-index（app 级 Verify 组件固定 1001），弹窗必须低于它。 */
export const SESSION_INVALID_MODAL_Z_INDEX = 1000;

type AnyRecord = Record<string, unknown>;

function isRecord(value: unknown): value is AnyRecord {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

/**
 * 兼容响应体为 JSON 字符串的极端情况（axios 一般已自动 parse），
 * 非对象一律视为「无数据」。
 */
function parseObject(value: unknown): AnyRecord | null {
  if (typeof value === 'string') {
    try {
      const parsed: unknown = JSON.parse(value);
      return isRecord(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }
  return isRecord(value) ? value : null;
}

/** 取出 401 响应体（`{ code, msg, data }`）；不是会话失效响应时返回 null。 */
export function getSessionInvalidResponseBody(
  error: unknown,
): AnyRecord | null {
  const response = (error as any)?.response;
  const body = parseObject(response?.data);
  if (!body || body.code !== 401) {
    return null;
  }
  return body;
}

/** 该 401 是否来自免登请求（`isToken: false`）：保持现状，不清态、只提示。 */
export function isSessionInvalidSkippedRequest(error: unknown): boolean {
  const config = (error as any)?.config as
    SessionInvalidRequestConfig | undefined;
  return config?.[SESSION_INVALID_SKIP_FLAG] === true;
}

/**
 * 从 401 响应体解析会话失效信息（reason/subReason 可能缺失，device 全部可空）。
 *
 * @returns 会话失效载荷；非 401 / 免登 401 / 数据不可用均返回 null（走通用错误提示）
 */
export function resolveSessionInvalidPayload(
  error: unknown,
): null | SessionInvalidPayload {
  if (isSessionInvalidSkippedRequest(error)) {
    return null;
  }
  const body = getSessionInvalidResponseBody(error);
  if (!body) {
    return null;
  }
  const data = parseObject(body.data);
  if (!data) {
    // 旧后端（或滚动发布中的旧实例）：没有 data，按「无 reason」走通用文案。
    return {};
  }
  const invalidAt =
    typeof data.invalidAt === 'string' && data.invalidAt.trim()
      ? data.invalidAt.trim()
      : null;
  return {
    device: isRecord(data.device)
      ? (data.device as unknown as SessionInvalidDevice)
      : null,
    invalidAt,
    reason: normalizeSessionInvalidReason(data.reason),
    // 二期：仅 KICK_OUT 时有意义；未知 wire 值在这里就丢弃（按无 subReason 降级）
    subReason: normalizeSessionInvalidSubReason(data.subReason),
  };
}

/** 会话失效错误已由弹窗解释，通用错误 toast 必须跳过，避免「弹窗 + 红 toast」重复。 */
export function shouldSuppressSessionInvalidErrorToast(
  error: unknown,
): boolean {
  if (!useSessionStore().invalid) {
    return false;
  }
  const body = getSessionInvalidResponseBody(error);
  return body?.code === 401;
}

/**
 * 会话失效原因解析拦截器。
 *
 * <p>必须注册在 {@link import('@vben/request').authenticateResponseInterceptor}
 * **之前**：axios 的 response rejected 按注册顺序执行，先落状态再重认证，
 * 弹窗才能拿到 reason/device。本拦截器只写状态、然后在原错误上继续 reject，
 * 不吞错误（后续拦截器仍按原有语义工作）。</p>
 */
export function createSessionInvalidResponseInterceptor(): ResponseInterceptorConfig {
  return {
    rejected: (error: unknown) => {
      const payload = resolveSessionInvalidPayload(error);
      if (payload) {
        const sessionStore = useSessionStore();
        // 只有「本标签页首次进入失效态」才广播给其它标签页（收到广播的标签页不再二次广播）
        if (sessionStore.invalidate(payload)) {
          broadcastSessionInvalid({
            detectedAt: sessionStore.detectedAt,
            device: sessionStore.device,
            invalidAt: sessionStore.invalidAt,
            reason: sessionStore.reason,
            subReason: sessionStore.subReason,
          });
        }
      }
      throw error;
    },
  };
}

/** 重新认证动作：静默放行 / 弹窗原地重登 / 降级为登出回登录页。 */
export type ReAuthAction = 'ignore' | 'logout' | 'modal';

export interface ReAuthDecisionInput {
  /** 当前路由是否处于 BasicLayout（应用主框架）上下文 */
  inBasicLayout: boolean;
  /** `preferences.app.loginExpiredMode` */
  loginExpiredMode: string;
  /** 当前路由是否就是登录页 */
  onLoginPage: boolean;
  /** session store 是否已进入失效态（由解析拦截器写入） */
  sessionInvalid: boolean;
}

/**
 * 决定 `doReAuthenticate` 的动作。
 *
 * 规则（与契约 §3 行为矩阵一致）：
 * 1. `page` 模式：保留原有兜底，直接登出；
 * 2. `modal` 模式 + 未进入失效态（免登请求 / 业务 401）：不清态、只提示；
 * 3. `modal` 模式 + 登录页 / 无布局挂载点：降级登出（弹窗没有地方可挂）；
 * 4. 其余：弹窗原地重登，**不要求 `isAccessChecked`**（刷新后首请求 401 也要弹）。
 */
export function resolveReAuthAction(input: ReAuthDecisionInput): ReAuthAction {
  if (input.loginExpiredMode !== 'modal') {
    return 'logout';
  }
  if (!input.sessionInvalid) {
    return 'ignore';
  }
  if (input.onLoginPage || !input.inBasicLayout) {
    return 'logout';
  }
  return 'modal';
}

export interface RouteContextLike {
  matched: Array<{ name?: unknown }>;
  meta?: null | Record<string, unknown>;
  path?: string;
}

/**
 * 当前路由是否位于 BasicLayout 内（会话失效弹窗唯一挂载点）。
 *
 * - 初始导航尚未解析（刷新后首个 401、`matched` 为空）：返回 true，
 *   由 app 级兜底弹窗承载（此时 BasicLayout 还没挂载，需要在 app.vue 兜底挂载）；
 * - 独立窗口页（`noBasicLayout + sessionInvalidModal`）：返回 true，
 *   由页面内的 SessionInvalidModal 承载（否则会降级登出并清掉持久化 token）；
 * - 登录页 / 其它独立页（`noBasicLayout`）：返回 false，交给调用方降级登出。
 */
export function isBasicLayoutContext(
  route: null | RouteContextLike | undefined,
): boolean {
  if (!route) {
    return false;
  }
  // 独立窗口页自行挂载会话失效弹窗：必须走页内弹窗（modal），
  // 不能降级 logout——那会执行 resetAllStores() 清掉持久化 token，连主窗口一起登出。
  if (route.meta?.sessionInvalidModal === true) {
    return true;
  }
  if (route.meta?.noBasicLayout === true) {
    return false;
  }
  if (route.matched.length === 0) {
    return (route.path ?? '') !== LOGIN_PATH;
  }
  return route.matched.some((record) => record.name === 'Root');
}
