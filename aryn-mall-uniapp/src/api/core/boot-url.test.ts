import { describe, expect, it } from 'vitest'
import { parseOpenBoot, rewriteBootUrl } from './boot-url'

describe('boot URL rewriting', () => {
  it('removes the service prefix and keeps the URL suffix', () => {
    expect(rewriteBootUrl('/auth/toc-token/login?source=wx', true)).toBe(
      '/boot/toc-token/login?source=wx',
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
