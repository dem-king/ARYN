/*
 * @Author: weisheng
 * @Date: 2025-06-23 22:23:05
 * @LastEditTime: 2025-06-24 19:03:21
 * @LastEditors: weisheng
 * @Description:
 * @FilePath: /aryn-uniapp-pro/src/store/persist.ts
 * 记得注释
 */
import type { PiniaPluginContext } from 'pinia'

const EXCLUDED_STORE_IDS = new Set([
  'temp',
  'shoppingCart',
  'shipContext',
  'global-loading',
  'global-toast',
  'global-message',
  // 租户能力是服务端事实（含 resolved 标记），持久化会让换租户后仍读到旧模式
  'tenantCapability',
  // 商城主题自带 storage 缓存（防闪色，同步首帧），且含 resolved 标记，不走通用持久化
  'mallTheme',
  // 版权配置同理：自带带租户后缀的 storage 缓存（首帧品牌/版权），且含 resolved 标记
  'copyright',
  // 语言偏好由 locales 模块用独立 key 持久化（i18n 初始化早于 pinia），避免双写
  'locale',
  // 会话失效标记只属于当前进程：持久化会导致冷启动/回前台弹出过期旧窗，
  // 且失效后本就不允许发请求，下一次真实 401 会重新触发
  'session',
])

/**
 * 只属于当前进程、不能跨启动恢复的运行时字段。
 *
 * 背景（2026-11-05 小程序客服排队滞留 / 客服回复收不到的根因）：通用持久化会把
 * Pinia 整棵 state 写入 uni storage，WebSocket 的 `socketState` 也被一并持久化。
 * 小程序重新编译或冷启动后会把它恢复成上一次的 `open`，而
 * `messageStore.connect()` 对 `open/connecting` 直接提前返回，真实 socket 从未
 * 创建 —— 界面显示「消息实时同步」，实际收不到任何推送。此类字段读取时丢弃、
 * 写入时剔除，避免跨进程伪造运行时状态。
 */
export const TRANSIENT_STATE_KEYS: Record<string, readonly string[]> = {
  message: ['socketState'],
  deliveryMessage: ['socketState'],
}

/** 剔除指定 store 的运行时字段，返回新对象，不修改入参。 */
export function stripTransientState<S extends Record<string, any>>(
  storeId: string,
  state: S,
): S {
  const keys = TRANSIENT_STATE_KEYS[storeId]
  if (!keys?.length || !state || typeof state !== 'object') {
    return state
  }
  const next: Record<string, any> = { ...state }
  keys.forEach((key) => {
    delete next[key]
  })
  return next as S
}

export function shouldPersistStore(storeId: string) {
  return !EXCLUDED_STORE_IDS.has(storeId)
}

function persist({ store }: PiniaPluginContext, excludedIds: string[]) {
  // 检查当前store的id是否在排除列表中
  const isExcluded = excludedIds.includes(store.$id)

  // 如果当前store的id在排除列表中，则不进行持久化
  if (isExcluded) {
    return
  }

  // 暂存 State（默认值：持久化状态缺字段时回退到运行时默认值）
  const defaultState = CommonUtil.deepClone(store.$state)
  let persistState = defaultState
  // 从缓存中读取
  const storageState = uni.getStorageSync(store.$id)
  if (storageState) {
    // 只有对象才按字段合并；字符串等历史脏值直接按默认值处理，避免被展开成下标键
    const storageObject
      = typeof storageState === 'object' ? storageState : null
    persistState = TRANSIENT_STATE_KEYS[store.$id]
      // 含运行时字段的 store：默认值 + 剔除运行时字段后的持久化状态合并，
      // 不能让 socketState 之类的旧值覆盖本次进程的真实状态。
      ? {
          ...defaultState,
          ...(storageObject
            ? stripTransientState(store.$id, storageObject)
            : {}),
        }
      : storageState
  }
  store.$state = persistState
  store.$subscribe(() => {
    // 在存储变化的时候将 store 缓存；运行时字段不落盘
    uni.setStorageSync(
      store.$id,
      CommonUtil.deepClone(stripTransientState(store.$id, store.$state)),
    )
  })
}

export function persistPlugin(context: PiniaPluginContext) {
  if (!shouldPersistStore(context.store.$id)) {
    return
  }
  persist(context, [])
}
