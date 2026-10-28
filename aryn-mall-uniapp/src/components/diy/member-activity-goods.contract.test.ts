import type { GoodsTabConfig, RetailDataSource } from './retail-types'

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 会员专区「按会员价活动自动」数据源接线契约。
 *
 * 漏网方式：接口/类型都加了，但 shop 端装修 JSON 的 mode='activity' 在归一化时被
 * 降级成 automatic（活动货盘变成分类排序），或 loadGoodsTabPage 没接活动分支，
 * 运营配了「按活动自动」C 端却仍走旧数据源。类型检查发现不了这类跨文件断线，
 * 按源码结构钉住：API 契约、类型层接受、数据层分支与空态、组件归一化原样保留。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/** 截取一个顶层 `export async function name(` 的实现（到下一个顶层函数为止） */
function loaderSource(data: string, name: string) {
  const start = data.indexOf(`export async function ${name}(`)
  if (start < 0)
    return ''
  const ends = [
    data.indexOf('\nexport async function ', start + 1),
    data.indexOf('\nasync function ', start + 1),
    data.indexOf('\nexport function ', start + 1),
    data.indexOf('\nfunction ', start + 1),
  ].filter(index => index > start)
  return data.slice(start, ends.length ? Math.min(...ends) : data.length)
}

const PROMOTION_API = 'src/api/promotion.ts'
const RETAIL_TYPES = 'src/components/diy/retail-types.ts'
const RETAIL_DATA = 'src/components/diy/retail-data.ts'
const TABS_COMPONENT = 'src/components/diy/diy-goods-tabs/index.vue'

describe('会员价活动货盘 API 契约', () => {
  it('exposes GET /promotion/app/member/activity-goods without hardcoded /boot', () => {
    const api = source(PROMOTION_API)
    const fn = api.slice(api.indexOf('export function getMemberActivityGoods'), api.length)

    expect(fn).toContain("'/promotion/app/member/activity-goods'")
    expect(fn).not.toContain('/boot')
    // 免登可访问的公共货盘：不携带登录态，也不受登录过期影响
    expect(fn).toContain('skipToken: true')
    // activityId 为空不传参（后端据此自动取当前进行中的会员价活动）
    expect(fn).toContain('id ? { params: { activityId: id } } : {}')

    // VO 形状：活动元信息 + 活动配置顺序的 spuIds
    const voBlock = api.slice(
      api.indexOf('export interface AppMemberActivityGoodsVO'),
      api.indexOf('export function getMemberActivityGoods'),
    )
    for (const field of ['activityId', 'activityName', 'endTime', 'spuIds'])
      expect(voBlock).toContain(field)
  })
})

describe('activity 数据源类型契约', () => {
  it('accepts mode=activity with optional activityId at the type level', () => {
    // 编写期即验证：装修 JSON 的 activity 配置能被类型接受（空 activityId=自动）
    const auto: GoodsTabConfig = {
      dataSource: { mode: 'activity' },
      id: 'member',
      title: '会员专区',
    }
    const fixed: RetailDataSource = { activityId: 'act-1', mode: 'activity' }
    const withBanner: GoodsTabConfig = {
      dataSource: { activityId: '', mode: 'activity' },
      id: 'member',
      memberBanner: { enabled: true },
      title: '会员专区',
    }

    expect(auto.dataSource.mode).toBe('activity')
    expect(auto.dataSource.activityId).toBeUndefined()
    expect(fixed.activityId).toBe('act-1')
    expect(withBanner.memberBanner?.enabled).toBe(true)
  })

  it('declares activity mode and activityId on RetailDataSource', () => {
    const types = source(RETAIL_TYPES)
    const block = types.slice(
      types.indexOf('export interface RetailDataSource'),
      types.indexOf('export interface RetailBaseProps'),
    )
    expect(block).toContain("'activity'")
    expect(block).toContain('activityId?: string')
    // 注释写清：货盘与顺序由活动决定、活动失效空态
    expect(block).toContain('活动')
    expect(block).toContain('空态')
  })
})

describe('goods-tabs activity 取数分支契约', () => {
  it('loads activity spuIds then restores the activity-configured order', () => {
    const data = source(RETAIL_DATA)
    const tabPage = loaderSource(data, 'loadGoodsTabPage')

    // activity 分支位于 automatic 之前，且只认 mode === 'activity'
    const activityAt = tabPage.indexOf("mode === 'activity'")
    const manualAt = tabPage.indexOf("mode === 'manual'")
    const automaticAt = tabPage.indexOf('getGoodsPage({')
    expect(manualAt).toBeGreaterThan(-1)
    expect(activityAt).toBeGreaterThan(manualAt)
    expect(automaticAt).toBeGreaterThan(activityAt)

    expect(tabPage).toContain('getMemberActivityGoods(tab.dataSource.activityId || \'\')')
    // 活动顺序回排 + 每页上限截断
    expect(tabPage).toContain('restoreConfiguredOrder(')
    expect(tabPage).toContain('.slice(0, Math.max(1, size))')
    // 一次性取全、不做滚动分页
    expect(tabPage).toContain('manual: true')
  })

  it('treats null / empty spuIds / API failure as an empty tab without throwing', () => {
    const data = source(RETAIL_DATA)
    const tabPage = loaderSource(data, 'loadGoodsTabPage')

    // 失败静默：活动接口异常按空态（catch），不冒泡到装修页
    expect(tabPage).toContain('catch')
    expect(tabPage).toContain('activity = null')
    // 无活动 / 无商品：空列表空态，不请求空商品
    expect(tabPage).toContain('spuIds.length === 0')
    expect(tabPage).toContain('return { list: [], manual: true, total: 0 }')
  })

  it('keeps the member banner price enrichment for the activity branch', () => {
    const data = source(RETAIL_DATA)
    const tabPage = loaderSource(data, 'loadGoodsTabPage')

    // 活动模式通常就是会员专区：同样走 memberBanner 门控的列表会员价补价
    expect(tabPage.match(/withListMemberPrices\(tab, /g) || []).toHaveLength(3)
    expect(tabPage).toContain('const list = await withListMemberPrices(tab, items)')
  })

  it('does not let other loaders call the activity goods API', () => {
    const data = source(RETAIL_DATA)
    for (const loader of [
      'loadGoodsGroup',
      'loadGoodsGroupPage',
      'loadGoodsRanking',
      'loadLimitedActivities',
      'loadSeckillSessions',
      'loadDiscounts',
      'loadNewbieFloor',
      'loadGoodsWaterfall',
      'loadGoodsWaterfallPage',
      'loadGoodsRecommend',
    ]) {
      expect(loaderSource(data, loader)).not.toContain('getMemberActivityGoods')
    }
    // 整个数据层只有 loadGoodsTabPage 引用活动货盘接口
    expect(data.match(/getMemberActivityGoods\(/g) || []).toHaveLength(1)
  })
})

describe('装修 Tab 归一化契约', () => {
  it('keeps mode=activity and activityId when normalizing published JSON', () => {
    const component = source(TABS_COMPONENT)
    const normalize = component.slice(
      component.indexOf('function normalizeTabs('),
      component.indexOf('const showData = computed'),
    )

    // 原样保留 activity 模式（不能被降级成 automatic，否则活动货盘失效）
    expect(normalize).toContain("source.mode === 'activity'")
    expect(normalize).toContain("? 'activity'")
    // activityId 仅 activity 模式透传；空值不下发（空=自动取进行中活动）
    expect(normalize).toContain('source.activityId')
    expect(normalize).toContain("...(mode === 'activity' && activityId ? { activityId } : {})")
  })
})
