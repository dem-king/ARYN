import { ref } from 'vue'

import { getPublishedByType } from '@/api/promotion/pageDesign'

/**
 * 模块级内存缓存：同一次应用生命周期内，同一 pageType 的装修 Schema 只请求一次。
 * key = pageType，value = pageContent（请求成功后写入；失败写入 null 避免重复请求）。
 */
const decorationCache = new Map<string, unknown>()

export interface UsePageDecorationReturn {
  /** 装修 Schema 数据，null 表示无装修内容 */
  pageContentData: ReturnType<typeof ref<unknown>>
  /** 是否正在加载（首次请求且无缓存时为 true） */
  loading: ReturnType<typeof ref<boolean>>
  /** 请求是否失败（失败后静默降级，不抛出） */
  error: ReturnType<typeof ref<boolean>>
  /** 触发加载：命中缓存则直接赋值，否则发请求 */
  fetch: () => Promise<void>
}

/**
 * 按 pageType 加载已发布装修页 Schema，带内存缓存与失败降级。
 *
 * 适用于「页面内嵌入装修区域」场景（商详页、分类页、个人中心页），
 * 与 useDecorationPage（整页 DIY，处理导航栏/分享/埋点）互补。
 *
 * @param pageType 页面类型：'1'=首页, '2'=商品详情页, '3'=分类页, '4'=个人中心页
 *
 * @example
 * ```ts
 * const { pageContentData, loading, fetch } = usePageDecoration('3')
 * onLoad(() => fetch())
 * // 模板：<DiyPage v-if="pageContentData" :page-content-data="pageContentData" />
 * //      <view v-else-if="loading" class="skeleton" />
 * ```
 */
export function usePageDecoration(pageType: string): UsePageDecorationReturn {
  const cached = decorationCache.get(pageType)
  const hasCache = decorationCache.has(pageType)

  const pageContentData = ref<unknown>(hasCache ? cached : null)
  const loading = ref(!hasCache)
  const error = ref(false)

  async function fetch(): Promise<void> {
    // 已有缓存（含 null 缓存，即上次请求过但无装修数据）则直接使用
    // 直接查 Map 而非闭包变量，确保同 pageType 的多组件实例共享缓存
    if (decorationCache.has(pageType)) {
      pageContentData.value = decorationCache.get(pageType) ?? null
      loading.value = false
      return
    }

    loading.value = true
    error.value = false
    try {
      const response = await getPublishedByType(pageType).send()
      const content = response?.pageContent ?? null
      decorationCache.set(pageType, content)
      pageContentData.value = content
    }
    catch (err) {
      console.warn(`[page-decoration] pageType=${pageType} 加载失败，降级为无装修:`, err)
      decorationCache.set(pageType, null)
      pageContentData.value = null
      error.value = true
    }
    finally {
      loading.value = false
    }
  }

  return {
    pageContentData,
    loading,
    error,
    fetch,
  }
}
