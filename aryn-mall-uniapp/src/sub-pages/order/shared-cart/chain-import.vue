<script setup lang="ts">
import type {
  SharedCartImport,
  SharedCartImportActionPayload,
  SharedCartImportCandidate,
  SharedCartImportRow,
} from '@/api/order/sharedCart'
/**
 * 微信群接龙粘贴导入（「粘贴 → 报告」两步）。
 *
 * 船员报货接龙发在微信群里，很多人从没登录过小程序甚至不是系统用户——
 * 解析出的人名只作**归属标签**（配送贴标签、按人分装用）：能对上成员
 * 自填姓名的挂到真实成员名下，对不上的原样保留在明细上。
 *
 * 与 Excel 导入（import.vue）共用服务端的报告/确认接口，差异只有两点：
 * 1. 输入是整段粘贴的自由文本，解析规则在服务端（ChainOrderTextParser）；
 * 2. 每行多一个「归属人」，可在报告页逐行修正（职务称呼 → 真实姓名）。
 *
 * 数据与校验全部以服务端返回为准：客户端只回传「行号 → 处置动作 + 人名修正」，
 * 不自行判断某行能否入单。
 *
 * 报告页的交互取舍（2026-10-11 重做）：
 * - **处置行数据以紧凑表格呈现**：真正要核对的是「人名 / 品名 / 数量」三列，
 *   一屏看的人越多，越能发现解析串行的错误；原来一卡一行把三行信息摊成三屏。
 * - **归属人就地编辑**：一行报货的人通常连续若干行，输入框按行记忆（进入编辑即
 *   预填当前人名），逐行清空会把它变成「每行都要重打一遍」。
 * - **模糊候选直接铺开**：服务端已打分给出候选，不必先手点搜索；
 *   连续多个候选各占一行列表反而更慢，改为候选项一行内横排。
 */
import { onLoad, onUnload } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { alovaInstance } from '@/api/core/instance'
import {
  confirmSharedCartImport,
  getSharedCartImport,
  listSharedCartImports,
  previewChainImport,
} from '@/api/order/sharedCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useAuthStore } from '@/store/authStore'

definePage({
  name: 'shared-cart-chain-import',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '粘贴接龙报货',
  },
})

const authStore = useAuthStore()
const { t } = useI18n()

const cartId = ref('')
/** 1粘贴 2报告 */
const step = ref<1 | 2>(1)
const parsing = ref(false)
const confirming = ref(false)
const chainText = ref('')
const report = ref<SharedCartImport | null>(null)
/** 行号 → 处置动作（只传动作，行内容一律以服务端已落库的解析行为准） */
const actions = reactive<Record<number, SharedCartImportActionPayload>>({})
/** 行号 → 归属人名修正（空 = 沿用解析结果） */
const personNames = reactive<Record<number, string>>({})
/** 行号 → 人工补选关键字 */
const pickKeywords = reactive<Record<number, string>>({})
/** 行号 → 人工补选中的候选 */
const pickCandidates = reactive<Record<number, SharedCartImportCandidate[]>>({})
const picking = reactive<Record<number, boolean>>({})
/** 行号 → 补选采用的商品名：行移入「已处置」组后仍要能看到选了什么 */
const pickNames = reactive<Record<number, string>>({})
/** 「仅看待处置」筛选：默认开，报告干净时用户先看到的不是一屏绿标 */
const onlyIssue = ref(true)

const rows = computed(() => report.value?.rows ?? [])

/** AI 解析的阶段提示：不回传真实进度（解析是同步完成的），只做活动指示 */
const AI_PHASE_KEYS = ['phaseReading', 'phaseRecognizing', 'phaseMatching', 'phaseGenerating']
const aiPhaseIndex = ref(0)
let aiPhaseTimer: ReturnType<typeof setInterval> | null = null
const aiPhase = computed(() =>
  t(`sharedCart.chainImport.${AI_PHASE_KEYS[aiPhaseIndex.value % AI_PHASE_KEYS.length]}`),
)

function startAiPhase() {
  stopAiPhase()
  aiPhaseIndex.value = 0
  aiPhaseTimer = setInterval(() => {
    aiPhaseIndex.value += 1
  }, 1400)
}

function stopAiPhase() {
  if (aiPhaseTimer) {
    clearInterval(aiPhaseTimer)
    aiPhaseTimer = null
  }
}

onUnload(stopAiPhase)
/** 需要用户处置的行：匹配成功、已下架的都不需要逐行确认 */
const actionableRows = computed(() =>
  rows.value.filter(row => row.resultType !== 'OK' && row.resultType !== 'OFF_SHELF'),
)
/** 无需处置的行（匹配成功/已下架），单独一组展示给用户核对 */
const passiveRows = computed(() =>
  rows.value.filter(row => row.resultType === 'OK' || row.resultType === 'OFF_SHELF'),
)

/** 报告页实际渲染的处置行（受「仅看待处置」开关影响） */
const visibleActionableRows = computed(() => {
  if (!onlyIssue.value)
    return actionableRows.value
  return actionableRows.value.filter(row => !actions[row.rowNo])
})

/** 已处置行数：跳过 / 补选 / 调数量都算，用于报告头部的进度口径 */
const handledCount = computed(
  () => actionableRows.value.filter(row => actions[row.rowNo]).length,
)

const remainingCount = computed(() => actionableRows.value.length - handledCount.value)

/** 匹配成功的行数（服务端口径优先，缺失时按行实时算） */
const matchedCount = computed(
  () => report.value?.matchedRows ?? rows.value.filter(row => row.resultType === 'OK').length,
)

/** 已处置且会并入的行（补选/调数量；跳过的不算）：集中展示，避免「选完就不见了」的观感 */
const resolvedRows = computed(() =>
  actionableRows.value.filter((row) => {
    const action = actions[row.rowNo]
    return !!action && action.action !== 'SKIP'
  }),
)

/** 跳过的行数：确认并入时服务端自动排除，这里只做计数提示 */
const skippedCount = computed(
  () => actionableRows.value.filter(row => actions[row.rowNo]?.action === 'SKIP').length,
)

/**
 * 底部「将并入」计数：匹配成功行按解析结果并入，未匹配行补选/调整后并入；
 * 未处置与跳过的行由服务端在确认时自动排除，不计入。
 */
const mergeCount = computed(() => matchedCount.value + resolvedRows.value.length)

