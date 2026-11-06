import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { defineOverridesPreferences } from '@vben/preferences';

import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { useSessionStore } from '#/store/session';

import {
  createSessionInvalidResponseInterceptor,
  getSessionInvalidResponseBody,
  isBasicLayoutContext,
  isSessionInvalidSkippedRequest,
  resolveReAuthAction,
  resolveSessionInvalidPayload,
  SESSION_INVALID_SKIP_FLAG,
  shouldSuppressSessionInvalidErrorToast,
} from './session-invalid';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const readAppSource = (path: string) =>
  readFileSync(resolve(appRoot, 'src', path), 'utf8');

interface UnauthorizedErrorInput {
  /** `error.response.data`（响应体） */
  body?: Record<string, unknown> | string;
  /** `error.response.data.data`（会话失效信息） */
  data?: unknown;
  /** 是否免登请求（`isToken: false`） */
  skip?: boolean;
  status?: number;
}

function buildUnauthorizedError(input: UnauthorizedErrorInput = {}) {
  const body =
    input.body ??
    ({
      code: 401,
      data: input.data,
      msg: '账号已在其他设备登录',
    } as Record<string, unknown>);
  return {
    config: { [SESSION_INVALID_SKIP_FLAG]: input.skip === true },
    response: { data: body, status: input.status ?? 200 },
  };
}

/**
 * 解析拦截器同步 `throw` 原错误（axios 会把它转成 rejected promise），
 * 这里直接捕获并返回被抛出的错误对象，用于断言「原样继续抛、不吞」。
 */
function captureThrown(run: () => unknown): unknown {
  try {
    run();
  } catch (error) {
    return error;
  }
  return undefined;
}

const beReplacedData = {
  device: {
    browser: 'Chrome 120',
    location: '上海市',
    loginTime: '2026-10-28T21:32:05+08:00',
    os: 'Windows 10',
  },
  invalidAt: '2026-10-28T21:32:07+08:00',
  reason: 'BE_REPLACED',
};

describe('session invalid response parsing', () => {
  it('extracts reason/invalidAt/device from data.data', () => {
    const payload = resolveSessionInvalidPayload(
      buildUnauthorizedError({ data: beReplacedData }),
    );

    expect(payload).toEqual({
      device: beReplacedData.device,
      invalidAt: '2026-10-28T21:32:07+08:00',
      reason: 'BE_REPLACED',
      subReason: null,
    });
  });

  it('parses TOKEN_EXPIRED without device info', () => {
    const payload = resolveSessionInvalidPayload(
      buildUnauthorizedError({
        data: {
          invalidAt: '2026-10-28T22:10:00+08:00',
          reason: 'TOKEN_EXPIRED',
        },
      }),
    );

    expect(payload).toEqual({
      device: null,
      invalidAt: '2026-10-28T22:10:00+08:00',
      reason: 'TOKEN_EXPIRED',
      subReason: null,
    });
  });

  it('falls back to "no reason" when data is missing (legacy backend)', () => {
    expect(
      resolveSessionInvalidPayload(buildUnauthorizedError({ data: undefined })),
    ).toEqual({});
  });

  it('falls back to "no reason" when data is not an object', () => {
    expect(
      resolveSessionInvalidPayload(buildUnauthorizedError({ data: 'null' })),
    ).toEqual({});
  });

  it('degrades unknown reason wire values to null instead of throwing', () => {
    const payload = resolveSessionInvalidPayload(
      buildUnauthorizedError({
        data: { reason: 'SOMETHING_NEW', subReason: 'ADMIN_KICK' },
      }),
    );

    // 已知 wire 值的 subReason 在解析层保留；是否生效由 session store 按
    // 「仅 reason=KICK_OUT 才接受 subReason」统一收紧（见 session.test.ts）
    expect(payload).toEqual({
      device: null,
      invalidAt: null,
      reason: null,
      subReason: 'ADMIN_KICK',
    });
  });

  it('parses known subReason and drops unknown wire values', () => {
    const known = resolveSessionInvalidPayload(
      buildUnauthorizedError({
        data: { reason: 'KICK_OUT', subReason: 'ACCOUNT_DISABLED' },
      }),
    );
    expect(known).toMatchObject({
      reason: 'KICK_OUT',
      subReason: 'ACCOUNT_DISABLED',
    });

    const unknown = resolveSessionInvalidPayload(
      buildUnauthorizedError({
        data: { reason: 'KICK_OUT', subReason: 'SOMETHING_NEW' },
      }),
    );
    expect(unknown).toMatchObject({ reason: 'KICK_OUT', subReason: null });
  });

  it('keeps partial device payloads (fields are all optional)', () => {
    const payload = resolveSessionInvalidPayload(
      buildUnauthorizedError({ data: { device: { os: 'macOS' } } }),
    );

    expect(payload?.device).toEqual({ os: 'macOS' });
    expect(payload?.reason).toBeNull();
  });

  it('ignores non-401 business codes', () => {
    expect(
      resolveSessionInvalidPayload({
        config: {},
        response: { data: { code: 500, msg: 'boom' }, status: 200 },
      }),
    ).toBeNull();
  });

  it('ignores 403 permission errors (must not clear session)', () => {
    expect(
      resolveSessionInvalidPayload({
        config: {},
        response: { data: { code: 403, msg: '无权限' }, status: 403 },
      }),
    ).toBeNull();
  });

  it('ignores 401 from public requests (isToken: false)', () => {
    expect(
      resolveSessionInvalidPayload(
        buildUnauthorizedError({ data: beReplacedData, skip: true }),
      ),
    ).toBeNull();
    expect(
      isSessionInvalidSkippedRequest(
        buildUnauthorizedError({ data: beReplacedData, skip: true }),
      ),
    ).toBe(true);
  });

  it('parses JSON string response bodies defensively', () => {
    expect(
      resolveSessionInvalidPayload(
        buildUnauthorizedError({
          body: JSON.stringify({ code: 401, data: beReplacedData }),
        }),
      ),
    ).toEqual({
      device: beReplacedData.device,
      invalidAt: '2026-10-28T21:32:07+08:00',
      reason: 'BE_REPLACED',
      subReason: null,
    });
  });

  it('returns null for non-object response bodies', () => {
    expect(
      getSessionInvalidResponseBody({
        config: {},
        response: { data: '<html>502</html>', status: 502 },
      }),
    ).toBeNull();
  });
});

