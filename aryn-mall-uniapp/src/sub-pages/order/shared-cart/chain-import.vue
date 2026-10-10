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
 */
import { onLoad } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
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

const rows = computed(() => report.value?.rows ?? [])
/** 需要用户处置的行：匹配成功、已下架的都不需要逐行确认 */
const actionableRows = computed(() =>
  rows.value.filter(row => row.resultType !== 'OK' && row.resultType !== 'OFF_SHELF'),
)
/** 无需处置的行（匹配成功/已下架），单独一组展示给用户核对 */
const passiveRows = computed(() =>
  rows.value.filter(row => row.resultType === 'OK' || row.resultType === 'OFF_SHELF'),
)

/** 报货人数：按解析出的人名去重（未识别的不计） */
const personCount = computed(
  () => new Set(rows.value.map(row => row.personName).filter(Boolean)).size,
)

const RESULT_LABEL: Record<string, string> = {
  OK: '匹配成功',
  UNMATCHED: '未匹配',
  SPEC_CHANGED: '规格变更',
  OVER_STOCK: '超库存',
  INVALID_QTY: '数量异常',
  OFF_SHELF: '已下架',
}

const RESULT_THEME: Record<string, string> = {
  OK: 'bg-emerald-50 text-emerald-600',
  UNMATCHED: 'bg-red-50 text-red-500',
  SPEC_CHANGED: 'bg-amber-50 text-amber-600',
  OVER_STOCK: 'bg-gray-100 text-gray-500',
  INVALID_QTY: 'bg-amber-50 text-amber-600',
  OFF_SHELF: 'bg-gray-100 text-gray-500',
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
        uni.showToast({ title: '剪贴板是空的', icon: 'none' })
        return
      }
      chainText.value = data
    },
    fail: () => {
      uni.showToast({ title: '读取剪贴板失败，请长按输入框粘贴', icon: 'none' })
    },
  })
}

async function parseText() {
  if (!chainText.value.trim()) {
    uni.showToast({ title: '请先粘贴接龙内容', icon: 'none' })
    return
  }
  if (!cartId.value)
    return
  parsing.value = true
  try {
    report.value = await previewChainImport(cartId.value, chainText.value)
    // 重置上一份报告留下的处置状态，避免行号串台
    Object.keys(actions).forEach(key => delete actions[Number(key)])
    Object.keys(personNames).forEach(key => delete personNames[Number(key)])
    Object.keys(pickCandidates).forEach(key => delete pickCandidates[Number(key)])
    step.value = 2
  }
  catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '解析失败',
      icon: 'none',
    })
  }
  finally {
    parsing.value = false
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
    uni.showToast({ title: '请人工改数量或补选商品', icon: 'none' })
    return
  }
  actions[row.rowNo] = { rowNo: row.rowNo, action: 'ADJUST_QTY', quantity }
  uni.showToast({ title: `已调整为 ${quantity}`, icon: 'none' })
}

/** 未匹配：人工补选，走既有商品搜索接口 */
async function searchCandidates(row: SharedCartImportRow) {
  const keyword = (pickKeywords[row.rowNo] || '').trim()
  if (!keyword) {
    uni.showToast({ title: '请输入品名关键字', icon: 'none' })
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
      uni.showToast({ title: '没有搜到商品，换个关键字试试', icon: 'none' })
  }
  catch {
    uni.showToast({ title: '搜索失败，请重试', icon: 'none' })
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
  uni.showToast({ title: '已选择商品', icon: 'none' })
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
    case 'ADJUST_QTY':
      return `已调整为 ${action.quantity}`
    case 'REPLACE_SKU':
      return '已补选商品'
    default:
      return '已跳过'
  }
}

