import { defineStore } from 'pinia'

/**
 * 字典 Store
 *
 * 改进点：
 * 1. 使用 Record<string, any> 替代数组，O(1) 查找
 * 2. 修复空值检查逻辑（原 && 应为 ||）
 * 3. 规范化代码风格
 */
export const useDictStore = defineStore('dict', {
  state: () => ({
    dict: {} as Record<string, any>,
  }),
  actions: {
    /** 获取字典 */
    getDict(key: string) {
      if (!key) {
        return null
      }
      return this.dict[key] ?? null
    },

    /** 设置字典 */
    setDict(key: string, value: any) {
      if (key) {
        this.dict[key] = value
      }
    },

    /** 删除字典 */
    removeDict(key: string) {
      if (key && key in this.dict) {
        delete this.dict[key]
        return true
      }
      return false
    },

    /** 清空字典 */
    cleanDict() {
      this.dict = {}
    },
  },
})
