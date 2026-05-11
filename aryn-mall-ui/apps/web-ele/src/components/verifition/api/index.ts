import { requestClient } from '#/api/request';

export namespace VerifyApi {
  /** 接口参数 */
  export interface VerifyAParams {
    captchaType?: string;
    pointJson?: string;
    token?: string;
  }

  /** 接口返回值 */
  export interface VerifyResult {
    repCode: string;
    repData: any;
    repMsg: string;
    success: boolean;
  }
}
/**
 * 获取验证图片  以及token
 */
export async function reqGet(data: VerifyApi.VerifyAParams) {
  return requestClient.get<VerifyApi.VerifyResult>('/auth/code/get', {
    params: data,
  });
}
/**
 * 滑动或者点选验证
 */
export async function reqCheck(data: VerifyApi.VerifyAParams) {
  return requestClient.post<VerifyApi.VerifyResult>('/auth/code/check', data, {
    headers: {
      isToken: false,
      isSwitchTenant: false,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
}