/** 解析出的项数：报告口径兜底 totalRows */
const totalCount = computed(() => report.value?.totalRows || rows.value.length)

/** 命中率：分母是「能命中的行」（总数 − 未匹配），回答「这份接龙有多少可以直接入单」 */
const matchRate = computed(() => {
  const hitBase = matchedCount.value + (report.value?.specChangedRows || 0)
  const total = totalCount.value
  if (!total)
    return 0
  return Math.round((hitBase / total) * 100)
})

/** 报货人数：按解析出的人名去重 */
const personCount = computed(() => {
  const counted = rows.value.length > 0
    ? rows.value.map(row => (personNames[row.rowNo] || row.personName || '').trim())
    : []
  return new Set(counted.filter(Boolean)).size
})

/** 报告计数卡：按「待人工 / 不可入单」分组，空白态也占位，不做 5 格等宽硬撑 */
const reportCells = computed(() => [
  { key: 'unmatched', label: t('sharedCart.result.unmatched'), value: report.value?.unmatchedRows || 0, tone: 'danger' },
  { key: 'specChanged', label: t('sharedCart.result.specPending'), value: report.value?.specChangedRows || 0, tone: 'warn' },
  { key: 'overStock', label: t('sharedCart.result.overStock'), value: report.value?.overStockRows || 0, tone: 'warn' },
  { key: 'invalid', label: t('sharedCart.result.invalidQty'), value: report.value?.invalidRows || 0, tone: 'warn' },
  { key: 'offShelf', label: t('sharedCart.result.offShelf'), value: report.value?.offShelfRows || 0, tone: 'muted' },
])

const RESULT_LABELS = computed<Record<string, string>>(() => ({
  OK: t('sharedCart.result.matched'),
  UNMATCHED: t('sharedCart.result.unmatched'),
  SPEC_CHANGED: t('sharedCart.result.specPending'),
  OVER_STOCK: t('sharedCart.result.overStock'),
  INVALID_QTY: t('sharedCart.result.invalidQty'),
  OFF_SHELF: t('sharedCart.result.offShelf'),
}))

/** 徽标配色：语义色只留「要人管 / 不用管」两级，避免一屏五种颜色 */
const RESULT_THEME: Record<string, string> = {
  OK: 'tag--ok',
  UNMATCHED: 'tag--danger',
  SPEC_CHANGED: 'tag--warn',
  OVER_STOCK: 'tag--warn',
  INVALID_QTY: 'tag--warn',
  OFF_SHELF: 'tag--muted',
}

/** 结果说明里「已匹配」这类无信息量的话不占行 */
function issueText(row: SharedCartImportRow) {
  const message = (row.resultMessage || '').trim()
  if (!message || message === '已匹配' || message === '匹配成功')
    return ''
  // 未匹配行的静态指引（「请人工补选」）由候选区/缺品空态承接，只保留解析疑点
  if (row.resultType === 'UNMATCHED') {
    const doubtAt = message.indexOf('解析疑点')
    return doubtAt >= 0 ? message.slice(doubtAt) : ''
  }
  return message
}

/** 行是否已有可点选的候选（服务端召回或人工搜索结果） */
function hasRowCandidates(row: SharedCartImportRow) {
  return !!(row.candidates?.length || pickCandidates[row.rowNo]?.length)
}

/** 商城确实没有的商品：未匹配、服务端（含 AI 别名召回）没给候选、且用户尚未处置 */
const missingRows = computed(() =>
  rows.value.filter(row =>
    row.resultType === 'UNMATCHED' && !(row.candidates?.length) && !actions[row.rowNo]),
)

/** 处置行展示的品名：解析原文优先，其次匹配结果 */
function rowTitle(row: SharedCartImportRow) {
  return row.rawName || row.matchedName || t('sharedCart.chainImport.rowNo', { row: row.rowNo })
}

/** 处置行的商品副标题：解析成功的行带出命中的规格 */
function rowMatchedNote(row: SharedCartImportRow) {
  if (!row.matchedName)
    return ''
  return row.matchedSpec ? `${row.matchedName} · ${row.matchedSpec}` : row.matchedName
}

function rowQuantity(row: SharedCartImportRow) {
  const quantity = row.quantity ?? row.rawQuantity
  if (quantity == null)
    return '—'
  return row.rawUnit ? `${quantity}${row.rawUnit}` : String(quantity)
}

/** 已处置行展示的商品名：补选行显示选中的商品，其余显示解析结果 */
function resolvedRowName(row: SharedCartImportRow) {
  const action = actions[row.rowNo]
  if (action?.action === 'REPLACE_SKU') {
    return pickNames[row.rowNo]
      || row.candidates?.find(candidate => candidate.skuId === action.skuId)?.name
      || row.rawName
      || t('sharedCart.chainImport.rowNo', { row: row.rowNo })
  }
  return row.rawName || row.matchedName || t('sharedCart.chainImport.rowNo', { row: row.rowNo })
}

/** 已处置行展示的数量：调数量行显示调整后的值 */
function resolvedRowQty(row: SharedCartImportRow) {
  const action = actions[row.rowNo]
  if (action?.action === 'ADJUST_QTY' && action.quantity != null)
    return row.rawUnit ? `${action.quantity}${row.rawUnit}` : String(action.quantity)
  return rowQuantity(row)
}

function MONEY(value?: number | null) {
  return value == null ? '' : `¥${Number(value).toFixed(2)}`
}

onLoad((options) => {
  cartId.value = options?.cartId ?? ''
  if (!authStore.isLoggedIn)
    uni.navigateTo({ url: '/pages/login/index' })
})

/** 读取剪贴板：接龙原文在微信里复制后，回到小程序一键填入 */
function handlePasteFromClipboard() {
  uni.getClipboardData({
    success: ({ data }) => {
      if (!data?.trim()) {
        uni.showToast({ title: t('sharedCart.chainImport.clipboardEmpty'), icon: 'none' })
        return
      }
      chainText.value = data
      uni.showToast({ title: t('sharedCart.chainImport.pasted'), icon: 'none' })
    },
    fail: () => {
      uni.showToast({ title: t('sharedCart.chainImport.clipboardFailed'), icon: 'none' })
    },
  })
}

function clearText() {
  chainText.value = ''
}

