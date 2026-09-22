import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('../../..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 商城配送链路可走通性守门。
 *
 * 历史缺陷：只有第三方快递（way=1）的下单路径会把订单推进到「待收货」，
 * 商城配送（way=3）与公司内部配送（way=4）的订单永远停留在「待发货」，
 * 且 C 端「确认收货」按钮只对 way=1 展示，客户无路可走、订单无法完成。
 */
describe('order receipt contracts', () => {
  const orderOperation = source('src/sub-pages/order/components/order-operation/index.vue')

  it('shows confirm-receipt for express, mall delivery and company internal delivery', () => {
    // 必须是 way=1/3/4 三种可收货方式，不能只放行快递
    expect(orderOperation).toMatch(/showReceiver[\s\S]{0,240}\['1', '3', '4'\]/)
    // 回归守门：确认收货不得再被 way=1 单值锁死
    expect(orderOperation).not.toMatch(/showReceiver[\s\S]{0,160}deliveryWay === '1'/)
  })

  it('keeps the pickup QR code exclusive to self-pickup', () => {
    expect(orderOperation).toMatch(/showQRCodeBtn[\s\S]{0,160}deliveryWay === '2'/)
  })

  it('renders a vessel delivery card for internal delivery orders', () => {
    const detail = source('src/sub-pages/order/order-detail/index.vue')
    expect(detail).toContain("state.order.deliveryWay === '4'")
    expect(detail).toContain('state.order.vesselName')
    expect(detail).toContain('state.order.portName')
  })

  it('shows the delivery timeline for task-driven deliveries', () => {
    const detail = source('src/sub-pages/order/order-detail/index.vue')
    expect(detail).toMatch(/DeliveryProgress/)
    expect(detail).toMatch(/['"]4['"]/)
  })
})
