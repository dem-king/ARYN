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
    expect(detail).toContain('state.order.deliveryWay === \'4\'')
    expect(detail).toContain('state.order.vesselName')
    expect(detail).toContain('state.order.portName')
  })

  it('shows the delivery timeline for task-driven deliveries', () => {
    const detail = source('src/sub-pages/order/order-detail/index.vue')
    expect(detail).toMatch(/DeliveryProgress/)
    expect(detail).toMatch(/['"]4['"]/)
  })

  it('maps order status 5 to refunding and 11 to canceled in the detail title', () => {
    const detail = source('src/sub-pages/order/order-detail/index.vue')
    const titleSwitch = detail.match(/const navbarTitle[\s\S]*?\n\}\)/)?.[0] ?? ''
    expect(titleSwitch).not.toBe('')
    // 后端 OrderStatusEnum: 5=退款中, 11=已取消；历史上 5 被错标为「订单已取消」
    expect(titleSwitch).toMatch(/case '5':\s*return '退款中'/)
    expect(titleSwitch).toMatch(/case '11':\s*return '订单已取消'/)
    expect(titleSwitch).not.toMatch(/case '5':\s*return '订单已取消'/)
  })

  it('gates confirm-receipt on delivered for task-driven delivery ways', () => {
    // 司机未点「已送达」前，商城配送/内部配送不得展示确认收货按钮
    // （订单在司机出发时就进入待收货，缺此条件会出现"未送达却能收货"）
    expect(orderOperation).toMatch(/showReceiver[\s\S]{0,600}delivered === true/)
    // 快递（way=1）无内部妥投信号，必须继续放行，不能被 delivered 条件误伤
    expect(orderOperation).toMatch(/deliveryWay === '1'[\s\S]{0,80}return true/)
  })
})
