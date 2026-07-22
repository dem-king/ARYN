import { describe, expect, it } from 'vitest'
import { requireTokenValue } from './auth-token'

describe('authentication token validation', () => {
  it('returns a non-empty token from a login response', () => {
    expect(requireTokenValue({ tokenValue: ' token-1 ' })).toBe('token-1')
  })

  it('rejects responses without a usable token', () => {
    expect(() => requireTokenValue({})).toThrow('登录响应缺少有效凭证')
    expect(() => requireTokenValue({ tokenValue: '   ' })).toThrow(
      '登录响应缺少有效凭证',
    )
  })
})
