import { alovaInstance } from '@/api/core/instance'

export function getList(type: string) {
  return alovaInstance.Get<any>(`/upms/dictvalue/type/${type}`)
}
