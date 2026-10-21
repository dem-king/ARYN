import { requestClient } from '#/api/request';

/**
 * 发送短信验证码
 * @param type 短信业务类型（如 login、register 等）
 * @param phone 手机号
 * @param captchaVerification 图形验证码二次校验串（aj-captcha 的 captchaVerification），
 *   后端要求发送短信前必须完成图形验证；未完成验证时为空，此时不作为 query 参数发送。
 */
export async function sendCode(
  type: string,
  phone: string,
  captchaVerification?: string,
) {
  // 用 axios 的 params 传 query 参数，避免手工拼接 URL 时对校验串做二次编码。
  const params: { captchaVerification?: string } = {};
  if (captchaVerification) {
    params.captchaVerification = captchaVerification;
  }
  return requestClient.get(`/upms/sms/${type}/${phone}`, { params });
}