async function parseText() {
  // 防连点：解析中再点会发起第二次请求，两份报告互相覆盖
  if (parsing.value)
    return
  if (!chainText.value.trim()) {
    uni.showToast({ title: t('sharedCart.chainImport.pasteRequired'), icon: 'none' })
    return
  }
  if (!cartId.value)
    return
  parsing.value = true
  startAiPhase()
  try {
    report.value = await previewChainImport(cartId.value, chainText.value)
    // 重置上一份报告留下的处置状态，避免行号串台
    Object.keys(actions).forEach(key => delete actions[Number(key)])
    Object.keys(personNames).forEach(key => delete personNames[Number(key)])
    Object.keys(pickCandidates).forEach(key => delete pickCandidates[Number(key)])
    Object.keys(pickNames).forEach(key => delete pickNames[Number(key)])
    // 报告干净时默认展示全部，不用用户再关一次筛选
    onlyIssue.value = actionableRows.value.length > 0
    step.value = 2
  }
  catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : t('sharedCart.chainImport.parseFailed'),
      icon: 'none',
    })
  }
  finally {
    parsing.value = false
    stopAiPhase()
  }
}

/** 组装单行处置：动作 + 人名修正（有改动才带上） */
function buildPayload(row: SharedCartImportRow): SharedCartImportActionPayload | null {
  const action = actions[row.rowNo]
  const person = (personNames[row.rowNo] || '').trim()
  const personChanged = person.length > 0 && person !== (row.personName || '')
  if (action) {
    return personChanged ? { ...action, personName: person } : action
  }
  // 只改人名的匹配成功行：按服务端已落的匹配结果并入（ACCEPT_SPEC 即沿用解析）
  if (personChanged && row.resultType === 'OK') {
    return { rowNo: row.rowNo, action: 'ACCEPT_SPEC', personName: person }
  }
  return null
}

/** 超库存/数量异常：按服务端建议值调整 */
function adjustQuantity(row: SharedCartImportRow) {
  const quantity = row.suggestedQuantity
  if (!quantity) {
    uni.showToast({ title: t('sharedCart.chainImport.adjustManually'), icon: 'none' })
    return
  }
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'ADJUST_QTY', quantity }
  uni.showToast({ title: t('sharedCart.chainImport.adjustedTo', { qty: quantity }), icon: 'none' })
}

/** 未匹配：人工补选，走既有商品搜索接口 */
async function searchCandidates(row: SharedCartImportRow, keyword?: string) {
  const word = (keyword ?? pickKeywords[row.rowNo] ?? row.rawName ?? '').trim()
  if (!word) {
    uni.showToast({ title: t('sharedCart.chainImport.keywordRequired'), icon: 'none' })
    return
  }
  pickKeywords[row.rowNo] = word
  picking[row.rowNo] = true
  try {
    const page: any = await alovaInstance.Get('/product/app/goodsspu/search', {
      params: { scene: '2', keyword: word, current: 1, size: 20 },
    })
    pickCandidates[row.rowNo] = (page?.records ?? []).map((record: any) => ({
      spuId: record.spuId,
      skuId: record.skuId,
      name: record.name,
      spec: record.specsInfo,
      salesPrice: record.salesPrice,
      stock: record.stock,
      skuStatus: '0',
      spuStatus: record.status,
    }))
    if (pickCandidates[row.rowNo].length === 0)
      uni.showToast({ title: t('sharedCart.chainImport.noResult'), icon: 'none' })
  }
  catch {
    uni.showToast({ title: t('sharedCart.chainImport.searchFailed'), icon: 'none' })
  }
  finally {
    picking[row.rowNo] = false
  }
}

/** 选中候选：记下 REPLACE_SKU 动作 */
function chooseCandidate(row: SharedCartImportRow, candidate: SharedCartImportCandidate) {
  if (!candidate.skuId)
    return
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'REPLACE_SKU', skuId: candidate.skuId }
  pickNames[row.rowNo] = candidate.name || ''
  pickCandidates[row.rowNo] = []
  uni.showToast({ title: t('sharedCart.chainImport.productSelected'), icon: 'none' })
}

/** 跳过某行 */
/** 有 AI 推荐且尚未处置的行数：0 时隐藏「采用全部 AI 推荐」 */
const aiPickPendingCount = computed(
  () => rows.value.filter(row => row.aiPickedSkuId && !actions[row.rowNo]).length,
)

/** 一键采用全部 AI 推荐：等价于逐行点选第一个候选，仍可逐行撤销 */
function adoptAllAiPicks() {
  for (const row of rows.value) {
    if (row.aiPickedSkuId && !actions[row.rowNo]) {
      actions[row.rowNo] = { rowNo: row.rowNo, action: 'REPLACE_SKU', skuId: row.aiPickedSkuId }
      pickNames[row.rowNo] = row.candidates?.find(candidate => candidate.skuId === row.aiPickedSkuId)?.name
        || row.rawName
        || ''
    }
  }
  uni.showToast({ title: t('sharedCart.chainImport.aiPicked'), icon: 'none' })
}

function skipRow(row: SharedCartImportRow) {
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'SKIP' }
}

/** 撤销某行的处置，回到待处理 */
function undoRow(row: SharedCartImportRow) {
  delete actions[row.rowNo]
  delete pickNames[row.rowNo]
}

/** 处置后是否还留在「待处置」里：原类型未匹配、补选后仍算未匹配，因此顺延仍可见 */
function isHandled(row: SharedCartImportRow) {
  return !!actions[row.rowNo]
}

/** 行处置状态文案 */
function actionLabel(row: SharedCartImportRow) {
  const action = actions[row.rowNo]
  if (!action)
    return ''
  switch (action.action) {
    case 'ADJUST_QTY':
      return t('sharedCart.chainImport.actionAdjusted', { qty: action.quantity })
    case 'REPLACE_SKU':
      return t('sharedCart.chainImport.actionReplaced')
    case 'SKIP':
      return t('sharedCart.chainImport.actionSkipped')
    default:
      return t('sharedCart.chainImport.actionAdopted')
  }
}

/** 处置后的动作条：已处置行只留「撤销」与状态，避免重复点 */
function rowDoneLabel(row: SharedCartImportRow) {
  return actionLabel(row) || t('sharedCart.chainImport.processed')
}

