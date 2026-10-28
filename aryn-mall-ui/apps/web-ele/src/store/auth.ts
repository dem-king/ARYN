import type { Recordable, UserInfo } from '@vben/types';

import type { AuthApi } from '#/api/core/auth';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { getUserInfoApi, loginApi, logoutApi, mobileLoginApi } from '#/api';
import { sendCode } from '#/api/upms/sms';
import { $t } from '#/locales';

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
// authenticateResponseInterceptor 的 doReAuthenticate → authStore.logout()；
// 若旧 token 已失效，新的退出请求仍返回 401，不设标记会形成
// logout → 401 → logout 的无限递归。重入时跳过接口请求、只做本地清理。
let serverLogoutPending = false;

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

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
          accessStore.setLoginExpired(false);
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );
        }

        if (userInfo?.realName) {
          ElNotification({
            message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            title: $t('authentication.loginSuccess'),
            type: 'success',
          });
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
          accessStore.setLoginExpired(false);
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );
        }

        if (userInfo?.realName) {
          ElNotification({
            message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            title: $t('authentication.loginSuccess'),
            type: 'success',
          });
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
    try {
      if (!serverLogoutPending) {
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
      // 退出时删除选中租户ID
      localStorage.removeItem('switch-tenant-id');
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
