import { describe, expect, it } from 'vitest'
import { parseOpenBoot, rewriteBootUrl } from './boot-url'

describe('boot URL rewriting', () => {
  it('removes the service prefix and keeps the URL suffix', () => {
    expect(rewriteBootUrl('/auth/toc-token/login?source=wx', true)).toBe(
      '/boot/toc-token/login?source=wx',
    )
  })

  it('routes delivery APIs in both deployment modes', () => {
    const cloudPath = '/mall-order/app/delivery/task/page'
    expect(rewriteBootUrl(cloudPath, false)).toBe(cloudPath)
    expect(rewriteBootUrl(cloudPath, true)).toBe('/boot/app/delivery/task/page')
  })

  it('routes the quick-cart API in both deployment modes', () => {
    const cloudPath = '/product/app/goodsspu/quick-cart/spu-1'
    expect(rewriteBootUrl(cloudPath, false)).toBe(cloudPath)
    expect(rewriteBootUrl(cloudPath, true)).toBe(
      '/boot/app/goodsspu/quick-cart/spu-1',
    )
  })

  it('routes the replenish batch-add and summary APIs in both deployment modes', () => {
    // 新增补给单接口的 boot/cloud 双模式输出必须显式锁定：
    // cloud 走网关的 /mall-order 域，boot 去掉首段加 /boot 前缀。
    const batchAdd = '/mall-order/app/shopping-cart/batch'
    expect(rewriteBootUrl(batchAdd, false)).toBe(batchAdd)
    expect(rewriteBootUrl(batchAdd, true)).toBe('/boot/app/shopping-cart/batch')

    const activeSummary = '/mall-order/app/shared-cart/active-summary'
    expect(rewriteBootUrl(activeSummary, false)).toBe(activeSummary)
    expect(rewriteBootUrl(activeSummary, true)).toBe(
      '/boot/app/shared-cart/active-summary',
    )
  })

  it('does not rewrite disabled, absolute, or already rewritten URLs', () => {
    expect(rewriteBootUrl('/auth/toc-token/login', false)).toBe(
      '/auth/toc-token/login',
    )
    expect(rewriteBootUrl('/boot/toc-token/login', true)).toBe(
      '/boot/toc-token/login',
    )
    expect(rewriteBootUrl('https://api.example.com/auth/login', true)).toBe(
      'https://api.example.com/auth/login',
    )
  })

  it('parses missing and case-insensitive environment flags safely', () => {
    expect(parseOpenBoot('TRUE')).toBe(true)
    expect(parseOpenBoot(undefined)).toBe(false)
  })
})
