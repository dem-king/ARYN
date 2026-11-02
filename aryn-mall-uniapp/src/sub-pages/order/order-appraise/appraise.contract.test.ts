import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「一单多品评价」C 端接线契约。
 *
 * 这次改动的回归方式不显眼：整单快捷区还在、逐卡编辑也在，但提交时又退回
 * 「每品必填文字」、子集提交漏传跳过/已评商品、我的评价入口没接上新接口——
 * 页面看起来都对，用户点下去才报错。因此按源码结构钉住关键接线，
 * 纯数据规则由 `payload.test.ts` / `appraise-actions.test.ts` 覆盖。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const ORDER_INFO_API = 'src/api/order/orderInfo.ts'
const ORDER_APPRAISE = 'src/sub-pages/order/order-appraise/index.vue'
const USER_APPRAISE = 'src/sub-pages/user/appraise/index.vue'
const USER_APPRAISE_ACTIONS = 'src/sub-pages/user/appraise/appraise-actions.ts'
const GOODS_APPRAISE = 'src/sub-pages/product/goods-appraise/index.vue'
const GOODS_COMMENT = 'src/sub-pages/product/goods-detail/components/GoodsComment.vue'

describe('评价接口契约', () => {
  it('评价/追评/修改/删除四个接口路径与任务契约一致', () => {
    const api = source(ORDER_INFO_API)
    expect(api).toContain('/mall-order/app/orderinfo/appraise/${id}`')
    expect(api).toContain('/mall-order/app/orderinfo/appraise/${id}/advance')
    expect(api).toContain('/mall-order/app/orderinfo/appraise/${id}/item/${orderItemId}')
    expect(api).toMatch(/orderAppraiseAdvance[\s\S]{0,120}Post/)
    expect(api).toMatch(/orderAppraiseModify[\s\S]{0,120}Put/)
    expect(api).toMatch(/orderAppraiseDelete[\s\S]{0,120}Delete/)
  })
})

describe('发表评价页（整单快捷 + 逐品可选）', () => {
  it('不再逐品强制文字，提交载荷交给纯函数组装', () => {
    const page = source(ORDER_APPRAISE)
    // 旧实现：任一商品没写文字就 return error(...)，这是本次要修掉的核心问题
    expect(page).not.toMatch(/if \(!item\.content\)/)
    expect(page).toContain('buildAppraisePayload')
    expect(page).toContain('findFirstAppraiseIssue')
    expect(page).toContain("from './payload'")
  })

  it('整单区与开关齐全：全部五星/统一文案/统一晒图/物流服务分/匿名/分开评价', () => {
    const page = source(ORDER_APPRAISE)
    for (const key of [
      'order.appraise.overallTitle',
      'order.appraise.allFiveStars',
      'order.appraise.unifiedTitle',
      'order.appraise.unifiedPics',
      'order.appraise.logisticsRating',
      'order.appraise.serviceRating',
      'order.appraise.anonymous',
      'order.appraise.separate',
      'order.appraise.writeSeparately',
      'order.appraise.skip',
    ])
      expect(page).toContain(key)
  })

  it('加载时按 orderId 拉已评列表标记 reviewed，提交后按剩余件数分流', () => {
    const page = source(ORDER_APPRAISE)
    expect(page).toContain('getUserPage')
    expect(page).toContain('orderId: state.orderId')
    expect(page).toContain('buildReviewedItemMap')
    expect(page).toContain('order.appraise.successPartial')
    expect(page).toContain('order.appraise.successAll')
    expect(page).toContain("name: 'user-appraise'")
  })
})

describe('我的评价页（追评/修改/删除）', () => {
  it('接入追评/修改/删除接口，成功后 reload 列表', () => {
    const page = source(USER_APPRAISE)
    expect(page).toContain('orderAppraiseAdvance')
    expect(page).toContain('orderAppraiseModify')
    expect(page).toContain('orderAppraiseDelete')
    expect(page).toContain('pagingRef.value?.reload()')
    expect(page).toContain('wd-popup')
  })

  it('展示商品图/名、标签、匿名/系统默认标识与追评内容', () => {
    const page = source(USER_APPRAISE)
    expect(page).toContain('item.spuUrls')
    expect(page).toContain('item.spuName')
    expect(page).toContain('item.tags')
    expect(page).toContain('isAnonymousAppraise')
    expect(page).toContain('isSystemDefaultAppraise')
    expect(page).toContain('advanceContent')
  })

  it('操作可见性由纯函数判定（30 天窗口 + 追评/修改各一次）', () => {
    const actions = source(USER_APPRAISE_ACTIONS)
    expect(actions).toContain('APPRAISE_ACTION_WINDOW_DAYS = 30')
    expect(actions).toContain('canAdvanceAppraise')
    expect(actions).toContain('canModifyAppraise')
    expect(actions).toContain('canDeleteAppraise')
    // 系统默认评价只允许追评
    expect(actions).toMatch(/canModifyAppraise[\s\S]{0,200}isSystemDefaultAppraise/)
    expect(actions).toMatch(/canDeleteAppraise[\s\S]{0,200}isSystemDefaultAppraise/)
  })
})

describe('商详/评价列表展示兜底', () => {
  it('商品评价页与商详评价区展示标签、追评，并对 null 评分兜底', () => {
    for (const file of [GOODS_APPRAISE, GOODS_COMMENT]) {
      const page = source(file)
      expect(page).toContain("product.review.scoreMissing")
      expect(page).toContain("product.review.advance")
      expect(page).toContain('item.tags')
      expect(page).toContain('hasScore')
    }
  })
})
