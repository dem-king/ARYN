import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 新人专享独立页接线契约。
 *
 * 首页楼层只展示「展示商品数」，活动商品更多时必须有出口进入本页；
 * 本页一次取全活动商品（limit=200），且「抢」与卡片价必须复用楼层的统一口径，
 * 不允许在页面里再实现一套价格/限购逻辑。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const PAGE = 'src/sub-pages/promotion/newbie/index.vue'
const FLOOR = 'src/components/diy/diy-newbie-floor/index.vue'
const API = 'src/api/promotion.ts'

describe('新人专享独立页', () => {
  it('页面一次取全活动商品，不沿用楼层的展示条数', () => {
    const page = source(PAGE)
    // 后端上限 200；页面必须显式传 limit，默认 20 会让商品再次被截断
    expect(page).toContain('GOODS_LIMIT = 200')
    expect(page).toContain('getCurrentNewbieFloor(GOODS_LIMIT)')
  })

  it('「抢」按钮复用楼层共用逻辑，并阻止冒泡跳商详', () => {
    const page = source(PAGE)
    expect(page).toContain('useNewbieGoodsActions')
    expect(page).toContain('handleGrab')
    expect(page).toMatch(/@click\.stop="handleGrab\(goods\)"/)
    // 整卡仍可进商详
    expect(page).toMatch(/@click="openGoods\(goods\.spuId\)"/)
  })

  it('楼层在商品多于展示数时提供「查看全部」入口', () => {
    const floor = source(FLOOR)
    expect(floor).toContain('hasMoreGoods')
    expect(floor).toContain('viewAll')
    expect(floor).toContain("url: '/sub-pages/promotion/newbie/index'")
    // 楼层只把可见条数交给横滑轨道，入口条件必须看活动全量商品数
    expect(floor).toMatch(/floor\.value\?\.goodsList\?\.length[\s\S]{0,80}showData\.value\.count/)
  })

  it('接口把 limit 透传给后端（不传保持楼层默认上限）', () => {
    const api = source(API)
    expect(api).toMatch(/getCurrentNewbieFloor\(limit\?: number\)/)
    expect(api).toContain('/promotion/app/newbie/current')
    expect(api).toContain('params: { limit }')
  })

  it('楼层接口不 skipToken：带登录态由后端判定资格', () => {
    const api = source(API)
    const start = api.indexOf('export function getCurrentNewbieFloor')
    const end = api.indexOf('\n}', start)
    const body = api.slice(start, end === -1 ? undefined : end)
    // skipToken 会让老客也被当成未登录，后端拿不到 userId，无法隐藏楼层
    expect(body).not.toContain('skipToken')
  })

  it('已登录老客（eligible=false）隐藏楼层并在专享页阻断', () => {
    const floor = source(FLOOR)
    // 楼层强制隐藏不受 emptyStrategy 影响：老客不该看到「活动存在」的任何痕迹
    expect(floor).toContain('hiddenForOldUser')
    expect(floor).toMatch(/shouldRender\s*&&\s*!hiddenForOldUser/)
    const page = source(PAGE)
    expect(page).toContain('const ineligible = computed')
    expect(page).toContain('promotion.newbie.ineligibleTitle')
    expect(page).toContain('v-else-if="ineligible"')
  })

  it('长期有效活动不显示倒计时，改为「长期有效」标签', () => {
    const page = source(PAGE)
    expect(page).toContain('activity.value?.longTerm === true')
    expect(page).toContain("t('promotion.newbie.longTerm')")
  })

  it('商品卡展示商品子标题，同名/空值不渲染', () => {
    const page = source(PAGE)
    expect(page).toContain('shouldShowSubtitle')
    expect(page).toContain('goods.subTitle')
    expect(page).toContain('class="goods-sub"')
  })
})
