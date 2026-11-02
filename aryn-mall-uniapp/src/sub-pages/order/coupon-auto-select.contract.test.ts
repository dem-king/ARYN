import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「确认页自动选最优券」契约。
 *
 * 背景：阶梯楼层写「先领券 · 后加购 · 下单自动抵扣」，但确认页原先默认不选券，
 * 用户不手动点「选择优惠券」就按原价成单 —— 文案承诺与实现不一致（2026-11-01 复核）。
 * 修复约束：
 * 1. 首轮结算 + 券列表到位后，自动选「本单可抵扣金额最大」的一张，并重新结算取服务端金额；
 * 2. 只自动选一次；用户手动确认过（选券/换券/不使用）后不得再覆盖；
 * 3. 券弹层必须回显当前已选券，并提供「不使用优惠券」显式出口（否则自动选券后无法取消）。
 *
 * 对应纯函数单测在 `src/utils/coupon-usability.test.ts`（pickBestCoupon / estimateCouponDiscount）。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 截取页面源码里一个函数的实现：到下一个顶层 `function` / `async function` 为止 */
function functionSource(page: string, name: string) {
  const start = page.indexOf(`function ${name}(`)
  if (start < 0)
    return ''
  const ends = [
    page.indexOf('\nfunction ', start + 1),
    page.indexOf('\nasync function ', start + 1),
  ].filter(index => index > start)
  return page.slice(start, ends.length ? Math.min(...ends) : page.length)
}

const ORDER_CONFIRM = 'src/sub-pages/order/order-confirm/index.vue'
const COUPON_SELECTOR = 'src/sub-pages/order/order-confirm/components/CouponSelector.vue'
const COUPON_USABILITY = 'src/utils/coupon-usability.ts'

describe('coupon auto-select contract', () => {
  it('首轮结算 + 券列表到位后自动选最优券', () => {
    const confirm = source(ORDER_CONFIRM)
    expect(confirm).toContain("import { pickBestCoupon } from '@/utils/coupon-usability'")

    const flow = functionSource(confirm, 'autoSelectBestCoupon')
    // 选券依赖结算下发的券门槛基数与券列表，二者缺一不可
    expect(flow).toContain('pickBestCoupon')
    expect(flow).toContain('info.couponEligibleAmount')
    expect(flow).toContain('state.orderParams.couponUserId = best.id')
    // 只尝试一次 + 用户手动操作后不再覆盖
    expect(flow).toContain('couponAutoApplied')
    expect(flow).toContain('couponUserTouched')
    // 选中后必须重算，页面金额以服务端返回为准
    expect(confirm).toContain('reSettleWithAutoCoupon')
    expect(flow).toContain('void reSettleWithAutoCoupon()')
  })

  it('自动选券由券列表加载完成触发，且失败不影响结算金额', () => {
    const confirm = source(ORDER_CONFIRM)
    expect(confirm).toMatch(
      /getCouponList\(\{ status: '0' \}\)[\s\S]{0,260}autoSelectBestCoupon\(\)/,
    )
    // 券列表失败保持「未选券」，不能阻断结算页
    expect(confirm).toContain('.catch(() => {')
  })

  it('用户手动确认过（含不使用）后不再自动覆盖', () => {
    const confirm = source(ORDER_CONFIRM)
    const couponConfirm = functionSource(confirm, 'couponConfirm')
    expect(couponConfirm).toContain('couponUserTouched = true')
    expect(couponConfirm).toContain('state.orderParams.couponUserId = next')
    expect(couponConfirm).toContain("const next = couponUserId || ''")
  })

  it('券弹层回显当前已选券，并提供「不使用优惠券」出口', () => {
    const selector = source(COUPON_SELECTOR)
    // 已选券作为 prop 传入，弹层打开时回显；未回显会导致点「确认」把券清掉
    expect(selector).toContain('couponUserId?: string')
    expect(selector).toContain('selectedCouponId.value = props.couponUserId')
    // 显式取消：未选中时回传空串，父级清空 couponUserId
    expect(selector).toContain("emit('couponConfirm', selectedCouponId.value || '')")
    expect(selector).toContain('checkout.coupon.noCoupon')
    // 父页面把当前已选券下发给弹层（否则弹层回显不到自动选中的券）
    expect(source(ORDER_CONFIRM)).toContain(
      ':coupon-user-id="state.orderParams.couponUserId"',
    )
  })

  it('最优券选择是纯函数：跳过不可用/已过期券，按抵扣金额排序', () => {
    const usability = source(COUPON_USABILITY)
    const pick = functionSource(usability, 'pickBestCoupon')
    expect(pick).toContain('getCouponUnavailableInfo')
    expect(pick).toContain('daysUntil')
    expect(pick).toContain('estimateCouponDiscount')
    // 有效期已过的券不能自动选
    expect(pick).toContain('days < 0')
  })
})
