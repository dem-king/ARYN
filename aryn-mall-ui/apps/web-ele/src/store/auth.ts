import type { Recordable, UserInfo } from '@vben/types';

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
import { encrypt } from '#/utils/aes';

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
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    const userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      params.password = encrypt(
        params.password,
        import.meta.env.VITE_PWD_PRIVICE_KEY,
      );
      const { tokenValue } = await loginApi(params);

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
  async function sendSmsCode(type: string, phone: string) {
    await sendCode(type, phone);
  }
  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
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
