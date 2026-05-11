/**
 * 搜索历史 composables
 * 提供搜索历史相关的响应式状态和方法
 */
import { computed } from 'vue'
import { useSearchHistoryStore } from '@/store/searchHistoryStore'

export function useSearchHistory() {
  const searchHistoryStore = useSearchHistoryStore()

  /**
   * 添加搜索历史
   * @param keyword 搜索关键词
   */
  const addSearchHistory = (keyword: string) => {
    searchHistoryStore.addHistory(keyword)
  }

  /**
   * 删除指定搜索历史
   * @param keyword 要删除的关键词
   */
  const removeSearchHistory = (keyword: string) => {
    searchHistoryStore.removeHistory(keyword)
  }

  /**
   * 清空所有搜索历史
   */
  const clearSearchHistory = () => {
    searchHistoryStore.clearHistory()
  }

  /**
   * 设置最大历史记录数
   * @param count 最大历史记录数
   */
  const setMaxHistoryCount = (count: number) => {
    searchHistoryStore.setMaxHistoryCount(count)
  }

  return {
    // 状态
    historyList: computed(() => searchHistoryStore.getHistoryList),
    hasHistory: computed(() => searchHistoryStore.hasHistory),
    historyCount: computed(() => searchHistoryStore.getHistoryCount),

    // 方法
    addSearchHistory,
    removeSearchHistory,
    clearSearchHistory,
    setMaxHistoryCount,
  }
}
