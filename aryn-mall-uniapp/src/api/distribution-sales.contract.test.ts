import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('../..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 2026-11「销售业绩归属与提成」小程序端验收契约。
 *
 * 背景：这次同时改了三件事 —— 分销佣金提现下线改券兑换、新增券兑换页、
 * 新增销售工作台与推广码。它们分散在 api / 分享 composable / 页面 / pages.json
 * 四处，类型检查发现不了「页面没注册」「入口指向旧接口」这类整链路缺失，
 * 因此按源码结构钉住关键接线。
 */
describe('分销佣金改券兑换契约', () => {
  const distributionApi = source('src/api/distribution/entity.ts')

  it('券包列表/兑换/记录走 promotion 微服务域，且不再暴露提现申请调用', () => {
    expect(distributionApi).toContain('/promotion/app/distribution/coupon/pack/list')
    expect(distributionApi).toContain('/promotion/app/distribution/coupon/exchange')
    expect(distributionApi).toContain('/promotion/app/distribution/coupon/exchange/page')
    // 提现申请接口已下线：页面不得再调用（历史提现查询保留只读）
    expect(distributionApi).not.toContain("'/promotion/app/distribution/withdraw/apply'")
    expect(distributionApi).not.toContain('submitDistributionWithdrawApply')
    // 双模式：禁止手拼 boot 前缀，由 request 中间件 rewriteBootUrl 改写
    expect(distributionApi).not.toContain('/boot')
  })

  it('分享绑定携带 bindSource，扫码（scene 含 sid）记为 QRCODE', () => {
    expect(distributionApi).toContain('bindSource')

    const share = source('src/composables/useDistributionShare.ts')
    expect(share).toContain('sceneParams.sid')
    expect(share).toContain("'QRCODE'")
    expect(share).toContain("'SHARE'")
    expect(share).toContain('bindSource: cache.bindSource ??')
  })

  it('分销中心去掉提现申请、展示可兑换佣金并接入券兑换页', () => {
    const page = source('src/sub-pages/user/distribution/index.vue')
    expect(page).not.toContain('submitDistributionWithdrawApply')
    expect(page).not.toContain('submitWithdraw')
    expect(page).toContain('availableCommission')
    expect(page).toContain('DISTRIBUTION_COUPON_EXCHANGE_PATH')
    expect(page).toContain('DISTRIBUTION_COUPON_EXCHANGE_RECORD_PATH')
  })

  it('销售工作台接口全部按微服务域声明（promotion / mall-user）', () => {
    const salesApi = source('src/api/sales/entity.ts')
    expect(salesApi).toContain('/promotion/app/sales/workbench')
    expect(salesApi).toContain('/promotion/app/sales/customer/page')
    expect(salesApi).toContain('/promotion/app/sales/order/page')
    expect(salesApi).toContain('/mall-user/app/sales/qrcode')
    expect(salesApi).not.toContain('/boot')
  })

  it('销售工作台只对已登记销售露出入口，且页面内没有提现入口', () => {
    const userCenter = source('src/pages/user/user-center/index.vue')
    expect(userCenter).toContain('getSalesWorkbench')
    expect(userCenter).toContain('v-if="isSales"')
    expect(userCenter).toContain('/sub-pages/user/sales/index')

    const workbench = source('src/sub-pages/user/sales/index.vue')
    expect(workbench).toContain('getSalesWorkbench')
    expect(workbench).toContain('getSalesCustomerPage')
    expect(workbench).toContain('getSalesOrderPage')
    expect(workbench).toContain('getSalesQrcode')
    // 销售提成随工资发放：小程序不提供提现
    expect(workbench).not.toMatch(/withdraw/i)
  })
})

describe('新增页面已注册到 pages.json', () => {
  it('券兑换页/兑换记录页/销售工作台都在 sub-pages 分包内', () => {
    const pages = source('src/pages.json')
    expect(pages).toContain('"path": "user/distribution/coupon-exchange"')
    expect(pages).toContain('"path": "user/distribution/coupon-exchange-record"')
    expect(pages).toContain('"path": "user/sales/index"')
  })
})
