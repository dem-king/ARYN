/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import { LOGIN_PATH } from '@vben/constants';
import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import { isServerLogoutPending, useAuthStore, useSessionStore } from '#/store';

import { parseOpenBoot, rewriteBootUrl } from './boot-url';
import { refreshTokenApi } from './core';
import {
  createSessionInvalidResponseInterceptor,
  isBasicLayoutContext,
  resolveReAuthAction,
  SESSION_INVALID_SKIP_FLAG,
  shouldSuppressSessionInvalidErrorToast,
} from './session-invalid';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const openBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT);

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });

  /**
   * 重新认证逻辑
   *
   * <p>被顶下线 / 登录过期 / 被踢等会话失效由 `session-invalid.ts` 的解析拦截器
   * 先行落状态，这里只决定「弹窗原地重登」还是「降级登出」：
   * - modal 模式：不再依赖 `accessStore.isAccessChecked`，刷新页面后首个 401 也要弹窗；
   * - 仅当前就在登录页 / 没有 BasicLayout 挂载点时才降级 `logout()`；
   * - 免登请求（`isToken: false`）的 401 不清态、只提示（保持既有语义）。</p>
   */
  async function doReAuthenticate() {
    console.warn('Access token or refresh token is invalid or expired. ');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    const sessionStore = useSessionStore();
    // 用户主动退出流程中的 401：本地清理由 logout() 收尾，不再弹窗也不再递归登出。
    if (isServerLogoutPending()) {
      return;
    }
    // 延迟加载路由：request 模块被 store/api 反向依赖，静态引入 router 会形成
    // 模块初始化环（router -> guard -> store -> api -> request）。
    const { router } = await import('#/router');
    const currentRoute = router.currentRoute.value;
    const action = resolveReAuthAction({
      inBasicLayout: isBasicLayoutContext(currentRoute),
      loginExpiredMode: preferences.app.loginExpiredMode,
      onLoginPage: currentRoute.path === LOGIN_PATH,
      sessionInvalid: sessionStore.invalid,
    });
    if (action === 'ignore') {
      return;
    }
    accessStore.setAccessToken(null);
    if (action === 'modal') {
      accessStore.setLoginExpired(true);
      return;
    }
    await authStore.logout();
  }

  /**
   * 刷新token逻辑
   */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const resp = await refreshTokenApi();
    const newToken = resp.data;
    accessStore.setAccessToken(newToken);
    return newToken;
  }

  function formatToken(token: null | string) {
    return token ? `${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();
      // switchTenantId
      const isToken = config.headers.isToken !== false;
      const isSwitchTenant = config.headers.isSwitchTenant === false;
      delete config.headers.isToken;
      delete config.headers.isSwitchTenant;
      // 免登请求 401 不算会话失效（避免白名单漏配时把用户踢下线）
      (config as unknown as Record<string, unknown>)[
        SESSION_INVALID_SKIP_FLAG
      ] = !isToken;
      const switchTenantId = localStorage.getItem('switch-tenant-id');
      if (switchTenantId && !isSwitchTenant) {
        config.headers['tenant-id'] = switchTenantId;
      }
      if (isToken) {
        config.headers.satoken = formatToken(accessStore.accessToken);
      } else {
        delete config.headers.satoken;
      }
      config.headers['Accept-Language'] = preferences.app.locale;
      config.url = rewriteBootUrl(config.url, openBoot);
      return config;
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  // 会话失效原因解析：必须注册在 authenticateResponseInterceptor 之前。
  // axios 的 response rejected 按注册顺序执行，先解析 reason/device 写入 session store，
  // 后一步重认证打开弹窗时才能拿到原因；本拦截器只写状态、继续抛错，不吞错误。
  client.addResponseInterceptor(createSessionInvalidResponseInterceptor());

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 会话失效（401）已经由不可关闭的弹窗解释，不再弹红色 toast：
      // 否则会出现「弹窗 + 红 toast + 服务端文案」三连提示。
      if (shouldSuppressSessionInvalidErrorToast(error)) {
        return;
      }
      // 这里可以根据业务进行定制,你可以拿到 error 内的信息进行定制化处理，根据不同的 code 做不同的提示，而不是直接使用 message.error 提示 msg
      // 当前mock接口返回的错误字段是 error 或者 message
      const responseData = error?.response?.data ?? {};
      const errorMessage = responseData?.msg ?? '';
      // 坐席心跳是 30 秒一次的后台轮询，失败由状态 store 自行恢复（重写租约或回落离线），
      // 不能每次都弹全局错误提示。
      const failedUrl: string =
        error?.config?.url ?? error?.response?.config?.url ?? '';
      if (failedUrl.endsWith('/staff/agent/heartbeat')) {
        console.warn('客服心跳续约失败，等待下一轮恢复。', error);
        return;
      }
      // 如果没有错误信息，则会根据状态码进行提示
      ElMessage.error(errorMessage || msg);
    }),
  );

  return client;
}

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });
