import { alovaInstance } from '@/api/core/instance'

// 修改信息
export function editObj(data: object) {
  return alovaInstance.Post<any>('/mall-user/app/userinfo/update/info', data)
}
// 修改手机号
export function editPhone(data: object) {
  return alovaInstance.Post<any>('/mall-user/app/userinfo/update/phone', data)
}
// 修改密码
export function editPassword(data: object) {
  return alovaInstance.Post<any>('/mall-user/app/userinfo/update/password', data)
}
// 根据邀请码获取推荐人信息
export function getInviterInfo(referralCode: string) {
  return alovaInstance.Get<any>(`/mall-user/app/userinfo/inviter/${referralCode}`)
}
