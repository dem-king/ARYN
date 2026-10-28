<script setup lang="ts">
import type {
  SharedCartImport,
  SharedCartImportActionPayload,
  SharedCartImportCandidate,
  SharedCartImportRow,
} from '@/api/order/sharedCart'
/**
 * 补给单 Excel 导入（原型「详情 → 导入 → 报告」三步）。
 *
 * 与原型的两点刻意差异：
 * 1. **不画假的解析进度条**：解析同步完成后一次性返回报告，没有可回传的真实进度，
 *    这里用不确定态 loading 表达「正在解析」；
 * 2. 不做底部 sheet，独立分包页承载三步，避免详情页再膨胀。
 *
 * 数据与校验全部以服务端返回为准：客户端只上传文件、回传「行号 → 处置动作」，
 * 不自行判断某行能否入单（匹配口径在服务端，前端重复实现必然漂移）。
 */
import { onLoad } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { alovaInstance } from '@/api/core/instance'
import {
  confirmSharedCartImport,
  getSharedCartImport,
  listSharedCartImports,
} from '@/api/order/sharedCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useAuthStore } from '@/store/authStore'
import {
  downloadSharedCartImportTemplate,
  previewSharedCartImport,
} from '@/sub-pages/api/order/sharedCartImport'

definePage({
  name: 'shared-cart-import',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '导入补给清单',
  },
})

const authStore = useAuthStore()
const { t } = useI18n()

const cartId = ref('')
/** 1选文件 2报告 */
const step = ref<1 | 2>(1)
const parsing = ref(false)
const confirming = ref(false)
const fileName = ref('')
const report = ref<SharedCartImport | null>(null)
/** 行号 → 处置动作（只传动作，行内容一律以服务端已落库的解析行为准） */
const actions = reactive<Record<number, SharedCartImportActionPayload>>({})
/** 行号 → 人工补选关键字 */
const pickKeywords = reactive<Record<number, string>>({})
/** 行号 → 人工补选中的候选 */
const pickCandidates = reactive<Record<number, SharedCartImportCandidate[]>>({})
const picking = reactive<Record<number, boolean>>({})

const rows = computed(() => report.value?.rows ?? [])
/**
 * 需要用户处置的行：匹配成功、已下架、未填数量的都不需要逐行确认。
 *
 * 未填数量必须排除在外：目录模板铺满在售商品，客户只填要买的几行，
 * 把没填的行全列出来会变成一屏几百条的噪声，真正的错误反而看不见。
 */
const actionableRows = computed(() =>
  rows.value.filter(row =>
    row.resultType !== 'OK' && row.resultType !== 'OFF_SHELF' && row.resultType !== 'NOT_FILLED',
  ),
)
/** 无需处置的行（含未填数量），单独一组展示给用户核对 */
const passiveRows = computed(() =>
  rows.value.filter(row =>
    row.resultType === 'OK' || row.resultType === 'OFF_SHELF' || row.resultType === 'NOT_FILLED',
  ),
)

/** 未填数量行数：报告计数优先，缺失时按行实时算（旧报告没有该字段） */
const notFilledCount = computed(
  () => report.value?.notFilledRows ?? rows.value.filter(row => row.resultType === 'NOT_FILLED').length,
)

const RESULT_LABELS = computed<Record<string, string>>(() => ({
  OK: t('sharedCart.result.matched'),
  UNMATCHED: t('sharedCart.result.unmatched'),
  SPEC_CHANGED: t('sharedCart.result.specChanged'),
  OVER_STOCK: t('sharedCart.result.overStock'),
  INVALID_QTY: t('sharedCart.result.invalidQty'),
  OFF_SHELF: t('sharedCart.result.offShelf'),
  NOT_FILLED: t('sharedCart.result.notFilled'),
}))

const RESULT_THEME: Record<string, string> = {
  OK: 'bg-emerald-50 text-emerald-600',
  UNMATCHED: 'bg-red-50 text-red-500',
  SPEC_CHANGED: 'bg-amber-50 text-amber-600',
  OVER_STOCK: 'bg-gray-100 text-gray-500',
  INVALID_QTY: 'bg-amber-50 text-amber-600',
  OFF_SHELF: 'bg-gray-100 text-gray-500',
  NOT_FILLED: 'bg-gray-100 text-gray-400',
}

