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
    // 文件流（上传/下载）单独一文件，但同样只能走统一解析函数，
    // 不得自己写 JSON.parse(import.meta.env.VITE_OPEN_BOOT) 这类旁路
    const sharedImportApi = source('src/sub-pages/api/order/sharedCartImport.ts')
    expect(sharedImportApi).toContain('parseOpenBoot')
    expect(sharedImportApi).toContain('rewriteBootUrl')
    expect(sharedImportApi).not.toContain('JSON.parse(import.meta.env')
    expect(sharedImportApi).not.toContain('\'/boot/')
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

  it('quantity rules stay client-side and batch failures stay readable', () => {
    // 数量规则（MOQ/步长）随船供包装资料下线（2026-09-29，见 ShoppingCartServiceImpl
    // batchAdd 内注释）：服务端不再做 MOQ/步长拦截，口径收敛在 C 端 utils/quick-cart，
    // 与单条加购一致。反向断言当哨兵——若服务端重新引入数量拦截，必须显式更新本契约。
    const impl = repoSource(
      `${ORDER_BIZ}/service/impl/ShoppingCartServiceImpl.java`,
    )
    expect(impl).not.toContain('最小起订量')
    expect(impl).not.toContain('整数倍')
    // 失败原因仍走业务异常 msg（readableReason），不能把异常类名透给用户
    expect(impl).toContain('readableReason')
  })

  it('summary no longer fabricates any purchase progress', () => {
    // 契约反转（2026-10-09）：计划量/已采量模型整体删除。
    // 摘要只回答「项数/人数/预估金额/预览明细」，不再下发任何进度字段；
    // 计算器文件本身也必须消失。
    const vo = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/SharedCartSummaryVO.java',
    )
    expect(vo).not.toContain('ReplenishProgressVO')
    expect(vo).not.toContain('progress')

    expect(() => repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/support/ReplenishProgressCalculator.java',
    )).toThrow()

    // 预估金额只按成员申请量算，不碰核定数量（那是提交那一刻才定的）
    const impl = repoSource(`${ORDER_BIZ}/service/impl/SharedCartServiceImpl.java`)
    const summaryStart = impl.indexOf('public SharedCartSummaryVO getActiveSummary')
    const summaryBody = impl.slice(summaryStart, impl.indexOf('private SharedCart findActiveCart', summaryStart))
    expect(summaryBody).toContain('getRequestedQuantity')
    expect(summaryBody).not.toContain('getPlannedQuantity')
    expect(summaryBody).not.toContain('getFulfilledQuantity')
  })
})
