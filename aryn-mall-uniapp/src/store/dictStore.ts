import { defineStore } from 'pinia'

export const useDictStore = defineStore('dict', {
  state: () => ({
    dict: [] as any[],
  }),
  actions: {
    // 获取字典
    getDict(_key: string) {
      if (_key == null && _key === '') {
        return null
      }
      try {
        for (let i = 0; i < this.dict.length; i++) {
          if (this.dict[i].key === _key) {
            return this.dict[i].value
          }
        }
      }
      catch (e) {
        console.log(e)
        return null
      }
    },
    // 设置字典
    setDict(_key: string, value: any) {
      if (_key !== null && _key !== '') {
        this.dict.push({
          key: _key,
          value,
        })
      }
    },
    // 删除字典
    removeDict(_key: string) {
      let bln = false
      try {
        for (let i = 0; i < this.dict.length; i++) {
          if (this.dict[i].key === _key) {
            this.dict.splice(i, 1)
            return true
          }
        }
      }
      catch (e) {
        console.log(e)
        bln = false
      }
      return bln
    },
    // 清空字典
    cleanDict() {
      this.dict = []
    },
  },
})
