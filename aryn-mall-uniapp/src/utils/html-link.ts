/**
 * mp-html 富文本里 `<a>` 被点击时的跳转口径。
 *
 * mp-html 自带的跳转只覆盖站内路径：含 `://` 的外链只在它的 App 分支
 * （`plus.runtime.openWeb`）里生效，小程序分支直接落空；`mailto:` / `tel:`
 * 不带 `://`，会被当成站内路径交给 `uni.navigateTo`，同样静默失败。
 * 因此这里只放行站内路径交给 mp-html 自己处理，其余协议统一复制到剪贴板——
 * 小程序不允许任意域名跳转，复制给用户自行打开是唯一通用的兜底。
 */

import { translate } from '@/locales'

export type HtmlLinkKind = 'anchor' | 'external' | 'in-app' | 'none'

const ABSOLUTE_SCHEME = /^[a-z][\w+.-]*:/i
const IN_APP_PATH = /^\.{0,2}\//

/** 判断 `<a href>` 的去向，供组件决定是否接管点击 */
export function classifyHtmlLink(href?: null | string): HtmlLinkKind {
  const value = href?.trim() ?? ''
  if (!value)
    return 'none'
  if (value.startsWith('#'))
    return 'anchor'
  if (IN_APP_PATH.test(value))
    return 'in-app'
  if (ABSOLUTE_SCHEME.test(value))
    return 'external'
  // 无前缀的相对串（如 `pages/home/index`）：仍然交给 mp-html 尝试
  return 'in-app'
}

/**
 * mp-html `linktap` 的处理：只接管外链，站内路径与锚点原样放给 mp-html。
 *
 * 不额外弹成功提示——`setClipboardData` 自带系统级「内容已复制」。
 */
export function handleHtmlLinkTap(href?: null | string): void {
  const value = href?.trim() ?? ''
  if (classifyHtmlLink(value) !== 'external')
    return
  // H5 是浏览器环境，外链直接开新页；用运行时判断而不是只靠条件编译，
  // 单测环境（node）下才不会碰到 window。
  // #ifdef H5
  if (typeof window !== 'undefined') {
    window.open(value, '_blank', 'noopener,noreferrer')
    return
  }
  // #endif
  uni.setClipboardData({
    data: value,
    fail: () =>
      uni.showToast({
        title: translate('diy.customHtml.linkCopyFailed'),
        icon: 'none',
      }),
  })
}
