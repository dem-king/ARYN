import { requestClient } from '#/api/request';

/**
 * 发送短信验证码
 */
export async function sendCode(type: string, phone: string) {
  return requestClient.get(`/upms/sms/${type}/${phone}`);
}
