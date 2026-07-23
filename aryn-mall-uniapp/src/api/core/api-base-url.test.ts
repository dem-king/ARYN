import { describe, expect, it } from 'vitest'
import {
  buildApiUrl,
  buildWebSocketUrl,
  selectApiBaseUrl,
} from './api-base-url'

describe('api base URL resolution', () => {
  it('uses the H5 development proxy without changing native builds', () => {
    expect(selectApiBaseUrl('http://localhost:9999', '/api/', true)).toBe(
      '/api',
    )
    expect(selectApiBaseUrl('http://localhost:9999/', '/api', false)).toBe(
      'http://localhost:9999',
    )
  })

  it('joins API paths with exactly one slash', () => {
    expect(buildApiUrl('/promotion/app/pagedesign', '/api/')).toBe(
      '/api/promotion/app/pagedesign',
    )
    expect(buildApiUrl('upms/file/app/upload', 'http://localhost:9999')).toBe(
      'http://localhost:9999/upms/file/app/upload',
    )
  })

  it('creates WebSocket URLs for relative H5 and absolute native bases', () => {
    expect(
      buildWebSocketUrl('/message/ws/app', 'http://localhost:8888', '/api'),
    ).toBe('ws://localhost:8888/api/message/ws/app')
    expect(
      buildWebSocketUrl(
        '/message/ws/app',
        undefined,
        'https://api.example.com',
      ),
    ).toBe('wss://api.example.com/message/ws/app')
  })
})
