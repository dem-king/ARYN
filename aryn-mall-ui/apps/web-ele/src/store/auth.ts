import type { Recordable, UserInfo } from '@vben/types';

import type { AuthApi } from '#/api/core/auth';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { ElMessage, ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { getUserInfoApi, loginApi, logoutApi, mobileLoginApi } from '#/api';
import { sendCode } from '#/api/upms/sms';
import { $t } from '#/locales';

import { getServiceDeskRole, notifyServiceDeskLogout } from './service-window';
import { useSessionStore } from './session';
import { broadcastSessionRestored } from './session-sync';

/**
 * 从请求异常中提取可安全记录的文本。
 *
 * <p>只读取响应体 `msg` 或 `Error.message`，禁止直接打印异常对象：axios 的
 * `error.config.headers.satoken` 是可直接重放的凭证，登录类请求的
 * `error.config.data` 还可能是含明文密码的表单。</p>
 */
function getSafeErrorMessage(error: unknown): string {
  const candidate = (error ?? {}) as {
    message?: unknown;
    response?: { data?: { msg?: unknown } };
  };
  const responseMessage = candidate.response?.data?.msg;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage.trim();
  }
  const message = candidate.message;
  return typeof message === 'string' && message.trim()
    ? message.trim()
    : '未知错误';
}

// 退出请求是否在途。退出改走 requestClient 后，401 响应会触发
// authenticateResponseInterceptor 的 doReAuthenticate；若旧 token 已失效，
// 新的退出请求仍返回 401，不设标记会形成 logout → 401 → logout 的无限递归。
// 重入时跳过接口请求、只做本地清理。
let serverLogoutPending = false;

/** 是否有用户主动发起的退出请求在途（请求层据此短路 401 重认证）。 */
export function isServerLogoutPending() {
  return serverLogoutPending;
}

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const sessionStore = useSessionStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 会话失效弹窗内的原地重登成功后的收尾。
   *
   * <p>不跳转、保留当前页面现场（表单数据还在），只清失效态 + 关弹窗 +
   * 提示用户重试刚才因 401 失败的操作（失败的请求不会自动重放，避免重复提交）。</p>
   *
   * <p>例外：刷新页面后首个 401 时初始导航会被守卫中断、BasicLayout 从未挂载，
   * 此时当前路由仍未解析（`matched` 为空），原地无法恢复页面，需要整页重载——
   * 新 token 已持久化，重载后守卫生效、回到用户原来访问的页面。</p>
   */
  async function handleSessionRestored() {
    // 二期多标签页：reset 之前广播，让其它标签页整页重载（token 已持久化）
    broadcastSessionRestored();
    // 关闭弹窗 + 重置会话失效状态机
    accessStore.setLoginExpired(false);
    sessionStore.reset();

    if (router.currentRoute.value.matched.length === 0) {
      window.location.reload();
      return;
    }

    ElMessage.success($t('sessionInvalid.reloginSuccess'));
  }

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: AuthApi.LoginParams,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    const userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const { tokenValue } = await loginApi({ ...params });

      // 如果成功获取到 accessToken
      if (tokenValue) {
        // 将 accessToken 存储到 accessStore 中
        accessStore.setAccessToken(tokenValue);

        // 获取用户信息并存储到 accessStore 中
        const userInfo = await fetchUserInfo();

        userStore.setUserInfo(userInfo);

        if (accessStore.loginExpired) {
          // 会话失效弹窗内的原地重登：关闭弹窗、保留当前路由，不跳转；
          // 恢复提示已由 handleSessionRestored 用轻量 ElMessage 给出，不再叠加登录成功通知
          await handleSessionRestored();
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );

          if (userInfo?.realName) {
            ElNotification({
              message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
              title: $t('authentication.loginSuccess'),
              type: 'success',
            });
          }
        }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }
  async function authMobileLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    const userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const { tokenValue } = await mobileLoginApi(params.phone, params.code);
      // 如果成功获取到 accessToken
      if (tokenValue) {
        // 将 accessToken 存储到 accessStore 中
        accessStore.setAccessToken(tokenValue);

        // 获取用户信息并存储到 accessStore 中
        const userInfo = await fetchUserInfo();

        userStore.setUserInfo(userInfo);

        if (accessStore.loginExpired) {
          // 会话失效弹窗内的原地重登：关闭弹窗、保留当前路由，不跳转；
          // 恢复提示已由 handleSessionRestored 用轻量 ElMessage 给出，不再叠加登录成功通知
          await handleSessionRestored();
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );

          if (userInfo?.realName) {
            ElNotification({
              message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
              title: $t('authentication.loginSuccess'),
              type: 'success',
            });
          }
        }
      }
    } finally {
      loginLoading.value = false;
    }
    return {
      userInfo,
    };
  }
  async function sendSmsCode(
    type: string,
    phone: string,
    captchaVerification?: string,
  ) {
    await sendCode(type, phone, captchaVerification);
  }
  async function logout(redirect: boolean = true) {
    // 会话已判定失效（被顶下线/过期/被踢）时跳过服务端退出：
    // 带失效 token 打 /auth/token/logout 只会再吃一个 401，并再次触发会话失效分流。
    const sessionInvalid = sessionStore.invalid;
    try {
      if (!serverLogoutPending && !sessionInvalid) {
        serverLogoutPending = true;
        try {
          await logoutApi();
        } catch (error) {
          // 脱敏告警：服务端退出失败不阻塞本地登出，但要把原因记下来。
          // 只记录 msg/message 文本，禁止记录 error 对象（含 satoken 请求头）。
          console.warn(
            '[auth] 服务端退出登录失败，已改为仅清理本地登录态：',
            getSafeErrorMessage(error),
          );
        } finally {
          serverLogoutPending = false;
        }
      }
    } finally {
      // 独立窗口复用同一 token：主窗口登出后它继续留着只会不断吃 401，
      // 主动联动关闭；独立窗口自己登出时直接关窗（手动打开无法自关则回登录页）。
      if (getServiceDeskRole() === 'desk') {
        try {
          window.close();
        } catch {
          // 忽略：后续 router.replace 仍会回登录页
        }
      } else {
        notifyServiceDeskLogout();
      }
      // 退出时删除选中租户ID
      localStorage.removeItem('switch-tenant-id');
      sessionStore.reset();
      resetAllStores();
      accessStore.setLoginExpired(false);

      // 回登录页带上当前路由地址
      await router.replace({
        path: LOGIN_PATH,
        query: redirect
          ? {
              redirect: encodeURIComponent(router.currentRoute.value.fullPath),
            }
          : {},
      });
    }
  }

  async function fetchUserInfo() {
    let userInfo: null | UserInfo = null;
    userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    // 设置按钮权限
    accessStore.setAccessCodes(userInfo.permissions);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    authMobileLogin,
    fetchUserInfo,
    loginLoading,
    sendSmsCode,
    logout,
  };
});
