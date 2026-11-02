/**
 * 登录设备页的数据逻辑（列表加载 / 下线动作）。
 *
 * 抽出来的原因：页面单测跑在 node 环境（没有小程序渲染器），把「加载、当前设备
 * 不可下线、下线后刷新」这些决策放在 composable 里，可以用 mock API 直接断言；
 * 页面只负责渲染与 toast。错误提示由页面统一走 `useGlobalToast`（composable
 * 不依赖 Pinia，保持可测性）。
 */
import type { LoginSessionDevice } from '@/api/session-devices'
import { computed, ref } from 'vue'
import { getMyLoginDevices, normalizeLoginDevices, removeLoginDevice } from '@/api/session-devices'
import { canRemoveLoginDevice, resolveLoginDevicesView } from './presentation'

/** 下线结果：removed=成功；blocked-current=当前设备/脏数据不可下线；failed=请求失败 */
export type LoginDevicesRemoveResult = 'removed' | 'blocked-current' | 'failed'

export function useLoginDevices() {
  const devices = ref<LoginSessionDevice[]>([])
  /** 请求进行中（首次加载显示骨架，刷新时保留旧列表不闪） */
  const loading = ref(false)
  /** 至少成功加载过一次 */
  const loaded = ref(false)
  /** 最近一次加载失败的原始 message（页面据此提示 + 展示重试态） */
  const loadError = ref('')
  /** 正在下线的设备 id（按钮 loading / 防连点） */
  const removingId = ref('')

  const view = computed(() => resolveLoginDevicesView(devices.value))

  /** 拉取活跃会话；失败返回 false（页面 toast + 重试态），不向外抛错避免 unhandled。 */
  async function load(): Promise<boolean> {
    loading.value = true
    loadError.value = ''
    try {
      const response = await getMyLoginDevices().send()
      devices.value = normalizeLoginDevices(response)
      loaded.value = true
      return true
    }
    catch (error) {
      loadError.value = (error as { message?: string } | null)?.message ?? ''
      return false
    }
    finally {
      loading.value = false
    }
  }

  /**
   * 下线指定会话。
   *
   * - 只允许下线非当前设备（当前设备走底部「退出登录」，与服务端规则一致）；
   * - 成功后重新拉列表，以服务端结果为准（不本地删行，避免与实际会话不一致）；
   * - 同一时间只允许一个下线请求（防连点导致重复 DELETE）。
   */
  async function remove(id: string): Promise<LoginDevicesRemoveResult> {
    const target = devices.value.find(device => device.id === id)
    if (!canRemoveLoginDevice(target))
      return 'blocked-current'
    if (removingId.value)
      return 'failed'
    removingId.value = id
    try {
      await removeLoginDevice(id).send()
      await load()
      return 'removed'
    }
    catch {
      return 'failed'
    }
    finally {
      removingId.value = ''
    }
  }

  return {
    devices,
    view,
    loading,
    loaded,
    loadError,
    removingId,
    load,
    remove,
  }
}