async function handleConfirm() {
  if (!report.value)
    return
  confirming.value = true
  try {
    const payload = rows.value.map(buildPayload).filter(item => item !== null)
    report.value = await confirmSharedCartImport(cartId.value, report.value.importId, payload)
    uni.showToast({
      title: report.value.importedRows
        ? t('sharedCart.chainImport.mergedCount', { count: report.value.importedRows })
        : t('sharedCart.chainImport.processed'),
      icon: 'none',
    })
    if (report.value.importedRows) {
      const pages = getCurrentPages()
      const previous: any = pages[pages.length - 2]
      if (previous?.$vm?.fetchDetail)
        previous.$vm.fetchDetail()
      const currentPage = pages[pages.length - 1]
      setTimeout(() => {
        const stack = getCurrentPages()
        if (stack[stack.length - 1] !== currentPage || stack.length <= 1)
          return
        uni.navigateBack()
      }, 800)
    }
  }
  catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : t('sharedCart.chainImport.mergeFailed'),
      icon: 'none',
    })
  }
  finally {
    confirming.value = false
  }
}

/** 重新粘贴（回到粘贴步骤） */
function backToPaste() {
  step.value = 1
}

async function reopenLatestImport() {
  uni.showLoading({ title: t('common.loading') })
  try {
    const list = await listSharedCartImports(cartId.value)
    const latest = (list ?? []).find(item => item.status === '1')
    if (!latest) {
      uni.showToast({ title: t('sharedCart.chainImport.noPendingImport'), icon: 'none' })
      return
    }
    report.value = await getSharedCartImport(cartId.value, latest.importId)
    onlyIssue.value = actionableRows.value.length > 0
    step.value = 2
  }
  catch {
    uni.showToast({ title: t('sharedCart.chainImport.loadFailed'), icon: 'none' })
  }
  finally {
    uni.hideLoading()
  }
}

/** 继续上次导入后直接停在报告页时，把报告置顶（有未处理项时提示还剩几行） */
const remainedText = computed(() =>
  remainingCount.value > 0
    ? t('sharedCart.chainImport.remainingText', { count: remainingCount.value })
    : '',
)
</script>

