import { alovaInstance } from '@/api/core/instance'

// 获取默认收获地址
export function getDefault() {
  return alovaInstance.Get<any>('/mall-user/app/address/default')
}

// 分页查询收货地址
export function getPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/address/page', {
    params,
  })
}

// 新增/编辑用户收货地址
export function saveOrUpdateAddress(data: object) {
  return alovaInstance.Post<any>('/mall-user/app/address', data)
}

//  根据id查询用户收货地址
export function getById(id: string) {
  return alovaInstance.Get<any>(`/mall-user/app/address/${id}`)
}

// 删除收货地址
export function delAddress(id: string) {
  return alovaInstance.Delete<any>(`/mall-user/app/address/${id}`)
}