onLoad((options) => {
  cartId.value = options?.cartId ?? ''
  if (!authStore.isLoggedIn)
    uni.navigateTo({ url: '/pages/login/index' })
})

/** 选文件：小程序只能从聊天记录选（基础库 ≥ 2.6.0），H5 走 chooseFile */
function handlePickFile() {
  // #ifdef MP-WEIXIN
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['xlsx', 'xls'],
    success: ({ tempFiles }) => {
      const file = tempFiles?.[0]
      if (file?.path)
        void uploadAndParse(file.path, file.name || '')
    },
  })
  // #endif

  // #ifdef H5
  uni.chooseFile({
    count: 1,
    extension: ['.xlsx', '.xls'],
    success: (result) => {
      // H5 的 tempFiles 是原生 File 对象（有 name、无 path），
      // 路径要取 tempFilePaths，两边字段并不一致
      const files = Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]
      const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths]
      const picked = (files as any)?.[0]
      const path = (picked as any)?.path || paths?.[0]
      if (path)
        void uploadAndParse(path, (picked as any)?.name || '')
    },
  })
  // #endif

  // #ifdef APP-PLUS
  uni.showToast({ title: t('sharedCart.importPage.appUnsupported'), icon: 'none' })
  // #endif
}

async function uploadAndParse(filePath: string, name: string) {
  if (!cartId.value)
    return
  parsing.value = true
  fileName.value = name
  try {
    report.value = await previewSharedCartImport(cartId.value, filePath)
    // 重置上一份报告留下的处置状态，避免行号串台
    Object.keys(actions).forEach(key => delete actions[Number(key)])
    Object.keys(pickCandidates).forEach(key => delete pickCandidates[Number(key)])
    step.value = 2
  }
  catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : t('sharedCart.importPage.uploadFailed'),
      icon: 'none',
    })
  }
  finally {
    parsing.value = false
  }
}

/** 规格变更：确认新规格 */
function acceptSpec(row: SharedCartImportRow) {
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'ACCEPT_SPEC' }
  uni.showToast({ title: t('sharedCart.importPage.specAccepted'), icon: 'none' })
}

/** 超库存/数量异常：按服务端建议值调整 */
function adjustQuantity(row: SharedCartImportRow) {
  const quantity = row.suggestedQuantity
  if (!quantity) {
    uni.showToast({ title: t('sharedCart.importPage.stockInsufficient'), icon: 'none' })
    return
  }
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'ADJUST_QTY', quantity }
  uni.showToast({ title: t('sharedCart.importPage.adjustedTo', { qty: quantity }), icon: 'none' })
}

