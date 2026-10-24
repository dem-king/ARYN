import { describe, expect, it } from 'vitest'

import { hasNoticeLink, resolveNoticeLink } from './notice-link'

const goodsLink = {
  params: {},
  path: '',
  targetId: '9',
  type: 'goods',
}

describe('公告链接解析', () => {
  it('优先使用装修链接对象', () => {
    expect(resolveNoticeLink({ content: '公告', link: goodsLink })).toBe(
      goodsLink,
    )
    expect(hasNoticeLink([{ content: '公告', link: goodsLink }])).toBe(true)
  })

  it('兼容旧数据 type=1 + url', () => {
    expect(
      resolveNoticeLink({
        content: '公告',
        type: 1,
        url: 'https://example.com/notice',
      }),
    ).toBe('https://example.com/notice')
    expect(
      hasNoticeLink([{ content: '公告', type: '1', url: '/pages/index/index' }]),
    ).toBe(true)
  })

  it('无链接时不跳转也不显示箭头', () => {
    expect(resolveNoticeLink({ content: '公告' })).toBeNull()
    expect(hasNoticeLink([{ content: '公告' }])).toBe(false)
  })
})
