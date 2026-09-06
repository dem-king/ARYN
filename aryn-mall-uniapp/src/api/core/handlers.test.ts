import { describe, expect, it } from 'vitest'
import { isDeliveryRequest, isUnauthorizedResponse, parseApiResponse } from './handlers'

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

  it('recognizes delivery requests for isolated session expiry handling', () => {
    expect(isDeliveryRequest({ url: '/boot/app/delivery/task/1/arrive' } as any)).toBe(true)
    expect(isDeliveryRequest({ url: '/boot/file/staff/delivery-evidence/upload' } as any)).toBe(true)
    expect(isDeliveryRequest({ url: '/boot/app/order/1/detail' } as any)).toBe(false)
  })
})
