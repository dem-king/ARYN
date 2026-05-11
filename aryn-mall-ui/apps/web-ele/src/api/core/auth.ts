import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
    code?: string;
    randomStr: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    tokenValue: string;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}
/**
 * 手机号登录
 */
export async function mobileLoginApi(phone: string, code: string) {
  return requestClient.post<AuthApi.LoginResult>('/auth/token/sms/login', {
    phone,
    code,
  });
}
/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/token/login', data, {
    headers: {
      isToken: false,
      isSwitchTenant: false,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>('/auth/token/refresh', {
    withCredentials: true,
  });
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.delete('/token/logout', {
    withCredentials: true,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取用户菜单
 */
export async function getAccessCodesApi() {
  return requestClient.get<any>('/upms/menu');
}