/** 未匹配：人工补选，走既有商品搜索接口 */
async function searchCandidates(row: SharedCartImportRow) {
  const keyword = (pickKeywords[row.rowNo] || '').trim()
  if (!keyword) {
    uni.showToast({ title: t('sharedCart.importPage.keywordRequired'), icon: 'none' })
    return
  }
  picking[row.rowNo] = true
  try {
    const page: any = await alovaInstance.Get('/product/app/goodsspu/search', {
      params: { scene: '2', keyword, current: 1, size: 20 },
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
      uni.showToast({ title: t('sharedCart.importPage.noSearchResult'), icon: 'none' })
  }
  catch {
    uni.showToast({ title: t('sharedCart.importPage.searchFailed'), icon: 'none' })
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
  pickCandidates[row.rowNo] = []
  uni.showToast({ title: t('sharedCart.importPage.productSelected'), icon: 'none' })
}

/** 跳过某行 */
function skipRow(row: SharedCartImportRow) {
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'SKIP' }
}

/** 行处置状态文案 */
function actionLabel(row: SharedCartImportRow) {
  const action = actions[row.rowNo]
  if (!action)
    return ''
  switch (action.action) {
    case 'ACCEPT_SPEC':
      return t('sharedCart.importPage.actionSpec')
    case 'ADJUST_QTY':
      return t('sharedCart.importPage.adjustedTo', { qty: action.quantity })
    case 'REPLACE_SKU':
      return t('sharedCart.importPage.actionReplaced')
    default:
      return t('sharedCart.importPage.actionSkipped')
  }
}

async function handleConfirm() {
  if (!report.value)
    return
  confirming.value = true
  try {
    report.value = await confirmSharedCartImport(cartId.value, report.value.importId, Object.values(actions))
    uni.showToast({
      title: report.value.importedRows
        ? t('sharedCart.importPage.mergedCount', { count: report.value.importedRows })
        : t('sharedCart.importPage.processed'),
      icon: 'none',
    })
    if (report.value.importedRows) {
      const pages = getCurrentPages()
      const previous: any = pages[pages.length - 2]
      if (previous?.$vm?.fetchDetail)
        previous.$vm.fetchDetail()
      // 延时返回期间用户可能已经自己离开本页（切了 tab、点了返回）：
      // 那时再 navigateBack 就是对已销毁页面发路由，微信报
      // `routeDone with a webviewId xxx is not found`。
      // 因此先记住当前页，回调里确认「还是这一页且下面还有页」才返回。
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
      title: error instanceof Error ? error.message : t('sharedCart.importPage.mergeFailed'),
      icon: 'none',
    })
  }
  finally {
    confirming.value = false
  }
}

/** 重新解析（回到选文件步骤，保留报告供返回查看） */
function backToPick() {
  step.value = 1
}

/** 下载模板：服务端生成、带鉴权头取回后打开（不把模板接口挂白名单） */
async function downloadTemplate() {
  try {
    await downloadSharedCartImportTemplate()
  }
  catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : t('sharedCart.importPage.templateDownloadFailed'),
      icon: 'none',
    })
  }
}

async function reopenLatestImport() {
  uni.showLoading({ title: t('common.loading') })
  try {
    const list = await listSharedCartImports(cartId.value)
    const latest = (list ?? []).find(item => item.status === '1')
    if (!latest) {
      uni.showToast({ title: t('sharedCart.importPage.noPendingImport'), icon: 'none' })
      return
    }
    report.value = await getSharedCartImport(cartId.value, latest.importId)
    step.value = 2
  }
  catch {
    uni.showToast({ title: t('sharedCart.importPage.loadFailed'), icon: 'none' })
  }
  finally {
    uni.hideLoading()
  }
}
</script>

