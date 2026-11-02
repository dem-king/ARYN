/**
 * 登录设备（活跃会话）API（三期契约 §2.1/§2.2）。
 *
 * 路径首段 `auth` 是 cloud 模式下的微服务域，boot 模式由请求层
 * `instance.ts` 的 `rewriteBootUrl` 自动改写成 `/boot/session/devices`；
 * 页面/组件禁止自己拼 `/boot`（双模式适配规则）。
 *
 * 设备 `id` 是服务端对 token 做 SHA-256 后的前 12 位（不可反推、不可重放），
 * 前端的职责只有原样回传，**不得**拼 token 或其它身份信息。
 */
import { alovaInstance } from '@/api/core/instance'

/** `GET /auth/session/devices` 的单条活跃会话。 */
export interface LoginSessionDevice {
  /** token SHA-256 前 12 位；下线接口用它定位会话 */
  id: string
  /** TOB / TOC（服务端下发，前端仅透传展示） */
  deviceType?: string
  browser?: string
  os?: string
  location?: string
  loginTime?: string
  current: boolean
}

/** 当前账号的活跃会话（当前设备在首位，其余按登录时间倒序——服务端已排序，前端防御性再排）。 */
export function getMyLoginDevices() {
  return alovaInstance.Get<LoginSessionDevice[]>('/auth/session/devices')
}

/** 下线指定会话；服务端禁止下线当前会话（当前设备请走退出登录）。 */
export function removeLoginDevice(id: string) {
  return alovaInstance.Delete<void>(`/auth/session/devices/${encodeURIComponent(id)}`)
}

/**
 * 归一化响应（页面/单测共用）：
 *  - 非数组/null → []；
 *  - 缺 `id` 的脏数据丢弃（id 是下线的唯一凭据，缺了渲染出来也没法用）；
 *  - `current` 统一成布尔（后端可能给 true/false，也可能缺省）。
 */
export function normalizeLoginDevices(value: unknown): LoginSessionDevice[] {
  if (!Array.isArray(value))
    return []
  return value
    .filter((device): device is LoginSessionDevice => {
      const record = device as Partial<LoginSessionDevice> | null
      return !!record && typeof record.id === 'string' && record.id.length > 0
    })
    .map(device => ({ ...device, current: device.current === true }))
}