describe('session invalid interceptor', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('writes the session store and rethrows the original error', async () => {
    const interceptor = createSessionInvalidResponseInterceptor();
    const error = buildUnauthorizedError({ data: beReplacedData });

    expect(captureThrown(() => interceptor.rejected?.(error))).toBe(error);

    const store = useSessionStore();
    expect(store.invalid).toBe(true);
    expect(store.reason).toBe('BE_REPLACED');
    expect(store.device?.browser).toBe('Chrome 120');
  });

  it('records the invalid state even for legacy 401 without data', async () => {
    const interceptor = createSessionInvalidResponseInterceptor();
    const error = buildUnauthorizedError({ data: undefined });

    expect(captureThrown(() => interceptor.rejected?.(error))).toBe(error);

    const store = useSessionStore();
    expect(store.invalid).toBe(true);
    expect(store.reason).toBeNull();
  });

  it('does not touch the session state for 403', async () => {
    const interceptor = createSessionInvalidResponseInterceptor();
    const error = {
      config: {},
      response: { data: { code: 403, msg: '无权限' }, status: 403 },
    };

    expect(captureThrown(() => interceptor.rejected?.(error))).toBe(error);
    expect(useSessionStore().invalid).toBe(false);
  });

  it('does not touch the session state for public request 401', async () => {
    const interceptor = createSessionInvalidResponseInterceptor();
    const error = buildUnauthorizedError({ data: beReplacedData, skip: true });

    expect(captureThrown(() => interceptor.rejected?.(error))).toBe(error);
    expect(useSessionStore().invalid).toBe(false);
  });

  it('keeps the first reason for concurrent 401 (single dialog)', async () => {
    const interceptor = createSessionInvalidResponseInterceptor();
    const first = buildUnauthorizedError({
      data: { invalidAt: '2026-10-28T21:32:07+08:00', reason: 'BE_REPLACED' },
    });
    const second = buildUnauthorizedError({
      data: { reason: 'TOKEN_EXPIRED' },
    });

    expect(captureThrown(() => interceptor.rejected?.(first))).toBe(first);
    expect(captureThrown(() => interceptor.rejected?.(second))).toBe(second);

    const store = useSessionStore();
    expect(store.reason).toBe('BE_REPLACED');
    expect(store.invalidAt).toBe('2026-10-28T21:32:07+08:00');
  });

  it('suppresses the generic error toast only for session-invalid 401', () => {
    const store = useSessionStore();
    const error = buildUnauthorizedError({ data: beReplacedData });

    // 未进入失效态：照常提示（免登 401 / 403 / 业务错误）
    expect(shouldSuppressSessionInvalidErrorToast(error)).toBe(false);

    store.invalidate({ reason: 'BE_REPLACED' });
    expect(shouldSuppressSessionInvalidErrorToast(error)).toBe(true);
    // 非 401 的错误在失效态下仍按原逻辑提示（如网络错误）
    expect(
      shouldSuppressSessionInvalidErrorToast({
        config: {},
        response: { data: { code: 500 }, status: 500 },
      }),
    ).toBe(false);
  });
});

