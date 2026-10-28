import { describe, expect, it } from 'vitest'
import {
  formatNoticeDateTime,
  formatNoticeTime,
  isImportantNotice,
  noticeCategoryLabel,
  noticeCategoryLabelKey,
  noticeCategoryLabelOf,
  noticeCategoryOf,
} from './notice'

/** 固定「当前时间」，避免用例随运行日期漂移。 */
const NOW = new Date(2026, 9, 25, 12, 0, 0).getTime()

describe('noticeCategoryOf', () => {
  it('把截图里的内部枚举码收敛成用户分类', () => {
    expect(noticeCategoryOf({ category: 'ORDER', sourceType: 'ORDER_PAY' })).toBe('order')
    expect(noticeCategoryOf({ category: 'PROMOTION', sourceType: 'COUPON_RECEIVE' })).toBe('promotion')
    expect(noticeCategoryOf({ category: 'VESSEL_BIND_AUDIT', sourceType: 'VESSEL_BIND_APPLY' })).toBe('vessel')
    expect(noticeCategoryOf({ category: 'REFUND', sourceType: 'ORDER_REFUND' })).toBe('refund')
  })

  it('只给 category 时也能按业务域归类', () => {
    expect(noticeCategoryOf({ category: 'ORDER' })).toBe('order')
    expect(noticeCategoryOf({ category: 'DELIVERY_TASK' })).toBe('delivery')
    expect(noticeCategoryOf({ category: 'PROMOTION' })).toBe('promotion')
    expect(noticeCategoryOf({ category: 'SYSTEM' })).toBe('system')
  })

  it('sourceType 优先于 category：退款通知的类目通常也是 ORDER', () => {
    expect(noticeCategoryOf({ category: 'ORDER', sourceType: 'ORDER_REFUND_APPLY' })).toBe('refund')
    expect(noticeCategoryOf({ category: 'ORDER', sourceType: 'COD_PAY_REMIND' })).toBe('order')
  })

  it('老通知没有 sourceType 时按标题关键词兜底', () => {
    expect(noticeCategoryOf({ title: '优惠券已到账' })).toBe('promotion')
    expect(noticeCategoryOf({ title: '船舶绑定申请已通过' })).toBe('vessel')
    expect(noticeCategoryOf({ title: '订单支付成功' })).toBe('order')
  })

  it('未知分类不猜语义，兜底为 other', () => {
    expect(noticeCategoryOf({})).toBe('other')
    expect(noticeCategoryOf({ category: 'BRAND_NEW_DOMAIN' })).toBe('other')
  })
})

describe('noticeCategoryLabel', () => {
  it('输出中文标签而不是后端枚举码', () => {
    expect(noticeCategoryLabel({ category: 'ORDER' })).toBe('订单')
    expect(noticeCategoryLabel({ category: 'PROMOTION' })).toBe('优惠')
    expect(noticeCategoryLabel({ category: 'VESSEL_BIND_AUDIT' })).toBe('船舶')
    expect(noticeCategoryLabel({ category: 'REFUND' })).toBe('退款')
    expect(noticeCategoryLabel({ category: 'DELIVERY_TASK' })).toBe('配送')
    expect(noticeCategoryLabel({ category: 'SYSTEM' })).toBe('系统')
    expect(noticeCategoryLabel({ category: 'UNKNOWN' })).toBe('通知')
  })

  it('收件箱条目重载与 noticeCategoryLabel 同口径', () => {
    const item = {
      category: 'VESSEL_BIND_AUDIT',
      sourceType: 'VESSEL_BIND_APPLY',
      title: '船舶绑定申请已通过',
    }
    expect(noticeCategoryLabelOf(item)).toBe('船舶')
  })

  it('每个分类都有独立的词条 key，避免页面手拼文案', () => {
    expect(noticeCategoryLabelKey('order')).toBe('message.notice.category.order')
    expect(noticeCategoryLabelKey('other')).toBe('message.notice.category.other')
  })
})

describe('isImportantNotice', () => {
  it('只有 HIGH 才展示「重要」，缺省 NORMAL 与脏值都不露码', () => {
    expect(isImportantNotice('HIGH')).toBe(true)
    expect(isImportantNotice('high')).toBe(true)
    expect(isImportantNotice('NORMAL')).toBe(false)
    expect(isImportantNotice(undefined)).toBe(false)
    expect(isImportantNotice('')).toBe(false)
  })
})

describe('formatNoticeTime', () => {
  it('今天只显示时分，昨天带「昨天」，同年补月日，跨年补年份', () => {
    expect(formatNoticeTime('2026-10-25 09:05:33', NOW)).toBe('09:05')
    expect(formatNoticeTime('2026-10-24 17:18:01', NOW)).toBe('昨天 17:18')
    expect(formatNoticeTime('2026-10-22 21:24:20', NOW)).toBe('10-22 21:24')
    expect(formatNoticeTime('2025-12-31 23:59:00', NOW)).toBe('2025-12-31')
  })

  it('解析失败原样返回，不因脏数据抛错', () => {
    expect(formatNoticeTime('not-a-date', NOW)).toBe('not-a-date')
    expect(formatNoticeTime(undefined, NOW)).toBe('')
  })
})

describe('formatNoticeDateTime', () => {
  it('详情页展示到分钟，去掉秒级精度', () => {
    expect(formatNoticeDateTime('2026-10-24 17:18:01')).toBe('2026-10-24 17:18')
    expect(formatNoticeDateTime('')).toBe('')
  })
})
