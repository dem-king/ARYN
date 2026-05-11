/**
 * 搜索历史状态管理
 * 管理商品搜索历史记录
 */
import { defineStore } from 'pinia'

interface SearchHistoryState {
  historyList: string[]
  maxHistoryCount: number
}

export const useSearchHistoryStore = defineStore('search-history', {
  state: (): SearchHistoryState => ({
    historyList: [],
    maxHistoryCount: 10, // 最大历史记录数
  }),

  getters: {
    /**
     * 获取搜索历史列表
     */
    getHistoryList: state => state.historyList,

    /**
     * 获取历史记录数量
     */
    getHistoryCount: state => state.historyList.length,

    /**
     * 判断是否有搜索历史
     */
    hasHistory: state => state.historyList.length > 0,
  },

  actions: {
    /**
     * 添加搜索历史
     * @param keyword 搜索关键词
     */
    addHistory(keyword: string) {
      if (!keyword)
        return

      // 去掉前后空格
      keyword = keyword.trim()

      // 判空（防止只输入空格）
      if (!keyword)
        return
      // 过滤重复搜索
      this.historyList = this.historyList.filter(item => item !== keyword)

      // 数组头部插入
      this.historyList.unshift(keyword)

      // 限制最大历史记录数
      if (this.historyList.length > this.maxHistoryCount) {
        this.historyList = this.historyList.slice(0, this.maxHistoryCount)
      }
    },

    /**
     * 删除指定搜索历史
     * @param keyword 要删除的关键词
     */
    removeHistory(keyword: string) {
      this.historyList = this.historyList.filter(item => item !== keyword)
    },

    /**
     * 清空所有搜索历史
     */
    clearHistory() {
      this.historyList = []
    },

    /**
     * 设置最大历史记录数
     * @param count 最大历史记录数
     */
    setMaxHistoryCount(count: number) {
      this.maxHistoryCount = count
      // 如果当前历史记录超过限制，截取前面的记录
      if (this.historyList.length > this.maxHistoryCount) {
        this.historyList = this.historyList.slice(0, this.maxHistoryCount)
      }
    },
  },
})
