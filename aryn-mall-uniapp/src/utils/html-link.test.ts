import { describe, expect, it, vi } from 'vitest'
import { classifyHtmlLink, handleHtmlLinkTap } from './html-link'

/**
 * 外链兜底口径的守门测试。
 *
 * 小程序端 mp-html 只对站内路径做 `uni.navigateTo`：含 `://` 的外链只在
 * App 分支里打开，小程序点了毫无反应。所以除站内路径外必须由我们复制到
 * 剪贴板；反过来，站内路径绝不能被我们接管，否则 mp-html 的跳转会被复制
 * 行为顶掉。
 */

describe('classifyHtmlLink', () => {
  it('站内绝对路径交给 mp-html', () => {
    expect(classifyHtmlLink('/pages/home/index')).toBe('in-app')
    expect(classifyHtmlLink('/sub-pages/order/order-list/index?id=1')).toBe('in-app')
  })

  it('相对路径同样交给 mp-html', () => {
    expect(classifyHtmlLink('./pages/order/index')).toBe('in-app')
    expect(classifyHtmlLink('../goods/index')).toBe('in-app')
  })

  it('http/https 外链需要兜底', () => {
    expect(classifyHtmlLink('https://example.com/rule')).toBe('external')
    expect(classifyHtmlLink('HTTP://example.com')).toBe('external')
  })

  it('mailto / tel 不带 ://，mp-html 会当成站内路径失败，同样走兜底', () => {
    expect(classifyHtmlLink('mailto:service@example.com')).toBe('external')
    expect(classifyHtmlLink('tel:4008001234')).toBe('external')
  })

  it('锚点与空值不处理', () => {
    expect(classifyHtmlLink('#section-1')).toBe('anchor')
    expect(classifyHtmlLink('   ')).toBe('none')
    expect(classifyHtmlLink(undefined)).toBe('none')
    expect(classifyHtmlLink(null)).toBe('none')
  })
})

describe('handleHtmlLinkTap', () => {
  it('外链复制到剪贴板', () => {
    const setClipboardData = vi.fn()
    vi.stubGlobal('uni', { setClipboardData })

    handleHtmlLinkTap(' https://example.com/rule ')

    expect(setClipboardData).toHaveBeenCalledWith(
      expect.objectContaining({ data: 'https://example.com/rule' }),
    )
    vi.unstubAllGlobals()
  })

  it('站内路径不接管，避免顶掉 mp-html 自己的跳转', () => {
    const setClipboardData = vi.fn()
    vi.stubGlobal('uni', { setClipboardData })

    handleHtmlLinkTap('/pages/home/index')

    expect(setClipboardData).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
