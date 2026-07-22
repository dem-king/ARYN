import { describe, expect, it } from 'vitest'
import { isUnauthorizedResponse, parseApiResponse } from './handlers'

describe('api response parsing', () => {
  it('detects a business 401 from a JSON string response', () => {
    const response = parseApiResponse('{"code":401,"msg":"expired"}')

    expect(response).toMatchObject({ code: 401, msg: 'expired' })
    expect(isUnauthorizedResponse(200, response)).toBe(true)
  })

  it('preserves non-JSON gateway responses instead of throwing SyntaxError', () => {
    expect(parseApiResponse('<html>Bad Gateway</html>')).toEqual({
      data: '<html>Bad Gateway</html>',
    })
  })
})