<template>
  <view class="page">
    <hrNavbar :title="t('sharedCart.chainImport.navTitle')" />

    <!-- 步骤 1：粘贴接龙 -->
    <template v-if="step === 1">
      <view class="paste-head">
        <view class="paste-head__title">
          {{ t('sharedCart.chainImport.pasteTitle') }}
        </view>
        <view class="paste-head__desc">
          {{ t('sharedCart.chainImport.pasteDesc') }}
        </view>
      </view>

      <view class="card paste-card">
        <view class="paste-box">
          <textarea
            v-model="chainText"
            class="paste-box__input"
            :maxlength="50000"
            :show-confirm-bar="false"
            auto-height
            :placeholder="t('sharedCart.chainImport.pastePlaceholder')"
          />
          <view v-if="chainText" class="paste-box__clear" @tap="clearText">
            {{ t('sharedCart.chainImport.clear') }}
          </view>
        </view>

        <!-- 剪贴板是这条链路最常见的入口：先给主按钮，不打字也能走通 -->
        <view class="paste-actions">
          <view class="btn btn--ghost" @tap="handlePasteFromClipboard">
            <text class="i-carbon:copy mr-8rpx flex-none text-28rpx" />
            {{ t('sharedCart.chainImport.readClipboard') }}
          </view>
          <view
            class="btn btn--primary"
            :class="{ 'btn--disabled': parsing || !chainText.trim() }"
            @tap="parseText"
          >
            <text v-if="!parsing" class="i-carbon:magic-wand-filled mr-8rpx flex-none text-28rpx" />
            {{ parsing ? t('sharedCart.chainImport.parsing') : t('sharedCart.chainImport.startParse') }}
          </view>
        </view>
      </view>

      <view class="card tips-card">
        <view class="tips-card__head">
          {{ t('sharedCart.chainImport.rulesTitle') }}
        </view>
        <view class="tips-row">
          <text class="tips-row__num">
            1
          </text>
          <text class="tips-row__text">
            {{ t('sharedCart.chainImport.ruleOne') }}
          </text>
        </view>
        <view class="tips-row">
          <text class="tips-row__num">
            2
          </text>
          <text class="tips-row__text">
            {{ t('sharedCart.chainImport.ruleTwo') }}
          </text>
        </view>
        <view class="tips-row">
          <text class="tips-row__num">
            3
          </text>
          <text class="tips-row__text">
            {{ t('sharedCart.chainImport.ruleThree') }}
          </text>
        </view>
      </view>

      <view class="paste-foot" @tap="reopenLatestImport">
        <text class="i-carbon:redo mr-8rpx text-24rpx" />
        {{ t('sharedCart.chainImport.continueLast') }}
      </view>
    </template>

    <!-- 步骤 2：解析报告 -->
    <template v-else>
      <!-- 报告摘要：命中率 + 计数卡，回答「这份接龙能不能直接用」 -->
      <view class="card summary">
        <view class="summary__head">
          <view class="summary__head-left">
            <view class="summary__title">
              {{ t('sharedCart.chainImport.reportTitle') }}
            </view>
            <view v-if="report?.parserSource === 'AI'" class="summary__ai">
              <text class="i-carbon:magic-wand-filled text-22rpx" />
              {{ t('sharedCart.chainImport.aiParsed') }}
            </view>
          </view>
          <view class="summary__meta">
            {{ t('sharedCart.chainImport.reportMeta', { items: totalCount, people: personCount }) }}
          </view>
        </view>

        <view class="summary__rate">
          <view class="summary__bar">
            <view class="summary__bar-fill" :style="`width:${Math.min(matchRate, 100)}%`" />
          </view>
          <view class="summary__rate-text">
            <text class="summary__rate-value">
              {{ matchedCount }}/{{ totalCount }}
            </text>
            <text class="summary__rate-label">
              {{ t('sharedCart.chainImport.matchedRate', { rate: matchRate }) }}
            </text>
          </view>
        </view>

        <view class="summary__cells">
          <view
            v-for="cell in reportCells"
            :key="cell.key"
            class="summary__cell"
            :class="`summary__cell--${cell.tone}`"
          >
            <view class="summary__cell-value">
              {{ cell.value }}
            </view>
            <view class="summary__cell-label">
              {{ cell.label }}
            </view>
          </view>
        </view>
      </view>

      <!-- 缺品汇总：商城确实没有的商品一眼看全，别让用户逐行翻才发现 -->
      <view v-if="missingRows.length" class="card missing">
        <view class="missing__head">
          <text class="i-carbon:warning-alt missing__icon" />
          <text class="missing__title">
            {{ t('sharedCart.chainImport.missingTitle', { count: missingRows.length }) }}
          </text>
        </view>
        <view class="missing__names">
          <text
            v-for="row in missingRows.slice(0, 8)"
            :key="`miss-${row.rowNo}`"
            class="missing__name"
          >
            {{ rowTitle(row) }}
          </text>
          <text v-if="missingRows.length > 8" class="missing__name missing__name--more">
            {{ t('sharedCart.chainImport.missingMore', { count: missingRows.length }) }}
          </text>
        </view>
        <view class="missing__hint">
          {{ t('sharedCart.chainImport.missingHint') }}
        </view>
      </view>

      <!-- 工具条：待处理进度 + AI 一键采用 + 仅看待处置 -->
      <view class="toolbar">
        <view
          v-if="aiPickPendingCount > 0"
          class="toolbar__ai"
          @tap="adoptAllAiPicks"
        >
          <text class="i-carbon:magic-wand-filled text-24rpx" />
          {{ t('sharedCart.chainImport.adoptAllAi', { count: aiPickPendingCount }) }}
        </view>
        <view class="toolbar__progress">
          <template v-if="remainingCount > 0">
            {{ t('sharedCart.chainImport.progressPrefix') }} <text class="toolbar__progress-num">
              {{ remainingCount }}
            </text> {{ t('sharedCart.chainImport.progressSuffix') }}
          </template>
          <template v-else-if="actionableRows.length">
            {{ t('sharedCart.chainImport.progressAllDone') }}
          </template>
          <template v-else>
            {{ t('sharedCart.chainImport.progressNone') }}
          </template>
          <text v-if="skippedCount > 0" class="toolbar__progress-skip">
            {{ t('sharedCart.chainImport.progressSkipped', { count: skippedCount }) }}
          </text>
        </view>
        <view
          v-if="actionableRows.length && (remainingCount + handledCount) > 0"
          class="toolbar__switch"
          :class="{ 'toolbar__switch--on': onlyIssue }"
          @tap="onlyIssue = !onlyIssue"
        >
          <text class="i-carbon:filter text-24rpx" />
          {{ t('sharedCart.chainImport.onlyPending') }}
        </view>
      </view>

      <!-- 待处置行：紧凑表格，一行一商品 -->
      <view v-if="visibleActionableRows.length" class="card">
        <view
          v-for="row in visibleActionableRows"
          :key="row.rowNo"
          class="issue"
          :class="{ 'issue--done': isHandled(row) }"
        >
          <view class="issue__head">
            <text class="tag" :class="RESULT_THEME[row.resultType]">
              {{ RESULT_LABELS[row.resultType] }}
            </text>
            <text class="issue__name">
              {{ rowTitle(row) }}
            </text>
            <text class="issue__qty">
              × {{ rowQuantity(row) }}
            </text>
          </view>

          <view v-if="issueText(row)" class="issue__issue">
            <text class="i-carbon:warning-alt mr-6rpx flex-none text-22rpx" />
            {{ issueText(row) }}
          </view>
          <view v-else-if="rowMatchedNote(row)" class="issue__match">
            {{ t('sharedCart.chainImport.parsedAs', { name: rowMatchedNote(row) }) }}
          </view>

          <!-- 归属人：一行连续多行属同一人，点开预填、可改可清 -->
          <view class="issue__owner">
            <text class="issue__owner-label">
              {{ t('sharedCart.chainImport.ownerLabel') }}
            </text>
            <input
              v-model="personNames[row.rowNo]"
              class="issue__owner-input"
              :class="{ 'issue__owner-input--empty': !(personNames[row.rowNo] || row.personName) }"
              :placeholder="row.personName || t('sharedCart.chainImport.ownerUnknown')"
              placeholder-class="ph"
            >
            <text v-if="row.personName" class="issue__owner-origin">
              {{ t('sharedCart.chainImport.ownerOriginal', { name: row.personName }) }}
            </text>
          </view>

          <!-- 服务端给出的模糊候选：直接铺开，点选即采用 -->
          <view
            v-if="row.candidates?.length && !isHandled(row)"
            class="issue__suggest"
          >
            <view class="issue__suggest-head">
              {{ t('sharedCart.chainImport.suggestTitle') }}
            </view>
            <view class="issue__suggest-list">
              <view
                v-for="(candidate, index) in row.candidates"
                :key="`sug-${row.rowNo}-${candidate.skuId || index}`"
                class="chip"
                :class="{ 'chip--ai': candidate.skuId === row.aiPickedSkuId }"
                @tap="chooseCandidate(row, candidate)"
              >
                <text v-if="candidate.skuId === row.aiPickedSkuId" class="chip__ai">
                  {{ t('sharedCart.chainImport.aiRecommend') }}
                </text>
                <text class="chip__name">
                  {{ candidate.name }}
                </text>
                <text v-if="candidate.spec" class="chip__spec">
                  {{ candidate.spec }}
                </text>
                <text class="chip__meta">
                  {{ t('sharedCart.chainImport.stock', { count: candidate.stock ?? '—' }) }}
                  <text v-if="candidate.salesPrice != null">
                    · {{ MONEY(candidate.salesPrice) }}
                  </text>
                </text>
              </view>
            </view>
          </view>

          <!-- 缺品空态：别名召回后仍无候选，明确告诉用户「商城没有」而不是笼统的未匹配 -->
          <view
            v-else-if="row.resultType === 'UNMATCHED' && !hasRowCandidates(row) && !isHandled(row)"
            class="issue__missing"
          >
            <view class="issue__missing-head">
              <text class="i-carbon:warning-alt text-24rpx" />
              {{ t('sharedCart.chainImport.missingRowTitle', { name: rowTitle(row) }) }}
            </view>
            <view class="issue__missing-hint">
              {{ t('sharedCart.chainImport.missingRowHint') }}
            </view>
          </view>

          <!-- 未处置：动作条 -->
          <view v-if="!isHandled(row)" class="issue__actions">
            <text
              v-if="row.resultType === 'OVER_STOCK' || row.resultType === 'INVALID_QTY'"
              class="link link--warn"
              @tap="adjustQuantity(row)"
            >
              {{ t('sharedCart.chainImport.useSuggested', { qty: row.suggestedQuantity ?? '—' }) }}
            </text>
            <text class="link link--muted" @tap="skipRow(row)">
              {{ t('sharedCart.chainImport.skipRow') }}
            </text>
            <view class="issue__search">
              <input
                v-model="pickKeywords[row.rowNo]"
                class="issue__search-input"
                :placeholder="row.rawName
                  ? t('sharedCart.chainImport.searchNamed', { name: row.rawName })
                  : t('sharedCart.chainImport.searchPlaceholder')"
                placeholder-class="ph"
                @confirm="searchCandidates(row)"
              >
              <text class="issue__search-btn" @tap="searchCandidates(row)">
                {{ picking[row.rowNo] ? t('sharedCart.chainImport.searching') : t('sharedCart.chainImport.search') }}
              </text>
            </view>
          </view>

          <!-- 已处置：状态 + 撤销 -->
          <view v-else class="issue__done">
            <text class="issue__done-state">
              <text class="i-carbon:checkmark mr-6rpx text-24rpx" />{{ rowDoneLabel(row) }}
            </text>
            <text class="link link--muted" @tap="undoRow(row)">
              {{ t('sharedCart.chainImport.undo') }}
            </text>
          </view>

          <!-- 人工补选结果：选中即回填为已处置，这里只在搜索态出现 -->
          <view v-if="pickCandidates[row.rowNo]?.length" class="issue__suggest">
            <view class="issue__suggest-head">
              {{ t('sharedCart.chainImport.searchResults') }}
            </view>
            <view class="issue__suggest-list">
              <view
                v-for="(candidate, index) in pickCandidates[row.rowNo]"
                :key="`pick-${row.rowNo}-${candidate.skuId || index}`"
                class="chip"
                @tap="chooseCandidate(row, candidate)"
              >
                <text class="chip__name">
                  {{ candidate.name }}
                </text>
                <text v-if="candidate.spec" class="chip__spec">
                  {{ candidate.spec }}
                </text>
                <text class="chip__meta">
                  {{ t('sharedCart.chainImport.stock', { count: candidate.stock ?? '—' }) }}
                  <text v-if="candidate.salesPrice != null">
                    · {{ MONEY(candidate.salesPrice) }}
                  </text>
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-else-if="actionableRows.length" class="empty">
        {{ t('sharedCart.chainImport.allHandled') }}
      </view>

      <!-- 已处置行：补选/调整后会随确认并入，集中展示结果——不能让用户选完就「消失」 -->
      <view v-if="resolvedRows.length" class="card passive">
        <view class="passive__head">
          <text class="passive__title">
            {{ t('sharedCart.chainImport.resolvedTitle') }}
          </text>
          <text class="passive__count">
            {{ t('sharedCart.chainImport.resolvedCount', { count: resolvedRows.length }) }}
          </text>
        </view>
        <view
          v-for="row in resolvedRows"
          :key="`resolved-${row.rowNo}`"
          class="passive__row"
        >
          <view class="flex items-center">
            <text class="tag tag--ok">
              {{ actionLabel(row) || t('sharedCart.chainImport.processed') }}
            </text>
            <text class="passive__name">
              {{ resolvedRowName(row) }}
            </text>
            <text class="passive__qty">
              × {{ resolvedRowQty(row) }}
            </text>
          </view>
          <view class="passive__foot">
            <input
              v-model="personNames[row.rowNo]"
              class="passive__owner"
              :placeholder="row.personName || t('sharedCart.chainImport.ownerPlaceholder')"
              placeholder-class="ph"
            >
            <text v-if="row.personName" class="passive__note">
              {{ t('sharedCart.chainImport.ownerOriginal', { name: row.personName }) }}
            </text>
            <text class="resolved__undo" @tap="undoRow(row)">
              {{ t('sharedCart.chainImport.undo') }}
            </text>
          </view>
        </view>
      </view>

      <!-- 匹配成功/已下架：只读核对，可改归属人 -->
      <view v-if="passiveRows.length" class="card passive">
        <view class="passive__head">
          <text class="passive__title">
            {{ t('sharedCart.chainImport.passiveTitle') }}
          </text>
          <text class="passive__count">
            {{ t('sharedCart.common.itemsCount', { count: passiveRows.length }) }}
          </text>
        </view>
        <view
          v-for="row in passiveRows"
          :key="`done-${row.rowNo}`"
          class="passive__row"
        >
          <view class="flex items-center">
            <text class="tag" :class="RESULT_THEME[row.resultType]">
              {{ RESULT_LABELS[row.resultType] }}
            </text>
            <text class="passive__name">
              {{ row.matchedName || row.rawName || t('sharedCart.chainImport.rowNo', { row: row.rowNo }) }}
            </text>
            <text class="passive__qty">
              {{ row.resultType === 'OK' ? `× ${rowQuantity(row)}` : '' }}
            </text>
          </view>
          <view class="passive__foot">
            <input
              v-model="personNames[row.rowNo]"
              class="passive__owner"
              :placeholder="row.personName || t('sharedCart.chainImport.ownerPlaceholder')"
              placeholder-class="ph"
            >
            <text v-if="issueText(row)" class="passive__note">
              {{ issueText(row) }}
            </text>
          </view>
        </view>
      </view>

      <!-- 底部固定条：占位与明细页同款，避免最后一张卡片被压住 -->
      <view class="footer-spacer" />
      <view class="action-bar">
        <view class="action-bar__ghost" @tap="backToPaste">
          {{ t('sharedCart.chainImport.repaste') }}
        </view>
        <view class="action-bar__info">
          <view class="action-bar__amount">
            {{ t('sharedCart.chainImport.mergeCount', { count: mergeCount }) }}
          </view>
          <view class="action-bar__label">
            {{ remainedText || t('sharedCart.chainImport.canMerge') }}
          </view>
        </view>
        <view
          class="action-bar__primary"
          :class="{ 'action-bar__primary--disabled': confirming || report?.status !== '1' }"
          @tap="handleConfirm"
        >
          {{ report?.status === '2'
            ? t('sharedCart.chainImport.merged')
            : t('sharedCart.chainImport.confirmMerge') }}
        </view>
      </view>
    </template>

    <!-- AI 解析动效：解析是同步完成的，这里只做活动指示（阶段轮播），不给假进度条 -->
    <view v-if="parsing" class="ai-mask" @tap.stop>
      <view class="ai-mask__card">
        <view class="ai-orb">
          <view class="ai-orb__ring" />
          <view class="ai-orb__ring ai-orb__ring--late" />
          <view class="ai-orb__core">
            <text class="i-carbon:magic-wand-filled" />
          </view>
        </view>
        <view class="ai-mask__title">
          {{ t('sharedCart.chainImport.aiParsingTitle') }}
        </view>
        <view class="ai-mask__phase">
          {{ aiPhase }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 40rpx;
  background: #f5f6f8;
}

