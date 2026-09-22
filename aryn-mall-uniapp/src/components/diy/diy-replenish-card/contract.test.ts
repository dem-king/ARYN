import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 补给单卡片接线契约。
 *
 * 进度口径本身由 `src/utils/replenish-progress.test.ts` 做行为测试；
 * 这里只守类型检查看不出来的两件事：前端路径与后端映射是否漂移、
 * 卡片是否真的用了那套口径（而不是自己另写一套展示逻辑）。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const CARD = 'src/components/diy/diy-replenish-card/index.vue'
const API = 'src/api/order/sharedCart.ts'
const ORDER_BIZ = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order'

describe('补给单卡片契约', () => {
  it('排计划接口路径与后端映射一致', () => {
    const api = source(API)
    // 用正则避免在普通字符串里写模板占位符（no-template-curly-in-string）
    expect(api).toMatch(/\$\{BASE\}\/\$\{id\}\/items\/plan/)

    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('@RequestMapping("/app/shared-cart")')
    expect(controller).toContain('@PutMapping("/{id}/items/plan")')
  })

  it('摘要类型接住服务端 progress 与预览行进度字段', () => {
    const api = source(API)
    expect(api).toContain('progress?: ReplenishSummaryProgress | null')
    for (const field of [
      'plannedQuantity',
      'fulfilledQuantity',
      'remainingQuantity',
      'completed',
    ]) {
      expect(api, `预览行缺少 ${field}`).toContain(field)
    }

    const vo = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/SharedCartSummaryVO.java',
    )
    for (const field of ['plannedQuantity', 'fulfilledQuantity', 'remainingQuantity', 'completed']) {
      expect(vo, `后端 VO 缺少 ${field}`).toContain(field)
    }
  })

  it('卡片用统一的进度视图，不自己另写一套除法', () => {
    const card = source(CARD)
    expect(card).toContain('buildReplenishProgressView')
    // 自己算百分比就会与详情页口径漂移
    expect(card).not.toMatch(/progressPercent[\s\S]{0,40}\/\s*\d/)
  })

  it('删掉「刻意不展示进度」的过期注释', () => {
    // 这两处注释在 C1 之后已与实现相反，留着会误导下一个接手的人
    expect(source(API)).not.toContain('不做进度百分比')
    expect(source(CARD)).not.toContain('刻意不展示')
  })
})
