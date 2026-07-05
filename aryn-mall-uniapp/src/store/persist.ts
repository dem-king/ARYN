/*
 * Pinia 持久化插件
 *
 * 安全改进：
 * 1. 过滤不应持久化的临时字段（isLoading, _pendingPromise, userInfoPromise 等）
 * 2. 对敏感字段（token）进行简单编码，增加窃取成本
 * 3. 注意：前端加密密钥必然可被提取，真正的安全应依赖后端 httpOnly cookie
 */
import type { PiniaPluginContext } from 'pinia'

/** 不应持久化的临时字段 */
const TRANSIENT_FIELDS = [
  'isLoading',
  '_pendingPromise',
  'userInfoPromise',
  '_loading',
]

/** 需要编码的敏感字段 */
const SENSITIVE_FIELDS = ['token', 'tokenValue', 'accessToken']

/**
 * 简单的 Base64 编码（增加窃取成本，非真正加密）
 */
function encode(value: string): string {
  try {
    // #ifdef H5
    return btoa(unescape(encodeURIComponent(value)))
    // #endif
    // #ifndef H5
    return value // 小程序/App 端不支持 btoa，直接返回
    // #endif
  } catch {
    return value
  }
}

/**
 * 简单的 Base64 解码
 */
function decode(value: string): string {
  try {
    // #ifdef H5
    return decodeURIComponent(escape(atob(value)))
    // #endif
    // #ifndef H5
    return value
    // #endif
  } catch {
    return value
  }
}

/**
 * 过滤掉不应持久化的临时字段
 */
function filterTransientFields(state: Record<string, any>): Record<string, any> {
  const filtered: Record<string, any> = {}
  for (const key of Object.keys(state)) {
    if (!TRANSIENT_FIELDS.includes(key)) {
      filtered[key] = state[key]
    }
  }
  return filtered
}

/**
 * 编码敏感字段
 */
function encodeSensitiveFields(state: Record<string, any>): Record<string, any> {
  const encoded: Record<string, any> = { ...state }
  for (const field of SENSITIVE_FIELDS) {
    if (encoded[field] && typeof encoded[field] === 'string') {
      encoded[field] = encode(encoded[field])
    }
  }
  return encoded
}

/**
 * 解码敏感字段
 */
function decodeSensitiveFields(state: Record<string, any>): Record<string, any> {
  const decoded: Record<string, any> = { ...state }
  for (const field of SENSITIVE_FIELDS) {
    if (decoded[field] && typeof decoded[field] === 'string') {
      decoded[field] = decode(decoded[field])
    }
  }
  return decoded
}

function persist({ store }: PiniaPluginContext, excludedIds: string[]) {
  // 检查当前store的id是否在排除列表中
  const isExcluded = excludedIds.includes(store.$id)

  // 如果当前store的id在排除列表中，则不进行持久化
  if (isExcluded) {
    return
  }

  // 暂存State（过滤临时字段）
  let persistState = CommonUtil.deepClone(filterTransientFields(store.$state))
  // 从缓存中读取
  const storageState = uni.getStorageSync(store.$id)
  if (storageState) {
    // 解码敏感字段后恢复
    persistState = decodeSensitiveFields(storageState)
  }
  store.$state = persistState
  store.$subscribe(() => {
    // 在存储变化的时候将store缓存（过滤临时字段 + 编码敏感字段）
    const stateToStore = encodeSensitiveFields(
      CommonUtil.deepClone(filterTransientFields(store.$state)),
    )
    uni.setStorageSync(store.$id, stateToStore)
  })
}

export function persistPlugin(context: PiniaPluginContext) {
  // 调用persist函数，并传入排除列表
  // 'temp' - 临时数据不持久化
  // 'shoppingCart' - 购物车数据不持久化（包含 _pendingPromise 等临时字段）
  persist(context, ['temp', 'shoppingCart'])
}