.card {
  margin: 20rpx 20rpx 0;
  border-radius: 20rpx;
  background: #fff;
}

/* ---------- 步骤 1：粘贴 ---------- */

.paste-head {
  padding: 28rpx 32rpx 0;
}

.paste-head__title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2329;
}

.paste-head__desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a97a6;
}

.paste-card {
  padding: 24rpx;
}

.paste-box {
  position: relative;
  border-radius: 16rpx;
  background: #f7f8fa;
}

.paste-box__input {
  box-sizing: border-box;
  width: 100%;
  min-height: 380rpx;
  padding: 24rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #1f2329;
}

.paste-box__clear {
  position: absolute;
  right: 16rpx;
  bottom: 12rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 22rpx;
  color: #8a97a6;
}

.paste-actions {
  display: grid;
  gap: 20rpx;
  grid-template-columns: 1fr 1fr;
  margin-top: 24rpx;
}

.btn {
  display: flex;
  height: 84rpx;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.btn--ghost {
  border: 1rpx solid #e3e6eb;
  color: #303133;
}

.btn--primary {
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
  color: #fff;
}

.btn--disabled {
  opacity: 0.5;
}

.tips-card {
  padding: 28rpx 32rpx;
}

.tips-card__head {
  font-size: 26rpx;
  font-weight: 700;
  color: #1f2329;
}

.tips-row {
  display: flex;
  margin-top: 18rpx;
}

.tips-row__num {
  display: flex;
  width: 32rpx;
  height: 32rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  margin-top: 2rpx;
  border-radius: 999rpx;
  background: #eef1f5;
  font-size: 20rpx;
  font-weight: 700;
  color: #5f6b7a;
}

.tips-row__text {
  margin-left: 16rpx;
  font-size: 23rpx;
  line-height: 1.55;
  color: #6b7684;
}

.paste-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 32rpx;
  font-size: 26rpx;
  color: var(--wot-color-theme-primary, #ff4d2e);
}

/* ---------- 步骤 2：报告摘要 ---------- */

.summary {
  padding: 28rpx 28rpx 24rpx;
}

.summary__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.summary__title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
}

