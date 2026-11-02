import { describe, expect, it } from 'vitest'

import { resolveNoticePrefix } from './notice-prefix'

describe('公告前缀解析', () => {
  it('图片前缀需要同时配了 titleType=1 与图片地址', () => {
    expect(
      resolveNoticePrefix({ titleType: '1', titleUrl: 'https://example.com/n.png' }),
    ).toBe('image')
    expect(resolveNoticePrefix({ titleType: '1', titleUrl: '   ' })).toBe('icon')
  })

  it('文字前缀需要同时配了 titleType=2 与非空文案', () => {
    expect(resolveNoticePrefix({ titleText: '公告', titleType: '2' })).toBe('text')
    // 存量脏数据：切到文字前缀后把文案清空，原样渲染会留一条空白前缀
    expect(resolveNoticePrefix({ titleText: '', titleType: '2' })).toBe('icon')
    expect(resolveNoticePrefix({ titleText: '   ', titleType: '2' })).toBe('icon')
  })

  it('未配置或未知取值回落铃铛图标', () => {
    expect(resolveNoticePrefix({})).toBe('icon')
    expect(resolveNoticePrefix({ titleType: '', titleText: '公告' })).toBe('icon')
    expect(resolveNoticePrefix({ titleType: '9', titleUrl: 'x', titleText: 'x' })).toBe('icon')
    expect(resolveNoticePrefix({ titleType: null, titleText: null, titleUrl: null })).toBe('icon')
  })

  it('数值型 titleType 与字符串同口径（旧数据可能是数字）', () => {
    expect(resolveNoticePrefix({ titleText: '公告', titleType: 2 })).toBe('text')
    expect(resolveNoticePrefix({ titleType: 1, titleUrl: '/static/a.png' })).toBe('image')
  })
})
