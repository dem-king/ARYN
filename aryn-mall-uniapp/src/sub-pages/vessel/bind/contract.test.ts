import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { OVERLAY_Z_INDEX } from '@/utils/overlay'

/**
 * 船舶绑定页「提交申请」反馈链路守门。
 *
 * 真实缺陷（2026-10-18）：用户点「提交申请」后界面毫无反应。两层原因——
 *  1. 后端对「同一用户仅一条待审核申请」有防重拦截（刻意保留），返回
 *     `code=1` + 「您有一条待审核的申请，请等待运营处理」；
 *  2. 前端把这条错误交给全局 toast，而 wd-toast 默认 z-index: 100，
 *     被本页 900/901 的底部弹层整个盖住；同时 `submitApply` 的
 *     `.catch(() => {})` 不写任何内联状态，弹层保持原样。
 *
 * 这两层任一回归，用户就又会掉回「点了没反应」。所以这里把契约钉死在源码上：
 * 弹层内必须有可见错误、必须有待审核拦截与撤回入口、全局反馈层必须高于页面浮层。
 */

const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const BIND_PAGE = 'src/sub-pages/vessel/bind/index.vue'

/** 取 `style` 字符串里出现的全部 z-index 数字（模板里这些是裸值） */
function zIndexesIn(text: string) {
  return [...text.matchAll(/z-index:\s*(\d+)/g)].map(match => Number(match[1]))
}