<template>
  <view class="min-h-100vh bg-gray-50 pb-40rpx">
    <hrNavbar :title="t('sharedCart.importPage.navTitle')" />

    <!-- 步骤 1：选择文件 -->
    <template v-if="step === 1">
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-32rpx">
        <view class="text-28rpx text-gray-800 font-bold">
          {{ t('sharedCart.importPage.title') }}
        </view>
        <view class="mt-8rpx text-22rpx text-gray-400">
          {{ t('sharedCart.importPage.fileHint') }}
        </view>

        <view
          class="mt-24rpx border-2 border-blue-400 rounded-20rpx border-dashed bg-blue-50 px-24rpx py-48rpx text-center"
          @tap="handlePickFile"
        >
          <view class="text-28rpx text-blue-600 font-bold">
            {{ parsing ? t('sharedCart.importPage.parsing') : t('sharedCart.importPage.pickFile') }}
          </view>
          <view class="mt-10rpx text-22rpx text-gray-400">
            {{ t('sharedCart.importPage.templateHint') }}
          </view>
        </view>

        <view v-if="parsing" class="mt-20rpx text-center text-24rpx text-gray-500">
          {{ t('sharedCart.importPage.parsingHint') }}
        </view>

        <view class="mt-20rpx text-center text-22rpx text-blue-500" @tap="downloadTemplate">
          {{ t('sharedCart.importPage.downloadTemplate') }}
        </view>

        <view class="mt-30rpx border-t border-gray-100 pt-20rpx text-22rpx text-gray-400">
          <view>· {{ t('sharedCart.importPage.ruleTemplate') }}</view>
          <view class="mt-6rpx">
            · {{ t('sharedCart.importPage.ruleCode') }}
          </view>
          <view class="mt-6rpx">
            · {{ t('sharedCart.importPage.ruleName') }}
          </view>
          <view class="mt-6rpx">
            · {{ t('sharedCart.importPage.ruleQty') }}
          </view>
        </view>
      </view>

      <view class="mx-20rpx mt-20rpx text-center text-24rpx text-blue-500" @tap="reopenLatestImport">
        {{ t('sharedCart.importPage.continueLast') }}
      </view>
    </template>

    <!-- 步骤 2：导入报告 -->
    <template v-else>
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-28rpx font-bold">
            {{ t('sharedCart.importPage.reportTitle') }}
          </view>
          <view class="text-22rpx text-gray-400">
            {{ t('sharedCart.importPage.totalRows', { count: report?.totalRows || 0 }) }}
          </view>
        </view>
        <view v-if="fileName" class="mt-6rpx text-22rpx text-gray-400">
          {{ fileName }}
        </view>

        <view class="mt-20rpx flex flex-wrap gap-12rpx">
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-emerald-600 font-bold">
              {{ report?.matchedRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.matched') }}
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-red-500 font-bold">
              {{ report?.unmatchedRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.unmatched') }}
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-amber-600 font-bold">
              {{ report?.specChangedRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.specChanged') }}
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-gray-500 font-bold">
              {{ report?.overStockRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.overStock') }}
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-amber-600 font-bold">
              {{ report?.invalidRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.invalidQty') }}
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-gray-400 font-bold">
              {{ report?.offShelfRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.offShelf') }}
            </view>
          </view>
          <!-- 未填数量独立一格且用灰字：模板铺满在售商品，这是正常状态而非错误 -->
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-gray-400 font-bold">
              {{ report?.notFilledRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              {{ t('sharedCart.result.notFilled') }}
            </view>
          </view>
        </view>

        <!-- 只填了少数行时给出明确解释，避免用户以为漏导了商品 -->
        <view v-if="report?.notFilledRows" class="mt-16rpx text-22rpx text-gray-400">
          {{ t('sharedCart.importPage.notFilledHint', { count: report.notFilledRows }) }}
        </view>
      </view>

      <!-- 待处置行 -->
      <view
        v-for="row in actionableRows"
        :key="row.rowNo"
        class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx"
      >
        <view class="flex items-center gap-12rpx">
          <text class="rounded-6rpx px-10rpx py-4rpx text-20rpx font-bold" :class="RESULT_THEME[row.resultType]">
            {{ RESULT_LABELS[row.resultType] }}
          </text>
          <text class="flex-1 text-26rpx text-gray-800">
            {{ row.rawName || row.rawCode || t('sharedCart.importPage.rowNo', { row: row.rowNo }) }}
          </text>
          <text class="text-22rpx text-gray-400">
            × {{ row.rawQuantity ?? '—' }}
          </text>
        </view>
        <view v-if="row.resultMessage" class="mt-8rpx text-22rpx text-gray-500">
          {{ row.resultMessage }}
        </view>
        <view v-if="row.matchedName" class="mt-6rpx text-22rpx text-gray-400">
          {{ t('sharedCart.importPage.currentProduct', { name: row.matchedName }) }}<text v-if="row.matchedSpec">
            · {{ row.matchedSpec }}
          </text>
        </view>

        <view class="mt-16rpx flex items-center gap-16rpx">
          <text
            v-if="row.resultType === 'SPEC_CHANGED'"
            class="text-24rpx text-emerald-600"
            @tap="acceptSpec(row)"
          >
            {{ t('sharedCart.importPage.confirmNewSpec') }}
          </text>
          <text
            v-if="row.resultType === 'OVER_STOCK' || row.resultType === 'INVALID_QTY'"
            class="text-24rpx text-amber-600"
            @tap="adjustQuantity(row)"
          >
            {{ t('sharedCart.importPage.adjustToSuggested') }}
          </text>
          <text class="text-24rpx text-blue-500" @tap="skipRow(row)">
            {{ t('sharedCart.importPage.skip') }}
          </text>
          <text v-if="actionLabel(row)" class="ml-auto text-22rpx text-gray-400">
            {{ actionLabel(row) }}
          </text>
        </view>

        <!-- 人工补选（未匹配）：走既有商品搜索接口 -->
        <view v-if="row.resultType === 'UNMATCHED'" class="mt-16rpx border-t border-gray-100 pt-16rpx">
          <view class="flex items-center gap-12rpx">
            <input
              v-model="pickKeywords[row.rowNo]"
              class="flex-1 rounded-full bg-gray-100 px-20rpx py-12rpx text-24rpx"
              :placeholder="t('sharedCart.importPage.searchPlaceholder')"
              @confirm="searchCandidates(row)"
            >
            <text class="text-24rpx text-blue-500" @tap="searchCandidates(row)">
              {{ picking[row.rowNo] ? t('sharedCart.importPage.searching') : t('sharedCart.importPage.search') }}
            </text>
          </view>
          <view
            v-for="(candidate, index) in pickCandidates[row.rowNo] || []"
            :key="`${row.rowNo}-${candidate.skuId || index}`"
            class="mt-12rpx flex items-center gap-12rpx rounded-12rpx bg-gray-50 px-16rpx py-12rpx"
            @tap="chooseCandidate(row, candidate)"
          >
            <view class="flex-1">
              <view class="text-24rpx text-gray-800">
                {{ candidate.name }}
              </view>
              <view class="mt-4rpx text-20rpx text-gray-400">
                <text v-if="candidate.spec">
                  {{ candidate.spec }} ·
                </text>
                {{ t('sharedCart.importPage.stock', { count: candidate.stock ?? '—' }) }}
              </view>
            </view>
            <text class="text-24rpx text-blue-500">
              {{ t('sharedCart.importPage.choose') }}
            </text>
          </view>
        </view>
      </view>

      <!--
        匹配成功/已下架的行：只读展示，避免用户以为漏了。
        未填数量的行**不逐行列出**：目录模板一次可能带回几百行，
        全铺出来会撑爆列表也让用户抓不住重点，只在上面用计数说明。
      -->
      <view
        v-if="passiveRows.length > notFilledCount"
        class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx"
      >
        <view class="text-28rpx font-bold">
          {{ t('sharedCart.importPage.noActionNeeded') }}
        </view>
        <view
          v-for="row in passiveRows.filter(item => item.resultType !== 'NOT_FILLED')"
          :key="`done-${row.rowNo}`"
          class="mt-16rpx flex items-center gap-12rpx border-b border-gray-100 pb-12rpx text-24rpx"
        >
          <text class="rounded-6rpx px-10rpx py-4rpx text-20rpx font-bold" :class="RESULT_THEME[row.resultType]">
            {{ RESULT_LABELS[row.resultType] }}
          </text>
          <text class="flex-1 text-gray-700">
            {{ row.matchedName || row.rawName || t('sharedCart.importPage.rowNo', { row: row.rowNo }) }}
          </text>
          <text class="text-22rpx text-gray-400">
            {{ row.resultType === 'OK' ? `× ${row.quantity ?? row.rawQuantity ?? '—'}` : '' }}
          </text>
        </view>
      </view>

      <view class="mx-20rpx my-30rpx flex gap-20rpx">
        <button class="h-80rpx flex-1 text-28rpx leading-80rpx !m-0" @tap="backToPick">
          {{ t('sharedCart.importPage.repick') }}
        </button>
        <button
          class="h-80rpx flex-1 text-28rpx leading-80rpx !m-0"
          type="primary"
          :disabled="confirming || report?.status !== '1'"
          @tap="handleConfirm"
        >
          {{ report?.status === '2'
            ? t('sharedCart.importPage.mergedToCart')
            : t('sharedCart.importPage.confirmMerge', { count: report?.matchedRows || 0 }) }}
        </button>
      </view>
    </template>
  </view>
</template>
