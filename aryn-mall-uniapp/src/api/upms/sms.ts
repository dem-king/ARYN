import { alovaInstance } from '@/api/core/instance'

/**
 * 发送短信验证码。
 *
 * 注意：必须携带登录态（不要 skipToken）。
 * 该接口只在「换绑手机号」流程使用（type=update），后端要求已登录才允许发送，
 * 否则会拒绝；接口路径本身在免登白名单里，不需要额外配置。
 */
export function sendSmsCode(type: string, phone: string) {
  return alovaInstance.Get<any>(`/upms/sms/${type}/${phone}`)
}