describe('船舶绑定申请：提交失败必须在弹层内可见', () => {
  it('提交失败时把错误写进弹层内联状态，而不是静默吞掉', () => {
    const page = source(BIND_PAGE)

    // 弹层必须有自己的错误状态
    expect(page).toMatch(/applyState[\s\S]{0,400}error/)
    // submitApply 的 catch 必须写入该状态（不允许空 catch）
    expect(page).toMatch(/applyState\.error\s*=/)
  })

  it('弹层里渲染了错误提示节点', () => {
    const page = source(BIND_PAGE)

    expect(page).toContain('applyState.error')
    // 提交前再清空上次错误，避免旧提示挂在新的输入上
    expect(page).toMatch(/applyState\.error\s*=\s*''/)
  })

  it('存在待审核申请时，提交按钮前给出拦截说明', () => {
    const page = source(BIND_PAGE)

    expect(page).toContain('pendingApply')
    expect(page).toContain('pendingApplyTip')
    // 待审核时直接拦在本地，不反复发请求
    expect(page).toMatch(/if\s*\(pendingApply\.value\)/)
  })

  it('待审核拦截给出「撤回」出口，而不是让用户干等', () => {
    const page = source(BIND_PAGE)

    expect(page).toContain('handleCancelApply(pendingApply)')
    // 拦截依赖本地申请列表，运营刚审核完时可能是旧状态：必须能就地刷新
    expect(page).toContain('vessel.bind.refreshStatus')
    expect(page).toMatch(/function openApply[\s\S]{0,500}refreshPendingState\(\)/)
  })

  it('刷新待审核状态时一并清掉陈旧内联错误', () => {
    // 真实风险：提交被待审核拦下 → 运营随后审核通过 → 用户点「刷新状态」，
    // 横幅消失但红块还挂着，用户被自己的旧错误劝退（其实此时已可提交）。
    const page = source(BIND_PAGE)

    expect(page).toMatch(/function refreshPendingState[\s\S]{0,300}applyState\.error\s*=\s*''/)
    expect(page).toContain('refreshPendingState()')
    // 刷新按钮必须走 refreshPendingState，而不是裸的 fetchJoinData
    expect(page).not.toMatch(/@tap="fetchJoinData\(\)"/)
  })

  it('本地拦截不与头部横幅重复同一段文案', () => {
    const page = source(BIND_PAGE)

    // 横幅已经讲了「哪条船、请先撤回」，操作点只补动作指引
    const localBlock = page.match(/if \(pendingApply\.value\) \{[\s\S]{0,300}/)
    expect(localBlock, '未找到 pendingApply 本地拦截').toBeTruthy()
    expect(localBlock?.[0]).not.toContain('pendingApplyTip')
  })

  it('切换候选人 / 返回搜索时清掉上一位的加人失败文案', () => {
    const page = source(BIND_PAGE)

    expect(page).toMatch(/function openAdd[\s\S]{0,300}addState\.error\s*=\s*''/)
    expect(page).toMatch(/function backToSearch[\s\S]{0,150}addState\.error\s*=\s*''/)
  })

  it('打开弹层时清空上一次的提交错误', () => {
    const page = source(BIND_PAGE)

    expect(page).toMatch(/function openApply[\s\S]{0,400}applyState\.error\s*=\s*''/)
  })

  it('同一页的「添加成员」弹层也不再静默失败', () => {
    const page = source(BIND_PAGE)

    // 加人失败同样落在底部弹层里，不能只依赖全局 toast
    expect(page).toMatch(/function submitAdd[\s\S]{0,900}addState\.error\s*=/)
    expect(page).toContain('addState.error')
    expect(page).toMatch(/function openAddFlow[\s\S]{0,300}addState\.error\s*=\s*''/)
  })
})

describe('绑定申请：IMO 编号与呼号是两个独立字段', () => {
  // IMO 是 7 位数字的船舶识别号，呼号（call sign）是另一套字母短码。
  // 旧版把两者塞进一个「IMO / 呼号」输入框 → 后端只能混存，运营无法按 IMO 精确匹配。
  it('弹层里 IMO 与呼号各占一行，且分别绑定两个字段', () => {
    const page = source(BIND_PAGE)

    // 旧标签必须消失，否则用户仍会当成一个字段填
    expect(page).not.toContain('IMO / 呼号')
    // i18n 后标签文案来自词条（与 order-receipt-contract 同口径断言 key）
    expect(page).toContain('vessel.bind.imo')
    expect(page).toContain('vessel.bind.callSign')
    // 两个 v-model 指向不同 state（顺序不限，存在即合格）
    expect(page).toMatch(/v-model="applyState\.form\.applyVesselImo"/)
    expect(page).toMatch(/v-model="applyState\.form\.applyVesselCallSign"/)
    // 表单 state 必须初始化呼号，否则 v-model 写进未声明的键（小程序端还会丢响应式）
    expect(page).toMatch(/form:\s*\{[\s\S]{0,300}applyVesselCallSign:\s*''/)
  })

  it('提交 payload 同时带 IMO 与呼号，空串仍归一为 undefined', () => {
    const page = source(BIND_PAGE)

    expect(page).toMatch(/applyVesselImo:\s*applyState\.form\.applyVesselImo\s*\|\|\s*undefined/)
    expect(page).toMatch(
      /applyVesselCallSign:\s*applyState\.form\.applyVesselCallSign\s*\|\|\s*undefined/,
    )
  })

  it('api 类型里两个字段都是可选的独立字段', () => {
    const api = source('src/api/vessel.ts')

    // 列表回读与提交入参都要认识呼号，否则后端返回的呼号在 TS 侧不可见
    expect(api.match(/applyVesselCallSign\?:\s*string/g)?.length).toBe(2)
    expect(api.match(/applyVesselImo\?:\s*string/g)?.length).toBe(2)
  })

  it('船舶信息区拆成 4 行，只有最后一行去掉分隔线', () => {
    const page = source(BIND_PAGE)
    // 取模板里最后一处「船舶信息」到「联系信息」之间的区块（前面还有脚本注释）。
    // i18n 后标题文案来自词条，用词条 key 定位区块。
    const start = page.lastIndexOf("t('vessel.bind.vesselSection')")
    const end = page.indexOf("t('vessel.bind.contactSection')", start)
    expect(start).toBeGreaterThan(-1)
    expect(end).toBeGreaterThan(start)

    const block = page.slice(start, end)
    const rows = block.match(/min-height: 96rpx/g) ?? []
    const dividers = block.match(/border-bottom: 1rpx solid #EDEEF0/g) ?? []
    // 船名 / IMO / 呼号 / 常靠港 = 4 行，中间 3 条分隔线（最后一行不画）
    expect(rows.length).toBe(4)
    expect(dividers.length).toBe(3)
  })
})

describe('我的申请：必须让用户看出实际绑到了哪条船', () => {
  // 真实缺陷（2026-10-19）：用户申请「悦航8号」，运营在审核弹窗里把船选成了
  // 「悦航7号」。申请单冻结为「已通过」，卡片只显示申请船名 —— 用户完全看不出
  // 自己实际进了哪条船，而船舶成员身份决定了他能看谁的成员、靠港计划与配送单。
  it('已通过的申请展示服务端返回的实际绑定船名', () => {
    const page = source(BIND_PAGE)

    expect(page).toContain('matchedVesselName')
    expect(page).toContain('vessel.bind.matchedVessel')
    // 只在已通过（status==='2'）且拿到匹配船名时展示，未通过时不能编造一条船
    expect(page).toMatch(/apply\.status\s*===\s*'2'[\s\S]{0,120}apply\.matchedVesselName/)
  })

  it('申请船名与实际绑定船名不一致时渲染警示文案', () => {
    const page = source(BIND_PAGE)

    // 文案由 utils 的纯函数产出（含归一化兜底判定），页面不许自己拼
    expect(page).toContain('mismatchTip')
    expect(page).toContain('applyMismatchTip(apply)')
    // 警示块用既有的警示配色，不自造新色值
    expect(page).toMatch(/applyMismatchTip\(apply\)[\s\S]{0,200}#FBF0E1/)
  })

  it('api 里透出 vesselMismatch 与成员绑定来源字段', () => {
    const api = source('src/api/vessel.ts')

    expect(api).toContain('vesselMismatch?: boolean')
    expect(api).toContain('bindSource?: string')
    expect(api).toContain('bindSourceLabel?: string')
    expect(api).toContain('bindReason?: string')
  })
})

describe('全局反馈层必须高于页面自定义浮层', () => {
  // 页面里现存的自定义浮层裸值：绑定/选址弹层 900/901、SKU 弹窗 990、
  // 类目面板与接龙导入 999、商详/日历 root-portal 1000，最高是
  // shared-cart-destination-sheet 与 delivery/trip-detail 的 wd-popup 1020。
  // 反馈层必须整体高于 1020 —— 只按 1000 卡会把「被 901 盖」换成「被 1020 盖」。
  const PAGE_OVERLAY_MAX = 1020

  it('toast / loading / message / notify 都压得住页面浮层', () => {
    expect(OVERLAY_Z_INDEX.toast).toBeGreaterThan(PAGE_OVERLAY_MAX)
    expect(OVERLAY_Z_INDEX.loading).toBeGreaterThan(PAGE_OVERLAY_MAX)
    expect(OVERLAY_Z_INDEX.messageBox).toBeGreaterThan(PAGE_OVERLAY_MAX)
    expect(OVERLAY_Z_INDEX.notify).toBeGreaterThan(PAGE_OVERLAY_MAX)
  })

  it('全局 toast 组件显式下发 z-index（不再用库默认的 100）', () => {
    const component = source('src/components/GlobalToast.vue')

    expect(component).toMatch(/:z-index="OVERLAY_Z_INDEX\.toast"/)
    // 库默认 100 远低于页面浮层：必须覆盖每一项提示的层级
    expect(component).toMatch(/zIndex:\s*OVERLAY_Z_INDEX\.toast/)
  })

  it('全局 loading 组件同样显式下发 z-index', () => {
    const component = source('src/components/GlobalLoading.vue')

    expect(component).toMatch(/:z-index="OVERLAY_Z_INDEX\.loading"/)
    expect(component).toMatch(/zIndex:\s*OVERLAY_Z_INDEX\.loading/)
  })

  it('全局消息框组件下发 z-index，并且不再被页面元素压住', () => {
    const component = source('src/components/GlobalMessage.vue')

    expect(component).toContain('OVERLAY_Z_INDEX.messageBox')
    // 传参形式可以是 `zIndex: x` 或 `option.zIndex = x`，只要求来自常量
    expect(component).toMatch(/zIndex\s*[:=]\s*OVERLAY_Z_INDEX\.messageBox/)
  })

  it('全局消息 composable 的默认层级同步抬高（避免直接调 useGlobalMessage 的入口漏掉）', () => {
    const composable = source('src/composables/useGlobalMessage.ts')

    expect(composable).toContain('OVERLAY_Z_INDEX.messageBox')
  })

  it('根组件的 wd-notify 也显式下发层级', () => {
    const app = source('src/App.ku.vue')

    expect(app).toContain('OVERLAY_Z_INDEX.notify')
  })

  it('绑定页弹层引用统一常量，而不是继续散落裸数字', () => {
    const page = source(BIND_PAGE)
    const zIndexes = zIndexesIn(page)

    // 弹层两项必须来自常量：900/901 的裸值不再出现在本页
    expect(zIndexes).not.toContain(900)
    expect(zIndexes).not.toContain(901)
    expect(page).toContain('OVERLAY_Z_INDEX.sheetMask')
    expect(page).toContain('OVERLAY_Z_INDEX.sheet')
  })
})