.summary__meta {
  font-size: 22rpx;
  color: #8a97a6;
}

.summary__rate {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
}

.summary__bar {
  height: 12rpx;
  flex: 1;
  overflow: hidden;
  border-radius: 999rpx;
  background: #eef1f5;
}

.summary__bar-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #6bcf9e, #0f9d6a);
  transition: width 0.3s;
}

.summary__rate-text {
  display: flex;
  flex: none;
  align-items: baseline;
  margin-left: 20rpx;
}

.summary__rate-value {
  font-size: 28rpx;
  font-weight: 700;
  color: #0f9d6a;
}

.summary__rate-label {
  margin-left: 8rpx;
  font-size: 20rpx;
  color: #8a97a6;
}

.summary__cells {
  display: grid;
  gap: 12rpx;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 24rpx;
}

.summary__cell {
  padding: 16rpx 8rpx;
  border-radius: 14rpx;
  background: #f7f8fa;
  text-align: center;
}

.summary__cell-value {
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1.1;
}

.summary__cell-label {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #8a97a6;
}

.summary__cell--danger .summary__cell-value {
  color: #e5484d;
}

.summary__cell--warn .summary__cell-value {
  color: #d97706;
}

.summary__cell--muted .summary__cell-value {
  color: #adb5bd;
}

/* ---------- 工具条 ---------- */

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx 4rpx;
}

.toolbar__ai {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2b2f3a, #171a21);
  box-shadow: 0 4rpx 14rpx rgb(23 26 33 / 28%);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}

.toolbar__progress {
  font-size: 24rpx;
  color: #6b7684;
}

.toolbar__progress-num {
  font-weight: 700;
  color: #d97706;
}

.toolbar__progress-skip {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: #adb5bd;
}

.toolbar__switch {
  display: flex;
  align-items: center;
  padding: 8rpx 20rpx;
  border: 1rpx solid #e3e6eb;
  border-radius: 999rpx;
  background: #fff;
  font-size: 22rpx;
  color: #8a97a6;
}

.toolbar__switch--on {
  border-color: transparent;
  background: #fff4e8;
  color: #d97706;
}

/* ---------- 待处置行 ---------- */

.issue {
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid #f2f3f5;
}

.issue:last-child {
  border-bottom: 0;
}

.issue--done {
  background: #fafbfc;
}

.issue__head {
  display: flex;
  align-items: center;
}

.tag {
  flex: none;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 700;
}

.tag--ok {
  background: #e7f7ef;
  color: #0f9d6a;
}

.tag--danger {
  background: #fdecec;
  color: #e5484d;
}

.tag--warn {
  background: #fff4e8;
  color: #d97706;
}

.tag--muted {
  background: #f1f3f5;
  color: #8a97a6;
}

.issue__name {
  display: -webkit-box;
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  overflow: hidden;
  font-size: 27rpx;
  font-weight: 600;
  color: #1f2329;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.issue__qty {
  flex: none;
  margin-left: 14rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #5f6b7a;
}

.issue__issue,
.issue__match {
  display: flex;
  align-items: flex-start;
  margin-top: 12rpx;
  font-size: 22rpx;
  line-height: 1.5;
}

.issue__issue {
  color: #d97706;
}

.issue__match {
  color: #8a97a6;
}

.issue__owner {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.issue__owner-label {
  flex: none;
  font-size: 22rpx;
  color: #8a97a6;
}

.issue__owner-input {
  width: 220rpx;
  height: 58rpx;
  margin-left: 14rpx;
  padding: 0 20rpx;
  border-radius: 12rpx;
  background: #f2f4f7;
  font-size: 24rpx;
  color: #1f2329;
}

.issue__owner-input--empty {
  background: #fdf3e7;
}

.issue__owner-origin {
  flex: 1;
  min-width: 0;
  margin-left: 12rpx;
  overflow: hidden;
  font-size: 20rpx;
  color: #b0b8c1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue__suggest {
  margin-top: 16rpx;
}

.issue__suggest-head {
  font-size: 22rpx;
  color: #8a97a6;
}

.issue__suggest-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 12rpx;
}

