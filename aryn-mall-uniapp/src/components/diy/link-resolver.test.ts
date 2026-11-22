import { afterEach, describe, expect, it, vi } from 'vitest'

import { createDecorationLinkAction, followDecorationLink, normalizeDecorationLink } from './link-resolver'

const { auth } = vi.hoisted(() => ({ auth: { isLoggedIn: false } }))

vi.mock('@/store/authStore', () => ({ useAuthStore: () => auth }))

describe('decoration link resolver', () => {
  it.each([
    [
      { params: {}, path: '', targetId: 'goods-1', type: 'goods' },
      '/sub-pages/product/goods-detail/index?id=goods-1',
    ],
    [
      { params: {}, path: '', targetId: 'category-1', type: 'category' },
      '/sub-pages/product/goods-list/index?categoryId=category-1',
    ],
    [
      { params: {}, path: '', targetId: 'page-1', type: 'page' },
      '/sub-pages/promotion/diy-page/index?id=page-1',
    ],
    [
      { params: {}, path: '', targetId: 'activity-1', type: 'activity' },
      '/sub-pages/promotion/group-buy/group-buy-detail/index?id=activity-1',
    ],
    [
      {
        params: { keyword: 'summer sale' },
        path: '/sub-pages/product/goods-search/index',
        type: 'custom',
      },
      '/sub-pages/product/goods-search/index?keyword=summer%20sale',
    ],
  ] as const)('creates a navigation action for %o', (link, url) => {
    expect(createDecorationLinkAction(link)).toEqual({ kind: 'navigate', url })
  })

  it('creates a mini-program action with structured parameters', () => {
    expect(
      createDecorationLinkAction({
        params: { appId: 'wx123', envVersion: 'trial' },
        path: '/pages/index?source=mall',
        type: 'mini-program',
      }),
    ).toEqual({
      appId: 'wx123',
      envVersion: 'trial',
      kind: 'mini-program',
      path: '/pages/index?source=mall',
    })
  })

  it('falls back to release for an unsupported mini-program environment', () => {
    expect(
      createDecorationLinkAction({
        params: { appId: 'wx123', envVersion: 'preview' },
        path: '/pages/index',
        type: 'mini-program',
      }),
    ).toMatchObject({ envVersion: 'release', kind: 'mini-program' })
  })

  it('uses the safe station-message route for customer service', () => {
    expect(
      createDecorationLinkAction({
        params: {},
        path: '',
        type: 'customer-service',
      }),
    ).toEqual({ kind: 'navigate', url: '/sub-pages/message/chat/index' })
  })

  it.each([
    '/sub-pages/product/goods-detail/index?id=goods-1',
    {
      name: '商品详情',
      url: '/sub-pages/product/goods-detail/index?id=goods-1',
    },
  ])('normalizes a legacy link before resolving it', (link) => {
    expect(normalizeDecorationLink(link)).toEqual({
      params: {},
      path: '/sub-pages/product/goods-detail/index?id=goods-1',
      type: 'custom',
    })
  })

  it.each([
    [
      '/pages/product/goods-detail/index?id=goods-1',
      '/sub-pages/product/goods-detail/index?id=goods-1',
    ],
    [
      { url: '/pages/shop/diy-page/index?id=page-1' },
      '/sub-pages/promotion/diy-page/index?id=page-1',
    ],
  ])('migrates the real legacy route in %o', (link, path) => {
    expect(normalizeDecorationLink(link)).toMatchObject({ path })
  })

  it('migrates a legacy route after the admin saved it as a custom link', () => {
    expect(
      normalizeDecorationLink({
        params: {},
        path: '/pages/shop/diy-page/index?id=page-1',
        type: 'custom',
      }),
    ).toMatchObject({ path: '/sub-pages/promotion/diy-page/index?id=page-1' })
  })

  it.each(['https://example.com/redirect', '//example.com/redirect', 'javascript:alert(1)'])(
    'rejects unsafe custom path %s',
    (path) => {
      expect(createDecorationLinkAction({ params: {}, path, type: 'custom' })).toEqual({
        kind: 'none',
      })
    },
  )
})

describe('decoration link navigation', () => {
  const shipSupplyPath = '/sub-pages/product/ship-supply/index'

  afterEach(() => {
    auth.isLoggedIn = false
    vi.unstubAllGlobals()
  })

  function mockNavigation() {
    const navigateTo = vi.fn((options: { url: string, complete?: () => void }) => {
      options.complete?.()
    })
    const setStorageSync = vi.fn()
    vi.stubGlobal('getCurrentPages', () => [{ route: 'pages/home/index' }])
    vi.stubGlobal('uni', { navigateTo, setStorageSync })
    return { navigateTo, setStorageSync }
  }

  it.each([
    [{ name: '船供专区', url: shipSupplyPath }, shipSupplyPath],
    [
      { type: 'custom', path: shipSupplyPath, params: { scene: '2', sharedCartId: 'cart-1' } },
      `${shipSupplyPath}?scene=2&sharedCartId=cart-1`,
    ],
  ] as const)('opens login before visiting the ship supply page and preserves the destination', (link, destination) => {
    const { navigateTo, setStorageSync } = mockNavigation()

    followDecorationLink(link)

    expect(navigateTo.mock.calls.map(([options]) => options.url)).toEqual(['/pages/login/index'])
    expect(setStorageSync).toHaveBeenCalledWith('redirectPath', destination)
  })

  it('opens the ship supply page directly for a signed-in shopper', () => {
    const { navigateTo, setStorageSync } = mockNavigation()
    auth.isLoggedIn = true
    const destination = `${shipSupplyPath}?scene=2&sharedCartId=cart-1`

    followDecorationLink(destination)

    expect(navigateTo.mock.calls).toEqual([[{ url: destination }]])
    expect(setStorageSync).not.toHaveBeenCalled()
  })

  it('keeps public product links accessible for guests', () => {
    const { navigateTo, setStorageSync } = mockNavigation()
    const destination = '/sub-pages/product/goods-detail/index?id=goods-1'

    followDecorationLink(destination)

    expect(navigateTo.mock.calls).toEqual([[{ url: destination }]])
    expect(setStorageSync).not.toHaveBeenCalled()
  })
})
