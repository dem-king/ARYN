/**
 * 登录设备页的展示逻辑（纯函数，单测直接驱动）。
 *
 * 数据来自 `GET /auth/session/devices`；「当前设备不可下线」是页面硬规则
 * （三期契约 §2.2：服务端也会拒绝，这里提前不渲染按钮），所以把判断收在
 * `canRemoveLoginDevice` 一处，模板与点击处理共用，避免两处各写一遍布尔。
 */
import type { LoginSessionDevice } from '@/api/session-devices'
import { parseLocalDateTimeTimestamp } from '@/utils/datetime'

export interface LoginDevicesView {
  /** 当前设备（服务端标记 current=true）；列表里没有则 null */
  current: LoginSessionDevice | null
  /** 其他会话：按登录时间倒序（服务端已排序，这里防御性再排一次） */
  others: LoginSessionDevice[]
  /** 列表整体为空（加载成功但没有数据） */
  isEmpty: boolean
}

/** 登录时间排序键：兼容 ISO（带时区）与后端 `yyyy-MM-dd HH:mm:ss` 两种形态。 */
export function loginTimeTimestamp(value?: string): number {
  if (!value)
    return 0
  const local = parseLocalDateTimeTimestamp(value)
  if (local != null)
    return local
  const parsed = new Date(value).getTime()
  return Number.isFinite(parsed) ? parsed : 0
}

export function resolveLoginDevicesView(
  devices?: LoginSessionDevice[] | null,
): LoginDevicesView {
  const list = Array.isArray(devices) ? devices : []
  const current = list.find(device => device.current) ?? null
  const others = list
    .filter(device => !device.current)
    .slice()
    .sort((left, right) => loginTimeTimestamp(right.loginTime) - loginTimeTimestamp(left.loginTime))
  return { current, others, isEmpty: list.length === 0 }
}

/**
 * 是否允许「下线」：当前设备不可下线（服务端也会拒绝，当前设备请用底部「退出登录」）；
 * 缺 id 的脏数据一律不给下线入口。
 */
export function canRemoveLoginDevice(device?: LoginSessionDevice | null): boolean {
  return !!device && !device.current && !!device.id
}

/** 设备系统/浏览器：`Windows 10 · Chrome 120`，空字段省略；都为空返回 ''（由页面兜底文案）。 */
export function resolveDeviceSystemText(device?: LoginSessionDevice | null): string {
  return [device?.os, device?.browser]
    .map(value => (typeof value === 'string' ? value.trim() : ''))
    .filter(Boolean)
    .join(' · ')
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

/** 登录时间：完整 `YYYY-MM-DD HH:mm`；解析失败返回 ''（整行隐藏，不显示占位）。 */
export function resolveDeviceLoginTimeText(device?: LoginSessionDevice | null): string {
  const value = device?.loginTime
  if (typeof value !== 'string' || !value.trim())
    return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime()))
    return ''
  const clock = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${clock}`
}