describe('re-authentication decision matrix', () => {
  const base = {
    inBasicLayout: true,
    loginExpiredMode: 'modal',
    onLoginPage: false,
    sessionInvalid: true,
  };

  it.each([
    {
      desc: 'page 模式保留原有兜底：直接登出',
      input: { ...base, loginExpiredMode: 'page' },
      expected: 'logout',
    },
    {
      desc: 'page 模式 + 免登 401：仍登出（保持现状）',
      input: {
        ...base,
        loginExpiredMode: 'page',
        sessionInvalid: false,
      },
      expected: 'logout',
    },
    {
      desc: 'modal 模式 + 未进入失效态（免登/业务 401）：不清态只提示',
      input: { ...base, sessionInvalid: false },
      expected: 'ignore',
    },
    {
      desc: 'modal 模式 + 在登录页：降级登出',
      input: { ...base, onLoginPage: true },
      expected: 'logout',
    },
    {
      desc: 'modal 模式 + 不在 BasicLayout：降级登出',
      input: { ...base, inBasicLayout: false },
      expected: 'logout',
    },
    {
      desc: 'modal 模式 + 布局内 + 已失效：弹窗原地重登',
      input: base,
      expected: 'modal',
    },
  ])('$desc', ({ input, expected }) => {
    expect(resolveReAuthAction(input)).toBe(expected);
  });
});

describe('basic layout context detection', () => {
  it('treats routes under the Root (BasicLayout) parent as modal-capable', () => {
    expect(
      isBasicLayoutContext({
        matched: [{ name: 'Root' }, { name: 'OrderList' }],
        path: '/order/list',
      }),
    ).toBe(true);
  });

  it('rejects auth pages / independent pages without BasicLayout', () => {
    expect(
      isBasicLayoutContext({
        matched: [{ name: 'Authentication' }, { name: 'Login' }],
        path: '/auth/login',
      }),
    ).toBe(false);
    expect(
      isBasicLayoutContext({
        matched: [{ name: 'PagePreview' }],
        meta: { noBasicLayout: true },
        path: '/page-preview/abc',
      }),
    ).toBe(false);
    // 独立工作台页自行挂载 SessionInvalidModal：必须走页内弹窗（modal），
    // 不能降级 logout——否则 resetAllStores() 会清掉持久化 token，连主窗口一起登出。
    expect(
      isBasicLayoutContext({
        matched: [{ name: 'MessageServiceWindow' }],
        meta: { noBasicLayout: true, sessionInvalidModal: true },
        path: '/message/service-window',
      }),
    ).toBe(true);
  });

  it('optimistically keeps the modal for unresolved initial navigation (refresh)', () => {
    expect(isBasicLayoutContext({ matched: [], path: '/order/list' })).toBe(
      true,
    );
  });

  it('rejects the login page even when navigation is unresolved', () => {
    expect(isBasicLayoutContext({ matched: [], path: '/auth/login' })).toBe(
      false,
    );
    expect(isBasicLayoutContext(null)).toBe(false);
  });
});

