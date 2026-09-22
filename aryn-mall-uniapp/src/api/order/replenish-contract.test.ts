import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 补给单（批量加购 + 摘要）前后端契约守门。
 *
 * 典型漏网方式：前端路径与后端 @RequestMapping 漂移、或 boot 模式下首段
 * 未按微服务域改写。两者都不会被类型检查发现。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const ORDER_BIZ = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order'

describe('replenish routing contract', () => {
  it('batch add path matches the backend mapping', () => {
    const api = source('src/api/order/shoppingCart.ts')
    expect(api).toContain('/mall-order/app/shopping-cart/batch')

    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppShoppingCartController.java`)
    expect(controller).toContain('@RequestMapping("/app/shopping-cart")')
    expect(controller).toContain('@PostMapping("/batch")')
  })

  it('active summary path matches the backend mapping', () => {
    const api = source('src/api/order/sharedCart.ts')
    // 用正则避免在普通字符串里写模板占位符（no-template-curly-in-string）
    expect(api).toMatch(/\$\{BASE\}\/active-summary/)

    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('@RequestMapping("/app/shared-cart")')
    expect(controller).toContain('@GetMapping("/active-summary")')
  })

  it('keeps the microservice-first segment so boot rewrite stays correct', () => {
    // boot 模式下 rewriteBootUrl 会去掉首段并加 /boot 前缀；
    // 若写成 /app/... （缺 mall-order 域）则 cloud 模式网关无此路由。
    const cartApi = source('src/api/order/shoppingCart.ts')
    const sharedApi = source('src/api/order/sharedCart.ts')
    expect(cartApi).toContain('\'/mall-order/app/shopping-cart/batch\'')
    expect(sharedApi).toContain('\'/mall-order/app/shared-cart\'')
    expect(cartApi).not.toContain('\'/boot/')
    expect(sharedApi).not.toContain('\'/boot/')
  })

  it('does not hand-roll VITE_OPEN_BOOT parsing', () => {
    const cartApi = source('src/api/order/shoppingCart.ts')
    const sharedApi = source('src/api/order/sharedCart.ts')
    for (const api of [cartApi, sharedApi]) {
      expect(api).not.toContain('VITE_OPEN_BOOT')
      expect(api).not.toContain('JSON.parse')
    }
  })
})

describe('replenish semantics contract', () => {
  it('batch add is not wrapped in a transaction (partial-success semantics)', () => {
    // 若给 batchAdd 加 @Transactional，任一项失败会回滚整批，
    // 用户无法把清单里可买的商品先加进购物车。
    const impl = repoSource(
      `${ORDER_BIZ}/service/impl/ShoppingCartServiceImpl.java`,
    )
    const signatureIndex = impl.indexOf('public ShoppingCartBatchAddVO batchAdd')
    expect(signatureIndex).toBeGreaterThan(-1)
    // 取方法签名上方最近的注解块（连续 @Xxx / 注释行），断言其中不含 @Transactional
    const header = impl.slice(0, signatureIndex).split('\n').reverse()
    const annotationLines: string[] = []
    for (const line of header) {
      const trimmed = line.trim()
      if (trimmed === '' || trimmed.startsWith('*') || trimmed.startsWith('/*') || trimmed.startsWith('//') || trimmed.startsWith('*/')) {
        continue
      }
      if (trimmed.startsWith('@')) {
        annotationLines.push(trimmed)
        continue
      }
      break
    }
    expect(annotationLines.some(line => line.includes('@Transactional'))).toBe(false)
  })

  it('quantity rule errors are surfaced as readable failure reasons', () => {
    const impl = repoSource(
      `${ORDER_BIZ}/service/impl/ShoppingCartServiceImpl.java`,
    )
    expect(impl).toContain('最小起订量')
    expect(impl).toContain('整数倍')
    // 失败原因走业务异常 msg，不能把异常类名透给用户
    expect(impl).toContain('readableReason')
  })

  it('summary carries progress only from the real planned/fulfilled columns', () => {
    // 77 号脚本给 shared_cart_item 加了 planned_quantity / fulfilled_quantity，
    // 收集阶段因此可以算真实进度。守两条底线：
    //   1. 进度字段确实来自这两个列（不是前端拿需求量凑的）；
    //   2. 未设计划的行不得回落成需求量，否则就是假进度。
    const vo = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/SharedCartSummaryVO.java',
    )
    expect(vo).toContain('ReplenishProgressVO.Summary progress')

    const calculator = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/support/ReplenishProgressCalculator.java',
    )
    // 未设计划 => plannedQuantity 空、remaining/completed 空，绝不回落 requestedQuantity
    expect(calculator).toContain('plannedQuantity == null')
    expect(calculator).not.toContain('getRequestedQuantity')
  })
})