.chip {
  max-width: 100%;
  padding: 12rpx 18rpx;
  border: 1rpx solid #d7f0e3;
  border-radius: 14rpx;
  background: #f2fbf6;
}

.chip--ai {
  border-color: var(--wot-color-theme-primary, #ff4d2e);
  background: #fff;
  box-shadow: 0 0 0 2rpx rgb(255 77 46 / 14%);
}

.chip__ai {
  display: inline-block;
  margin-right: 8rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  color: #ffffff;
  font-size: 18rpx;
  font-weight: 600;
}

.chip__name {
  display: block;
  overflow: hidden;
  font-size: 24rpx;
  font-weight: 600;
  color: #0f9d6a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip__spec {
  display: block;
  overflow: hidden;
  margin-top: 4rpx;
  font-size: 21rpx;
  color: #5f6b7a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip__meta {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #8a97a6;
}

.issue__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 18rpx;
}

.link {
  margin-right: 32rpx;
  font-size: 24rpx;
}

.link--warn {
  color: #d97706;
}

.link--muted {
  color: #8a97a6;
}

.issue__search {
  display: flex;
  align-items: center;
  width: 100%;
  margin-top: 16rpx;
}

.issue__search-input {
  height: 64rpx;
  flex: 1;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #f2f4f7;
  font-size: 24rpx;
  color: #1f2329;
}

.issue__search-btn {
  flex: none;
  margin-left: 20rpx;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #eef4ff;
  font-size: 24rpx;
  color: #2f6feb;
}

.issue__done {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
}

.issue__done-state {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: #0f9d6a;
}

:deep(.ph) {
  color: #b0b8c1;
}

.empty {
  margin: 20rpx 20rpx 0;
  padding: 48rpx 0;
  border-radius: 20rpx;
  background: #fff;
  font-size: 24rpx;
  color: #8a97a6;
  text-align: center;
}

/* ---------- 无需处置 ---------- */

.passive {
  padding: 24rpx 28rpx;
}

.passive__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.passive__title {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2329;
}

.passive__count {
  font-size: 22rpx;
  color: #8a97a6;
}

.passive__row {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f2f3f5;
}

.passive__row:last-child {
  border-bottom: 0;
}

.passive__name {
  display: -webkit-box;
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  overflow: hidden;
  font-size: 26rpx;
  color: #303133;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.passive__qty {
  flex: none;
  margin-left: 14rpx;
  font-size: 22rpx;
  color: #8a97a6;
}

.passive__foot {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.passive__owner {
  width: 200rpx;
  height: 52rpx;
  flex: none;
  padding: 0 18rpx;
  border-radius: 10rpx;
  background: #f7f8fa;
  font-size: 22rpx;
  color: #303133;
}

.passive__note {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  overflow: hidden;
  font-size: 21rpx;
  color: #8a97a6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resolved__undo {
  margin-left: auto;
  flex: none;
  font-size: 24rpx;
  color: #8a97a6;
}

/* ---------- 底部固定条（与明细页同款） ---------- */

.footer-spacer {
  height: calc(160rpx + env(safe-area-inset-bottom));
}

.action-bar {
  position: fixed;
  right: 0;
  bottom: var(--window-bottom, 0px);
  left: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgb(0 0 0 / 4%);
}

.action-bar__ghost {
  height: 80rpx;
  flex: none;
  padding: 0 28rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  line-height: 80rpx;
  color: #303133;
}

.action-bar__info {
  flex: none;
  margin: 0 20rpx;
  text-align: center;
}

.action-bar__amount {
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.1;
  color: #0f9d6a;
}

.action-bar__label {
  margin-top: 2rpx;
  font-size: 20rpx;
  line-height: 1.2;
  color: #9aa4b2;
}

.action-bar__primary {
  height: 80rpx;
  flex: 1;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
  font-size: 28rpx;
  font-weight: 500;
  line-height: 80rpx;
  text-align: center;
  color: #fff;
}

.action-bar__primary--disabled {
  opacity: 0.5;
}

/* ---------- AI 解析标识 ---------- */

.summary__head-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.summary__ai {
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
}

/* ---------- 缺品汇总 ---------- */

.missing {
  padding: 24rpx 28rpx;
  border: 1rpx solid #ffe0e0;
  background: #fffafa;
}

.missing__head {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.missing__icon {
  flex: none;
  font-size: 26rpx;
  color: #e5484d;
}

.missing__title {
  font-size: 26rpx;
  font-weight: 700;
  color: #c62828;
}

.missing__names {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 16rpx;
}

.missing__name {
  max-width: 100%;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background: #fdecec;
  font-size: 22rpx;
  color: #b3403f;
}

.missing__name--more {
  background: transparent;
  color: #8a97a6;
}

.missing__hint {
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 1.55;
  color: #8a97a6;
}

/* ---------- 行内缺品空态 ---------- */

.issue__missing {
  margin-top: 16rpx;
  padding: 18rpx 20rpx;
  border: 1rpx dashed #f2c4c4;
  border-radius: 14rpx;
  background: #fffafa;
}

.issue__missing-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #c62828;
}

.issue__missing-hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a97a6;
}

/* ---------- AI 解析动效 ---------- */

.ai-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(17 20 28 / 55%);
}

.ai-mask__card {
  display: flex;
  min-width: 400rpx;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 56rpx 40rpx;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 20rpx 60rpx rgb(0 0 0 / 18%);
}

.ai-orb {
  position: relative;
  width: 128rpx;
  height: 128rpx;
}

.ai-orb__ring {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  border: 3rpx solid var(--wot-color-theme-primary, #ff4d2e);
  border-radius: 50%;
  opacity: 0.6;
  animation: ai-ring 1.8s ease-out infinite;
}

.ai-orb__ring--late {
  animation-delay: 0.9s;
}

.ai-orb__core {
  position: absolute;
  left: 14rpx;
  top: 14rpx;
  right: 14rpx;
  bottom: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 10rpx 28rpx rgb(0 0 0 / 16%);
  color: #fff;
  font-size: 56rpx;
  animation: ai-breathe 1.8s ease-in-out infinite;
}

@keyframes ai-ring {
  0% {
    transform: scale(0.72);
    opacity: 0.7;
  }

  100% {
    transform: scale(1.4);
    opacity: 0;
  }
}

@keyframes ai-breathe {
  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.06);
  }
}

.ai-mask__title {
  margin-top: 28rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
}

.ai-mask__phase {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8a97a6;
}
</style>
