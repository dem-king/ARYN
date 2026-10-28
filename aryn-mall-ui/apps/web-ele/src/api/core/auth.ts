import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password: string;
    username: string;
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
 * 手机号短信验证码登录
 *
 * <p>必须用表单方式提交：后端 `TobTokenController.smsLogin(String phone)` 从
 * request parameter 读手机号，`SmsValidateCodeFilter` 也从 request parameter 读
 * code 并与 Redis 中的 `sms_code_<phone>` 比对。用 JSON body 时 phone/code 都绑不到，
 * 登录会静默失败（历史上这个入口正因此不可用）；而缺了验证码校验则等于匿名登录。</p>
 */
export async function mobileLoginApi(phone: string, code: string) {
  return requestClient.post<AuthApi.LoginResult>(
    '/auth/token/sms/login',
    { phone, code },
    {
      headers: {
        isToken: false,
        isSwitchTenant: false,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    },
  );
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
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/auth/token/refresh',
    {
      withCredentials: true,
    },
  );
}

/**
 * 退出登录
 *
 * <p>必须走统一的 {@link requestClient}：其请求拦截器会注入 satoken、执行 boot/cloud
 * 双模式路径改写（boot 下 `/auth/token/logout` → `/boot/token/logout`）并补充
 * Accept-Language。此前使用裸 {@link baseRequestClient}，boot 模式请求 404、cloud 模式
 * 不带 token，服务端无法撤销会话，会出现「界面已退出、旧 token 仍有效」。</p>
 */
export async function logoutApi() {
  return requestClient.delete('/auth/token/logout', {
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
