import { describe, expect, it } from 'vitest'
import { shouldPersistStore } from './persist'

describe('store persistence exclusions', () => {
  it.each([
    'temp',
    'shoppingCart',
    'global-loading',
    'global-toast',
    'global-message',
  ])('does not persist transient store %s', (storeId: string) => {
    expect(shouldPersistStore(storeId)).toBe(false)
  })

  it('continues to persist authentication state', () => {
    expect(shouldPersistStore('auth')).toBe(true)
  })
})
