import { describe, expect, it } from 'vitest'
import { createDecorationLinkAction } from './link-resolver'

describe('customer service decoration link', () => {
  it('uses the safe station-message route', () => {
    expect(
      createDecorationLinkAction({
        params: {},
        path: '',
        type: 'customer-service',
      }),
    ).toEqual({
      kind: 'navigate',
      url: '/sub-pages/message/chat/index',
    })
  })
})
