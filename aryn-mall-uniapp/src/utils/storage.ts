/**
 * 本地存储工具
 *
 * 改进点：
 * 1. 统一命名空间前缀，避免与其他应用冲突
 * 2. 支持 JSON 序列化/反序列化
 * 3. 支持过期时间机制
 */

/** 存储命名空间前缀 */
const NAMESPACE = 'aryn_'

interface StorageData<T> {
  value: T
  expire?: number // 过期时间戳（毫秒），0 或不设置表示永久
}

export const Local = {
  /**
   * 设置存储
   * @param key 存储 key（自动添加命名空间前缀）
   * @param val 存储值
   * @param expire 过期时间（毫秒），不传表示永久
   */
  set<T>(key: string, val: T, expire?: number) {
    const data: StorageData<T> = {
      value: val,
      expire: expire ? Date.now() + expire : undefined,
    }
    uni.setStorageSync(NAMESPACE + key, data)
  },

  /**
   * 获取存储值
   * @param key 存储 key
   * @param defaultValue 默认值（key 不存在或已过期时返回）
   */
  get<T = any>(key: string, defaultValue?: T): T | undefined {
    const data = uni.getStorageSync(NAMESPACE + key) as StorageData<T> | undefined
    if (!data) {
      return defaultValue
    }
    // 检查是否过期
    if (data.expire && Date.now() > data.expire) {
      uni.removeStorageSync(NAMESPACE + key)
      return defaultValue
    }
    return data.value
  },

  /**
   * 移除存储
   */
  remove(key: string) {
    uni.removeStorageSync(NAMESPACE + key)
  },

  /**
   * 清除所有带命名空间的存储
   */
  clear() {
    const info = uni.getStorageInfoSync()
    info.keys.forEach((key) => {
      if (key.startsWith(NAMESPACE)) {
        uni.removeStorageSync(key)
      }
    })
  },
}