async function handleConfirm() {
  if (!report.value)
    return
  confirming.value = true
  try {
    const payload = rows.value.map(buildPayload).filter(item => item !== null)
    report.value = await confirmSharedCartImport(cartId.value, report.value.importId, payload)
    uni.showToast({
      title: report.value.importedRows ? `已并入 ${report.value.importedRows} 项` : '已处理',
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
      title: error instanceof Error ? error.message : '并入失败',
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
  uni.showLoading({ title: '加载中' })
  try {
    const list = await listSharedCartImports(cartId.value)
    const latest = (list ?? []).find(item => item.status === '1')
    if (!latest) {
      uni.showToast({ title: '没有待处理的导入', icon: 'none' })
      return
    }
    report.value = await getSharedCartImport(cartId.value, latest.importId)
    step.value = 2
  }
  catch {
    uni.showToast({ title: '加载失败，请重试', icon: 'none' })
  }
  finally {
    uni.hideLoading()
  }
}
</script>

<template>
  <view class="min-h-100vh bg-gray-50 pb-40rpx">
    <hrNavbar title="粘贴接龙报货" />

    <!-- 步骤 1：粘贴接龙 -->
    <template v-if="step === 1">
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-32rpx">
        <view class="text-28rpx text-gray-800 font-bold">
          粘贴微信群接龙
        </view>
        <view class="mt-8rpx text-22rpx text-gray-400">
          把群里接龙消息整段复制过来，自动按「人 × 商品 × 数量」解析
        </view>

        <textarea
          v-model="chainText"
          class="mt-24rpx h-400rpx w-full rounded-16rpx bg-gray-50 p-24rpx text-26rpx"
          :maxlength="50000"
          placeholder="粘贴接龙原文，例如：&#10;1. 任化东 红富士冰糖心4斤，香蕉（熟点）3斤&#10;2. 水手长 盐汽水四包"
        />

        <view class="mt-20rpx flex gap-20rpx">
          <button class="h-80rpx flex-1 text-28rpx leading-80rpx !m-0" @tap="handlePasteFromClipboard">
            读取剪贴板
          </button>
          <button
            class="h-80rpx flex-1 text-28rpx leading-80rpx !m-0"
            type="primary"
            :disabled="parsing"
            @tap="parseText"
          >
            {{ parsing ? '正在解析…' : '解析接龙' }}
          </button>
        </view>

        <view class="mt-30rpx border-t border-gray-100 pt-20rpx text-22rpx text-gray-400">
          <view>· 每行「人名 + 报货内容」，商品用逗号或空格隔开、带数量（如 香蕉3斤）</view>
          <view class="mt-6rpx">
            · 解析不准的行会在报告里标出来，逐行核对后再并入
          </view>
          <view class="mt-6rpx">
            · 接龙里的人不需要是系统用户：人名只作配送分装标签
          </view>
        </view>
      </view>

      <view class="mx-20rpx mt-20rpx text-center text-24rpx text-blue-500" @tap="reopenLatestImport">
        继续上次未处理的导入 ›
      </view>
    </template>

    <!-- 步骤 2：解析报告 -->
    <template v-else>
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-28rpx font-bold">
            解析报告
          </view>
          <view class="text-22rpx text-gray-400">
            共 {{ report?.totalRows || 0 }} 项 · {{ personCount }} 人报货
          </view>
        </view>

        <view class="mt-20rpx flex flex-wrap gap-12rpx">
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-emerald-600 font-bold">
              {{ report?.matchedRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              匹配成功
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-red-500 font-bold">
              {{ report?.unmatchedRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              未匹配
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-gray-500 font-bold">
              {{ report?.overStockRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              超库存
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-amber-600 font-bold">
              {{ report?.invalidRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              数量异常
            </view>
          </view>
          <view class="min-w-120rpx flex-1 rounded-12rpx bg-gray-50 px-10rpx py-14rpx text-center">
            <view class="text-32rpx text-gray-400 font-bold">
              {{ report?.offShelfRows || 0 }}
            </view>
            <view class="mt-4rpx text-20rpx text-gray-400">
              已下架
            </view>
          </view>
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
            {{ RESULT_LABEL[row.resultType] }}
          </text>
          <text class="flex-1 truncate text-26rpx text-gray-800">
            {{ row.rawName || `第 ${row.rowNo} 行` }}
          </text>
          <text class="text-22rpx text-gray-400">
            × {{ row.quantity ?? '—' }}<text v-if="row.rawUnit">
              {{ row.rawUnit }}
            </text>
          </text>
        </view>
        <view v-if="row.resultMessage" class="mt-8rpx text-22rpx text-gray-500">
          {{ row.resultMessage }}
        </view>
        <view v-if="row.matchedName" class="mt-6rpx text-22rpx text-gray-400">
          当前商品：{{ row.matchedName }}<text v-if="row.matchedSpec">
            · {{ row.matchedSpec }}
          </text>
        </view>

        <!-- 归属人：接龙人名可修正（职务称呼 → 真实姓名），空则必须补 -->
        <view class="mt-16rpx flex items-center gap-12rpx">
          <text class="text-22rpx text-gray-400">
            归属人
          </text>
          <input
            v-model="personNames[row.rowNo]"
            class="h-56rpx w-240rpx rounded-12rpx bg-gray-100 px-16rpx text-24rpx"
            :placeholder="row.personName || '未识别到人名'"
            placeholder-class="text-gray-300"
          >
        </view>

        <view class="mt-16rpx flex items-center gap-16rpx">
          <text
            v-if="row.resultType === 'OVER_STOCK' || row.resultType === 'INVALID_QTY'"
            class="text-24rpx text-amber-600"
            @tap="adjustQuantity(row)"
          >
            调整为建议数量
          </text>
          <text class="text-24rpx text-blue-500" @tap="skipRow(row)">
            跳过
          </text>
          <text v-if="actionLabel(row)" class="ml-auto text-22rpx text-gray-400">
            {{ actionLabel(row) }}
          </text>
        </view>

        <!-- 人工补选（未匹配/超库存换货）：走既有商品搜索接口 -->
        <view v-if="row.resultType !== 'OFF_SHELF'" class="mt-16rpx border-t border-gray-100 pt-16rpx">
          <view class="flex items-center gap-12rpx">
            <input
              v-model="pickKeywords[row.rowNo]"
              class="h-56rpx flex-1 rounded-full bg-gray-100 px-20rpx text-24rpx"
              placeholder="搜索品名补选商品"
              @confirm="searchCandidates(row)"
            >
            <text class="text-24rpx text-blue-500" @tap="searchCandidates(row)">
              {{ picking[row.rowNo] ? '搜索中' : '搜索' }}
            </text>
          </view>
          <view
            v-for="(candidate, index) in pickCandidates[row.rowNo] || []"
            :key="`${row.rowNo}-${candidate.skuId || index}`"
            class="mt-12rpx flex items-center gap-12rpx rounded-12rpx bg-gray-50 px-16rpx py-12rpx"
            @tap="chooseCandidate(row, candidate)"
          >
            <view class="min-w-0 flex-1">
              <view class="truncate text-24rpx text-gray-800">
                {{ candidate.name }}
              </view>
              <view class="mt-4rpx text-20rpx text-gray-400">
                <text v-if="candidate.spec">
                  {{ candidate.spec }} ·
                </text>
                库存 {{ candidate.stock ?? '—' }}
              </view>
            </view>
            <text class="text-24rpx text-blue-500">
              选择
            </text>
          </view>
        </view>
      </view>

      <!-- 匹配成功/已下架的行：只读展示，解析疑点随结果说明带出 -->
      <view v-if="passiveRows.length" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          无需处置
        </view>
        <view
          v-for="row in passiveRows"
          :key="`done-${row.rowNo}`"
          class="mt-16rpx border-b border-gray-100 pb-12rpx"
        >
          <view class="flex items-center gap-12rpx text-24rpx">
            <text class="rounded-6rpx px-10rpx py-4rpx text-20rpx font-bold" :class="RESULT_THEME[row.resultType]">
              {{ RESULT_LABEL[row.resultType] }}
            </text>
            <text class="min-w-0 flex-1 truncate text-gray-700">
              {{ row.matchedName || row.rawName || `第 ${row.rowNo} 行` }}
            </text>
            <text class="text-22rpx text-gray-400">
              × {{ row.quantity ?? '—' }}<text v-if="row.rawUnit">
                {{ row.rawUnit }}
              </text>
            </text>
          </view>
          <view class="mt-6rpx flex items-center gap-12rpx">
            <text class="min-w-0 flex-1 truncate text-22rpx" :class="row.personName ? 'text-gray-400' : 'text-amber-500'">
              {{ row.personName || '归属未指定' }}
            </text>
            <input
              v-model="personNames[row.rowNo]"
              class="h-48rpx w-200rpx rounded-10rpx bg-gray-50 px-12rpx text-22rpx"
              placeholder="改归属人"
              placeholder-class="text-gray-300"
            >
          </view>
          <view v-if="row.resultMessage && row.resultMessage !== '已匹配'" class="mt-4rpx text-22rpx text-gray-500">
            {{ row.resultMessage }}
          </view>
        </view>
      </view>

      <view class="mx-20rpx my-30rpx flex gap-20rpx">
        <button class="h-80rpx flex-1 text-28rpx leading-80rpx !m-0" @tap="backToPaste">
          重新粘贴
        </button>
        <button
          class="h-80rpx flex-1 text-28rpx leading-80rpx !m-0"
          type="primary"
          :disabled="confirming || report?.status !== '1'"
          @tap="handleConfirm"
        >
          {{ report?.status === '2' ? '已并入补给单' : `确认并入（${report?.matchedRows || 0} 项）` }}
        </button>
      </view>
    </template>
  </view>
</template>
