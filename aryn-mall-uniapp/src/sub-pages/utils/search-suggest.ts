/**
 * 搜索联想（纯函数，便于单测）。
 *
 * 背景：后端目前**没有** suggestion 接口（检索能力见可行性分析 §4.2），
 * 因此这里先用「热搜词 + 用户历史」在本地做前缀/包含匹配兜底，
 * 等后端出词表后把 `suggestWords` 的入参换成接口返回即可，调用方不用改。
 *
 * 刻意不做的两件事：
 *   · 不做错别字纠错 —— 没有词表与拼音数据，硬做只会给出更差的联想；
 *   · 不做同义词扩展 —— 同上，属于后端语义能力（对标小象的语义引擎），
 *     前端拍脑袋映射会在运营改词后失效。
 */

/** 单个联想结果 */
export interface SuggestItem {
  word: string
  /** 来源：热度排行 / 用户历史，用于分组或标注 */
  from: 'history' | 'hot'
}

/** 最多返回多少条联想词 */
export const MAX_SUGGEST = 8

/**
 * 生成联想词。
 *
 * 匹配规则（按优先级）：
 *   1. 前缀命中（用户输入「苹」→「苹果」「苹果醋」）
 *   2. 包含命中（用户输入「果」→「红富士苹果」）
 *   历史词优先于热搜词 —— 用户搜过的词更贴近其意图。
 *
 * @param keyword 用户当前输入（空串返回空数组，由调用方展示默认面板）
 * @param historyWords 搜索历史（新→旧）
 * @param hotWords 热搜/推荐词
 */
export function buildSearchSuggest(
  keyword: string,
  historyWords: string[] = [],
  hotWords: string[] = [],
): SuggestItem[] {
  const query = (keyword || '').trim().toLowerCase()
  if (!query)
    return []

  const seen = new Set<string>()
  const result: SuggestItem[] = []

  const collect = (words: string[], from: SuggestItem['from']) => {
    const prefixHits: SuggestItem[] = []
    const containsHits: SuggestItem[] = []
    for (const raw of words) {
      const word = (raw || '').trim()
      if (!word)
        continue
      const lower = word.toLowerCase()
      if (lower === query)
        continue
      const key = `${from}:${lower}`
      if (seen.has(key) || seen.has(lower))
        continue
      if (lower.startsWith(query))
        prefixHits.push({ word, from })
      else if (lower.includes(query))
        containsHits.push({ word, from })
    }
    for (const item of [...prefixHits, ...containsHits]) {
      const lower = item.word.toLowerCase()
      if (seen.has(lower))
        continue
      seen.add(lower)
      seen.add(`${from}:${lower}`)
      result.push(item)
    }
  }

  // 历史优先：用户搜过的词比通用热搜更贴近其意图
  collect(historyWords, 'history')
  collect(hotWords, 'hot')

  return result.slice(0, MAX_SUGGEST)
}

/**
 * 联想词来源标签，用于给用户一点上下文（避免"这些词哪来的"）。
 */
export function suggestFromLabel(from: SuggestItem['from']): string {
  return from === 'history' ? '历史' : '热搜'
}
