import { describe, expect, it } from 'vitest'
import {
  isStatusTipImageUrl,
  resolveStatusTipImage,
  resolveStatusTipImageUrl,
  resolveStatusTipUrlPrefix,
} from './status-tip'

describe('resolveStatusTipUrlPrefix', () => {
  it('cloud 模式保留微服务域前缀', () => {
    expect(resolveStatusTipUrlPrefix(false, 'https://api.example.com'))
      .toBe('https://api.example.com/upms/status-tip/')
  })

  it('boot 模式改写为 /boot 前缀', () => {
    expect(resolveStatusTipUrlPrefix(true, 'https://api.example.com'))
      .toBe('https://api.example.com/boot/status-tip/')
  })

  it('相对 base（H5 开发代理）保持相对路径', () => {
    expect(resolveStatusTipUrlPrefix(true, '/api')).toBe('/api/boot/status-tip/')
  })

  it('空 base 返回纯路径', () => {
    expect(resolveStatusTipUrlPrefix(false, '')).toBe('/upms/status-tip/')
  })

  it('前缀始终以 / 结尾，保证 urlPrefix + image + .png 拼接正确', () => {
    expect(resolveStatusTipUrlPrefix(true, '').endsWith('/')).toBe(true)
    expect(resolveStatusTipUrlPrefix(false, 'http://h').endsWith('/')).toBe(true)
  })
})

describe('resolveStatusTipImage', () => {
  const prefix = 'https://api.example.com/upms/status-tip/'

  it('未配置时回落内置静态图', () => {
    expect(resolveStatusTipImage('message', {}, prefix))
      .toBe('https://api.example.com/upms/status-tip/message.png')
  })

  it('三张会场插画未配置时回落随包 PNG，而不是后端 status-tip 目录', () => {
    expect(resolveStatusTipImage('empty-cart', {}, prefix))
      .toBe('/static/promotion/empty/empty-cart.png')
    expect(resolveStatusTipImage('empty-order', {}, prefix))
      .toBe('/static/promotion/empty/empty-order.png')
    expect(resolveStatusTipImage('empty-seckill', {}, prefix))
      .toBe('/static/promotion/empty/empty-seckill.png')
  })

  it('会场插画配置了合法地址时优先使用', () => {
    expect(resolveStatusTipImage('empty-seckill', { 'empty-seckill': 'https://cdn.example.com/seckill.png' }, prefix))
      .toBe('https://cdn.example.com/seckill.png')
  })

  it('会场插画配置了非法协议时回落随包 PNG', () => {
    expect(resolveStatusTipImage('empty-cart', { 'empty-cart': 'javascript:alert(1)' }, prefix))
      .toBe('/static/promotion/empty/empty-cart.png')
  })

  it('空串 / 纯空白视为未配置', () => {
    expect(resolveStatusTipImage('search', { search: '' }, prefix)).toBe(`${prefix}search.png`)
    expect(resolveStatusTipImage('search', { search: '   ' }, prefix)).toBe(`${prefix}search.png`)
  })

  it('配置了 http/https 地址时优先使用', () => {
    expect(resolveStatusTipImage('network', { network: 'https://cdn.example.com/a.png' }, prefix))
      .toBe('https://cdn.example.com/a.png')
    expect(resolveStatusTipImage('network', { network: 'http://cdn.example.com/a.png' }, prefix))
      .toBe('http://cdn.example.com/a.png')
  })

  it('非法协议视为未配置，避免配置入口被注入 javascript:/data:', () => {
    expect(resolveStatusTipImage('halo', { halo: 'javascript:alert(1)' }, prefix)).toBe(`${prefix}halo.png`)
    expect(resolveStatusTipImage('halo', { halo: 'data:image/png;base64,xxx' }, prefix)).toBe(`${prefix}halo.png`)
  })

  it('其它预设位的配置不会串位', () => {
    expect(resolveStatusTipImage('comment', { message: 'https://cdn.example.com/m.png' }, prefix))
      .toBe(`${prefix}comment.png`)
  })
})

describe('isStatusTipImageUrl', () => {
  it('只接受 http/https 与站内相对路径', () => {
    expect(isStatusTipImageUrl('https://a/b.png')).toBe(true)
    expect(isStatusTipImageUrl('/upms/file/local/1/a.png')).toBe(true)
    expect(isStatusTipImageUrl('ftp://a/b.png')).toBe(false)
    expect(isStatusTipImageUrl('javascript:alert(1)')).toBe(false)
    expect(isStatusTipImageUrl('')).toBe(false)
  })
})

describe('resolveStatusTipImageUrl', () => {
  it('公网 URL 原样返回', () => {
    expect(resolveStatusTipImageUrl('https://cdn.example.com/a.png', 'https://api.example.com', false))
      .toBe('https://cdn.example.com/a.png')
  })

  it('站内相对路径补后端域名（cloud）', () => {
    expect(resolveStatusTipImageUrl('/upms/file/local/t/a.png', 'https://api.example.com', false))
      .toBe('https://api.example.com/upms/file/local/t/a.png')
  })

  it('站内相对路径在 boot 模式改写为 /boot 并补域名', () => {
    expect(resolveStatusTipImageUrl('/upms/file/local/t/a.png', 'https://api.example.com', true))
      .toBe('https://api.example.com/boot/file/local/t/a.png')
  })

  it('已是 /boot 路径时不再重复改写', () => {
    expect(resolveStatusTipImageUrl('/boot/file/local/t/a.png', 'https://api.example.com', true))
      .toBe('https://api.example.com/boot/file/local/t/a.png')
  })
})
