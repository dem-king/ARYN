/**
 * 公告前缀（左侧锚点）解析。
 *
 * 装修配置里 `titleType` 有三种取值：`'1'` 图片、`'2'` 文字、其余（空值）默认铃铛图标。
 * 存量页面里存在「`titleType='2'` 但 `titleText` 为空」的组合（运营把前缀从图标切成
 * 文字后又清空了文案，或复制了别的组件配置），原样渲染会留下一条空白前缀：公告条
 * 左侧缺一个锚点，整条公告看起来像背景缺了一块。
 *
 * 这里把三种配置统一收敛成「能渲染的那一种」：配置不足以渲染时回落铃铛图标，
 * 保证任何脏数据组合下公告条都有可见的前缀。
 */
export type NoticePrefixKind = 'icon' | 'image' | 'text'

export interface NoticePrefixConfig {
  titleText?: unknown
  titleType?: unknown
  titleUrl?: unknown
}

export function resolveNoticePrefix(config: NoticePrefixConfig): NoticePrefixKind {
  const titleType = config.titleType == null ? '' : String(config.titleType).trim()
  const titleUrl = typeof config.titleUrl === 'string' ? config.titleUrl.trim() : ''
  const titleText = typeof config.titleText === 'string' ? config.titleText.trim() : ''
  if (titleType === '1' && titleUrl)
    return 'image'
  if (titleType === '2' && titleText)
    return 'text'
  return 'icon'
}
