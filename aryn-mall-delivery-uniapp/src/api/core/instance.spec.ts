import { describe, expect, it } from 'vitest'
import {
  buildDeliveryApiUrl,
  buildDeliveryWebSocketUrl,
  encodeDeliveryForm,
  normalizeDeliveryApiBaseUrl,
} from './instance'

describe('配送端 API 地址', () => {
  it('规范化配置中的空白和末尾斜杠', () => {
    expect(normalizeDeliveryApiBaseUrl(' http://localhost:9999/ ')).toBe('http://localhost:9999')
  })

  it('Cloud 模式生成 Gateway 完整地址', () => {
    expect(buildDeliveryApiUrl('/auth/token/login', 'http://localhost:9999/', false))
      .toBe('http://localhost:9999/auth/token/login')
  })

  it('Boot 模式改写业务前缀', () => {
    expect(buildDeliveryApiUrl('/auth/token/login', 'http://localhost:9999', true))
      .toBe('http://localhost:9999/boot/token/login')
    expect(buildDeliveryApiUrl('/mall-order/delivery/staff/tasks', 'http://localhost:9999', true))
      .toBe('http://localhost:9999/boot/delivery/staff/tasks')
  })

  it('WebSocket 使用同一基地址并切换协议', () => {
    expect(buildDeliveryWebSocketUrl('/message/ws/staff', 'https://api.example.com', false))
      .toBe('wss://api.example.com/message/ws/staff')
  })

  it('表单请求编码验证码登录参数', () => {
    expect(encodeDeliveryForm({
      code: 'token+/=',
      password: 'p&ss',
      username: 'staff name',
    })).toBe('code=token%2B%2F%3D&password=p%26ss&username=staff%20name')
  })
})