describe('session invalid wiring contracts', () => {
  const preferencesSource = readAppSource('preferences.ts');
  const requestSource = readAppSource('api/request.ts');
  const authStoreSource = readAppSource('store/auth.ts');
  const basicLayoutSource = readAppSource('layouts/basic.vue');
  const appSource = readAppSource('app.vue');
  const mainSource = readAppSource('main.ts');
  const interceptorSource = readAppSource('api/session-invalid.ts');
  const messageStoreSource = readAppSource('store/message.ts');
  const sessionStoreSource = readAppSource('store/session.ts');

  const doReAuthenticateBlock =
    /async function doReAuthenticate\(\) \{([\s\S]*?)\n {2}\}/.exec(
      requestSource,
    )?.[1] ?? '';
  const errorMessageBlock =
    /errorMessageResponseInterceptor\(\(msg: string, error\) => \{([\s\S]*?)\n {4}\}\),/.exec(
      requestSource,
    )?.[1] ?? '';
  const logoutBlock =
    /async function logout\(redirect: boolean = true\) \{([\s\S]*?)\n {2}\}/.exec(
      authStoreSource,
    )?.[1] ?? '';

  it('turns the app-level login expired mode into "modal"', () => {
    expect(preferencesSource).toContain("loginExpiredMode: 'modal'");
  });

  it('registers the reason interceptor before authenticateResponseInterceptor', () => {
    const defaultIndex = requestSource.indexOf('defaultResponseInterceptor(');
    const reasonIndex = requestSource.indexOf(
      'createSessionInvalidResponseInterceptor(',
    );
    const authenticateIndex = requestSource.indexOf(
      'authenticateResponseInterceptor({',
    );
    const errorIndex = requestSource.indexOf(
      'errorMessageResponseInterceptor(',
    );

    expect(defaultIndex).toBeGreaterThan(-1);
    expect(reasonIndex).toBeGreaterThan(defaultIndex);
    // axios response rejected 按注册顺序执行：先写原因，后重认证
    expect(reasonIndex).toBeLessThan(authenticateIndex);
    expect(authenticateIndex).toBeLessThan(errorIndex);
  });

  it('does not require isAccessChecked before opening the modal', () => {
    expect(doReAuthenticateBlock).not.toContain('isAccessChecked');
    expect(doReAuthenticateBlock).toContain('setLoginExpired(true)');
    expect(doReAuthenticateBlock).toContain('resolveReAuthAction(');
  });

  it('degrades to logout only for page mode or no layout mount point', () => {
    expect(doReAuthenticateBlock).toContain("action === 'modal'");
    // 'logout' 分支（page 模式 / 登录页 / 无挂载点）是默认落点
    expect(doReAuthenticateBlock).toContain('await authStore.logout()');
  });

  it('short-circuits re-auth while the user-initiated logout is in flight', () => {
    expect(doReAuthenticateBlock).toContain('isServerLogoutPending()');
  });

  it('marks public requests so their 401 never clears the session', () => {
    expect(requestSource).toContain('SESSION_INVALID_SKIP_FLAG');
    expect(requestSource).toContain('= !isToken');
  });

  it('skips the generic error toast for session-invalid 401', () => {
    expect(errorMessageBlock).toContain(
      'shouldSuppressSessionInvalidErrorToast(error)',
    );
  });

  it('skips the server logout call once the session is invalid', () => {
    expect(logoutBlock).toContain('sessionStore.invalid');
    expect(logoutBlock).toContain('!serverLogoutPending && !sessionInvalid');
  });

  it('resets the session store and stays on the current route after relogin', () => {
    expect(authStoreSource).toContain('handleSessionRestored');
    expect(authStoreSource).toContain('sessionStore.reset()');
    expect(authStoreSource).toContain("$t('sessionInvalid.reloginSuccess')");
  });

  it('mounts the app-level session invalid modal instead of the vben default one', () => {
    expect(basicLayoutSource).toContain('<SessionInvalidModal');
    expect(basicLayoutSource).not.toContain('AuthenticationLoginExpiredModal');
    expect(appSource).toContain('<SessionInvalidModal');
    expect(appSource).toContain('matched.length === 0');
  });

  it('keeps app-level preferences when the shared preference decorator is applied', () => {
    const decorated = defineOverridesPreferences({
      app: { loginExpiredMode: 'modal' } as never,
    });
    // @vben/preferences 的 defineOverridesPreferences 不能整体替换 app，
    // 否则 app 级的 loginExpiredMode 会被静默丢弃（运行时退回 'page'）
    expect(decorated.app?.loginExpiredMode).toBe('modal');
    expect(decorated.app?.accessMode).toBe('backend');
  });

  it('migrates cached preferences to modal mode at bootstrap', () => {
    expect(mainSource).toContain(
      "preferences.app.loginExpiredMode !== 'modal'",
    );
    expect(mainSource).toContain('updatePreferences');
  });

  it('broadcasts the first invalidation and the successful relogin to other tabs', () => {
    expect(interceptorSource).toContain('broadcastSessionInvalid');
    expect(authStoreSource).toContain('broadcastSessionRestored');
  });

  it('starts the multi-tab listener from the application root', () => {
    expect(appSource).toContain('startSessionSync');
    expect(appSource).toContain('stopSessionSync');
  });

  it('governs websocket reconnect and notification refresh on session invalid', () => {
    expect(messageStoreSource).toContain('sessionStore.invalid');
    expect(messageStoreSource).toContain('code === 401');
    // 锁屏边界在状态机入口统一处理（收到多标签广播的失效走同一入口）
    expect(sessionStoreSource).toContain('unlockScreen');
    // refreshNotifications 内部 catch，消除 unhandled rejection
    expect(messageStoreSource).toMatch(
      /async function refreshNotifications\(\) \{[\s\S]*?catch \(error\)/,
    );
  });
});
