import type { DecorationLink } from '../schema/types'

export interface NoticeContentItem {
  content?: unknown
  link?: unknown
  type?: unknown
  url?: unknown
}

function hasLegacyUrl(item: NoticeContentItem) {
  return (
    (item.type === 1 || item.type === '1')
    && typeof item.url === 'string'
    && item.url.length > 0
  )
}

export function hasNoticeLink(contentList: readonly NoticeContentItem[]) {
  return contentList.some(item => Boolean(item?.link) || hasLegacyUrl(item))
}

export function resolveNoticeLink(
  item?: NoticeContentItem | null,
): DecorationLink | string | null {
  if (!item) return null
  if (typeof item.link === 'string' && item.link) return item.link
  if (item.link && typeof item.link === 'object')
    return item.link as DecorationLink
  if (hasLegacyUrl(item)) return item.url as string
  return null
}
