import { alovaInstance } from '@/api/core/instance'
// 发送短信验证码
export function sendSmsCode(type: string, phone: string) {
  return alovaInstance.Get<any>(`/upms/sms/${type}/${phone}`, {
    headers: {
      skipToken: true,
    },
  })
}
