import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 补给单卡片接线契约。
 *
 * 这里只守类型检查看不出来的一件事：前端展示与后端下发的摘要在字段上是否漂移。
 * 卡片只回答「几项、几个人、多少钱、有什么」，不做采购执行进度 —— 那没有事实来源。
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
  it('卡片不再声明或展示任何计划量/已采量字段', () => {
    const api = source(API)
    const card = source(CARD)
    for (const gone of ['plannedQuantity', 'fulfilledQuantity', 'remainingQuantity', 'ReplenishSummaryProgress']) {
      expect(api, `类型里仍有 ${gone}`).not.toContain(gone)
      expect(card, `卡片里仍有 ${gone}`).not.toContain(gone)
    }
    // 后端摘要 VO 同样不该再下发这些字段
    const vo = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/SharedCartSummaryVO.java',
    )
    for (const gone of ['plannedQuantity', 'fulfilledQuantity', 'remainingQuantity', 'completed']) {
      expect(vo, `后端 VO 仍有 ${gone}`).not.toContain(gone)
    }
  })

  it('卡片只用申请数量拼预览行，不画进度条也不做除法', () => {
    const card = source(CARD)
    // 预览行的数量直接取服务端下发的 quantity
    expect(card).toContain('item.quantity')
    // 任何形式的进度百分比都是回归
    expect(card).not.toContain('progressView')
    expect(card).not.toContain('progress-bar')
    expect(card).not.toMatch(/progressPercent[\s\S]{0,40}\/\s*\d/)
    // 金额只作量级参考，文案必须是「预估」而不是「合计」
    expect(card).toContain('预估 ¥')
  })

  it('商品名从 SPU 批量查询取，不依赖 SKU 回填的 goodsSpu', () => {
    // getSkuByIds 走 goodsSkuResultMap，**不填充 goodsSpu**，靠它取名字会恒为 null，
    // 卡片只能显示 19 位 SKU ID（管理端详情当年同一个坑）。类型检查发现不了。
    const impl = repoSource(
      `${ORDER_BIZ}/service/impl/SharedCartServiceImpl.java`,
    )
    expect(impl).toContain('remoteGoodsSpuService.getSpuByIds')
    // 只在注释里提 goodsSpu 可以；写成取值表达式就是回退到坏链路
    const code = impl
      .split('\n')
      .filter(line => !line.trim().startsWith('*') && !line.trim().startsWith('//'))
      .join('\n')
    expect(code).not.toContain('getGoodsSpu()')
  })

  it('无数据策略为显示占位时渲染引导卡，两个入口直达', () => {
    const card = source(CARD)
    // 占位只在「摘要已查但无单」时出现；还没查/未登录/非船供租户仍整体隐藏
    expect(card).toContain('summaryLoaded')
    expect(card).toContain('showData.value.emptyStrategy === \'placeholder\'')
    // 占位卡是入口不是装饰：能发起补给单、能直达「导入清单下单」向导
    expect(card).toContain('/sub-pages/order/shared-cart/list')
    expect(card).toContain('/sub-pages/order/shared-cart/import-entry')
  })
})
