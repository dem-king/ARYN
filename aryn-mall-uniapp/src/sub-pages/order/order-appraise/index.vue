<script setup lang="ts">
import { onLoad, onPageScroll, onUnload } from '@dcloudio/uni-app'
import { computed, nextTick, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import { getById, orderAppraise } from '@/api/order/orderInfo'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { getUserPage } from '@/sub-pages/api/product/appraise'
import { formatTime } from '@/utils/index'
import { annotateNewbieSplitRows } from '@/utils/order-item-merge'
import {
  APPRAISE_MAX_CONTENT,
  APPRAISE_MAX_PICS,
  buildAppraisePayload,
  buildReviewedItemMap,
  findFirstAppraiseIssue,
  normalizeScore,
} from './payload'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'order-appraise',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '发表评论',
  },
})

/** 快捷标签 UI 上最多勾选 3 个（后端允许 5，留出接口余量） */
const MAX_SELECTED_TAGS = 3
/** 快捷标签词条 key：文案走 i18n，提交的是当前语言下的标签文本 */
const TAG_KEYS = ['tagFresh', 'tagPackaging', 'tagPortion', 'tagDelivery', 'tagService', 'tagValue'] as const

interface AppraiseCard {
  orderItemId: string
  spuId: string
  picUrl: string
  spuName: string
  specsInfo: string
  /** 新人价拆行标注（首件行/超出价行），解释同 SKU 两行 */
  newbieSplitHint?: boolean
  newbieRemainderHint?: boolean
  goodsScore: number
  /** 用户是否单独动过这一卡的星级（整单星级变化时不覆盖已单独调整的卡） */
  scoreTouched: boolean
  content: string
  uploadList: any[]
  tags: string[]
  expanded: boolean
  skipped: boolean
  reviewed: boolean
  reviewedTime: string
  reviewedScore: number | null
  systemDefault: boolean
}

interface PageState {
  orderId: string
  items: AppraiseCard[]
  /** 整单星级：默认 5，变化时同步到未单独调整的商品卡 */
  overallScore: number
  logisticsScore: number
  serviceScore: number
  unifiedContent: string
  unifiedUploadList: any[]
  unifiedTags: string[]
  anonymous: boolean
  separateMode: boolean
  loading: boolean
  loadFailed: boolean
  submitting: boolean
}

const { error, success, info } = useGlobalToast()
const { t } = useI18n()
const router = useRouter()
const globalLoading = useGlobalLoading()
const authStore = useAuthStore()

const uploadPath = rewriteBootUrl(
  '/upms/file/app/upload',
  parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
) ?? '/upms/file/app/upload'
const action = ref(buildApiUrl(uploadPath))
const headers = ref({
  'satoken': authStore.getToken,
  'tenant-id': import.meta.env.VITE_TENANT_ID,
})

const state = reactive<PageState>({
  orderId: '',
  items: [],
  overallScore: 5,
  logisticsScore: 5,
  serviceScore: 5,
  unifiedContent: '',
  unifiedUploadList: [],
  unifiedTags: [],
  anonymous: false,
  separateMode: false,
  loading: true,
  loadFailed: false,
  submitting: false,
})

/** 提交前定位到的问题卡下标（滚动 + 高亮） */
const highlightIndex = ref(-1)
let highlightTimer: ReturnType<typeof setTimeout> | null = null
/** 提交成功后的延时跳转句柄（页面卸载时必须清掉，否则会对已销毁页面发路由） */
let navTimer: ReturnType<typeof setTimeout> | null = null
/** setup 时的页面实例：延时回调里用它判断页面是否仍是栈顶 */
const pageAtSetup = typeof getCurrentPages === 'function' ? getCurrentPages().at(-1) : undefined

/**
 * 延时路由：提交提示还在展示时，用户可能自己点了返回/被登录态顶掉。
 * 对已销毁页面发路由会触发微信
 * `routeDone with a webviewId xxx is not found`，故先记住当前页、到点确认仍在栈顶
 * 再跳（与 order-detail / shared-cart 的既有写法同口径）。
 */
function navigateLater(delay: number, run: () => void) {
  if (navTimer) {
    clearTimeout(navTimer)
  }
  navTimer = setTimeout(() => {
    navTimer = null
    if (pageAtSetup && getCurrentPages().at(-1) !== pageAtSetup) {
      return
    }
    run()
  }, delay)
}

onUnload(() => {
  if (navTimer) {
    clearTimeout(navTimer)
    navTimer = null
  }
  if (highlightTimer) {
    clearTimeout(highlightTimer)
    highlightTimer = null
  }
})

/** 可选快捷标签（当前语言下的文案） */
const tagOptions = computed(() => TAG_KEYS.map(key => t(`order.appraise.${key}`)))

/** 可提交的商品数：未评价且未跳过 */
const editableCount = computed(() => state.items.filter(item => !item.reviewed && !item.skipped).length)
/** 整单是否已全部评价（进入页面即全部评完的兜底展示） */
const allReviewed = computed(() => state.items.length > 0 && state.items.every(item => item.reviewed))
const canSubmit = computed(() => editableCount.value > 0 && !state.submitting)

onLoad((options) => {
  const id = options?.id
  if (!id) {
    state.loading = false
    return
  }
  state.orderId = String(id)
  loadPage()
})

/**
 * 拉取订单项 + 当前订单已评列表（并行）：
 * 已评列表只用来把对应商品卡置灰，失败不阻断评价流程（后端仍会幂等/校验）。
 */
async function loadPage() {
  state.loadFailed = false
  globalLoading.loading(t('common.loading'))
  try {
    const [orderResult, reviewedResult] = await Promise.allSettled([
      getById(state.orderId),
      getUserPage({
        current: 1,
        size: 200,
        orderId: state.orderId,
        desc: 'create_time',
      }),
    ])
    if (orderResult.status !== 'fulfilled' || !orderResult.value) {
      state.loadFailed = true
      error(t('order.appraise.loadFailed'))
      return
    }
    const reviewedMap = reviewedResult.status === 'fulfilled'
      ? buildReviewedItemMap(reviewedResult.value?.records || [])
      : new Map()
    const orderItemList = annotateNewbieSplitRows(orderResult.value?.orderItemList || [])
    state.items = orderItemList.map((item: any) => {
      const reviewed = reviewedMap.get(item.id)
      return {
        orderItemId: String(item.id),
        spuId: item.spuId || '',
        picUrl: item.picUrl || '',
        spuName: item.spuName || '',
        specsInfo: item.specsInfo || '',
        // 新人价拆行标注：评价按行提交，保留逐行但解释同 SKU 两行
        newbieSplitHint: item.newbieSplitHint === true,
        newbieRemainderHint: item.newbieRemainderHint === true,
        goodsScore: 5,
        scoreTouched: false,
        content: '',
        uploadList: [],
        tags: [],
        expanded: false,
        skipped: false,
        reviewed: Boolean(reviewed),
        reviewedTime: reviewed?.reviewedTime || '',
        reviewedScore: reviewed?.reviewedScore ?? null,
        systemDefault: Boolean(reviewed?.systemDefault),
      }
    })
  }
  catch {
    // 网络/接口错误由请求中间件统一 toast，这里只落失败态给出重试入口
    state.loadFailed = true
  }
  finally {
    state.loading = false
    globalLoading.close()
  }
}

/** 整单星级变化：同步到未单独调整过星级的未评商品 */
function onOverallScoreChange(event?: { value?: number | string | null }) {
  const score = normalizeScore(event?.value) ?? 5
  state.overallScore = score
  for (const item of state.items) {
    if (item.reviewed || item.skipped || item.scoreTouched)
      continue
    item.goodsScore = score
  }
}

/** 全部五星：一键把所有未评商品置 5 星（显式操作，覆盖单独调整过的卡） */
function onAllFiveStars() {
  state.overallScore = 5
  for (const item of state.items) {
    if (item.reviewed || item.skipped)
      continue
    item.goodsScore = 5
    item.scoreTouched = false
  }
}

/** 单卡星级变化：标记已单独调整，后续整单星级不再覆盖 */
function onItemScoreChange(index: number, event?: { value?: number | string | null }) {
  const item = state.items[index]
  if (!item)
    return
  item.goodsScore = normalizeScore(event?.value) ?? 5
  item.scoreTouched = true
}

function toggleExpand(index: number) {
  const item = state.items[index]
  if (!item)
    return
  item.expanded = !item.expanded
}

function skipItem(index: number) {
  const item = state.items[index]
  if (!item)
    return
  item.skipped = true
  item.expanded = false
}

function unskipItem(index: number) {
  const item = state.items[index]
  if (!item)
    return
  item.skipped = false
}

function toggleTag(tags: string[], tag: string) {
  const index = tags.indexOf(tag)
  if (index >= 0) {
    tags.splice(index, 1)
    return
  }
  if (tags.length >= MAX_SELECTED_TAGS) {
    info(t('order.appraise.quickTagsLimit', { count: MAX_SELECTED_TAGS }))
    return
  }
  tags.push(tag)
}

function toggleItemTag(index: number, tag: string) {
  const item = state.items[index]
  if (!item)
    return
  toggleTag(item.tags, tag)
}

function toggleUnifiedTag(tag: string) {
  toggleTag(state.unifiedTags, tag)
}

function goMyAppraise() {
  router.push({ name: 'user-appraise' })
}

/** 滚动 + 高亮到具体商品卡，用于上传失败等可定位的问题 */
function focusItem(index: number) {
  highlightIndex.value = index
  if (highlightTimer)
    clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => {
    highlightIndex.value = -1
  }, 2000)
  nextTick(() => {
    uni.pageScrollTo({
      selector: `#appraise-item-${index}`,
      duration: 250,
    })
  })
}

function itemDisplayName(index: number) {
  return state.items[index]?.spuName || t('order.appraise.productRating')
}

/** 组装整单区表单，供载荷组装与问题定位共用 */
function buildForm() {
  return {
    separate: state.separateMode,
    anonymous: state.anonymous,
    logisticsScore: state.logisticsScore,
    serviceScore: state.serviceScore,
    unified: {
      content: state.unifiedContent,
      uploadList: state.unifiedUploadList,
      tags: state.unifiedTags,
    },
  }
}

async function onsubmit() {
  if (state.submitting || !state.orderId)
    return
  const form = buildForm()
  const payload = buildAppraisePayload(state.items, form)
  if (payload.length === 0) {
    if (allReviewed.value) {
      info(t('order.appraise.allReviewed'))
      navigateLater(900, () => router.push({ name: 'user-appraise' }))
      return
    }
    error(t('order.appraise.submitEmpty'))
    return
  }
  // 提交前定位：上传失败/未传完会导致丢图，滚动到具体商品卡而不是只弹通用错误
  const issue = findFirstAppraiseIssue(state.items, form)
  if (issue) {
    const name = itemDisplayName(issue.index)
    error(issue.code === 'uploading'
      ? t('order.appraise.uploadPending', { name })
      : t('order.appraise.uploadFailed', { name }))
    focusItem(issue.index)
    return
  }
  state.submitting = true
  globalLoading.loading(t('common.loading'))
  try {
    await orderAppraise(state.orderId, payload)
    const submittedIds = new Set(payload.map(entry => entry.orderItemId))
    // 剩余 = 未评价（含被跳过的）且不在本次提交里的商品
    const remaining = state.items.filter(item => !item.reviewed && !submittedIds.has(item.orderItemId)).length
    const submittedCount = payload.length
    // 本地先置为已评价：即使返回上一页失败，也不会重复提交同一批商品
    for (const item of state.items) {
      const entry = payload.find(payloadItem => payloadItem.orderItemId === item.orderItemId)
      if (!entry)
        continue
      item.reviewed = true
      item.reviewedScore = entry.goodsScore
      item.reviewedTime = ''
    }
    if (remaining > 0) {
      success({
        msg: t('order.appraise.successPartial', { count: submittedCount, remain: remaining }),
        duration: 1800,
      })
      // 子集提交后订单仍在「待评价」：提示剩余件数并回到上一页，稍后可继续评
      navigateLater(1800, () => router.back())
      return
    }
    success({ msg: t('order.appraise.successAll'), duration: 1500 })
    // 全部评完后端已把订单置为已评价：跳到「我的评价」看结果
    navigateLater(900, () => router.push({ name: 'user-appraise' }))
  }
  catch {
    // 接口错误由请求中间件统一 toast（后端 message）
    state.submitting = false
  }
  finally {
    globalLoading.close()
  }
}

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <hr-navbar :title="t('order.appraise.title')" />

  <view v-if="state.loading" class="flex justify-center p-t-120rpx">
    <wd-loading />
  </view>

  <view v-else-if="state.loadFailed" class="m-2 rounded-xl bg-white p-40rpx text-center dark:bg-[var(--wot-dark-background2)]">
    <view class="text-28rpx text-gray-500">
      {{ t('order.appraise.loadFailed') }}
    </view>
    <view class="p-t-24rpx">
      <wd-button size="small" type="primary" plain @click="loadPage">
        {{ t('common.retry') }}
      </wd-button>
    </view>
  </view>

  <view v-else class="p-20rpx">
    <!-- 已全部评价（进入页面即无待评商品） -->
    <view
      v-if="state.items.length === 0 || allReviewed"
      class="rounded-xl bg-white p-40rpx text-center dark:bg-[var(--wot-dark-background2)]"
    >
      <view class="text-28rpx text-gray-500">
        {{ state.items.length === 0 ? t('order.appraise.noItems') : t('order.appraise.allReviewed') }}
      </view>
      <view class="p-t-24rpx">
        <wd-button size="small" type="primary" plain @click="goMyAppraise">
          {{ t('accountSettings.appraise.navTitle') }}
        </wd-button>
      </view>
    </view>

    <template v-else>
      <!-- ============ 整单快捷区 ============ -->
      <view class="rounded-xl bg-white p-24rpx dark:bg-[var(--wot-dark-background2)]">
        <view class="flex items-center justify-between">
          <view class="text-30rpx text-gray-800 font-bold dark:text-gray-100">
            {{ t('order.appraise.overallTitle') }}
          </view>
          <wd-button size="small" plain @click="onAllFiveStars">
            {{ t('order.appraise.allFiveStars') }}
          </wd-button>
        </view>

        <view class="p-t-16rpx">
          <wd-rate v-model="state.overallScore" :num="5" size="24px" @change="onOverallScoreChange" />
        </view>

        <!-- 物流 / 服务评分：整单采集，写入每条商品评价 -->
        <view class="flex items-center justify-between p-t-16rpx">
          <text class="text-26rpx text-gray-600 dark:text-gray-300">
            {{ t('order.appraise.logisticsRating') }}
          </text>
          <wd-rate v-model="state.logisticsScore" :num="5" size="18px" />
        </view>
        <view class="flex items-center justify-between p-t-8rpx">
          <text class="text-26rpx text-gray-600 dark:text-gray-300">
            {{ t('order.appraise.serviceRating') }}
          </text>
          <wd-rate v-model="state.serviceScore" :num="5" size="18px" />
        </view>

        <!-- 统一文案 / 晒图 / 标签：分开评价时隐藏，改由每张商品卡单独填写 -->
        <template v-if="!state.separateMode">
          <view class="p-t-24rpx text-26rpx text-gray-600 dark:text-gray-300">
            {{ t('order.appraise.unifiedTitle') }}
          </view>
          <view class="p-t-12rpx">
            <wd-textarea
              v-model="state.unifiedContent"
              custom-class="wd-textarea"
              :maxlength="APPRAISE_MAX_CONTENT"
              :show-word-limit="true"
              :auto-height="true"
              :placeholder="t('order.appraise.unifiedPlaceholder')"
            />
          </view>
          <view class="p-t-16rpx text-24rpx text-gray-400">
            {{ t('order.appraise.unifiedPics') }}
          </view>
          <view class="p-t-8rpx">
            <wd-upload
              v-model:file-list="state.unifiedUploadList"
              multiple
              :header="headers"
              image-mode="aspectFill"
              :action="action"
              :limit="APPRAISE_MAX_PICS"
            />
          </view>
          <view class="flex items-center p-t-16rpx">
            <text class="text-24rpx text-gray-400">{{ t('order.appraise.quickTags') }}</text>
            <text class="p-l-8rpx text-22rpx text-gray-300">
              {{ t('order.appraise.quickTagsLimit', { count: MAX_SELECTED_TAGS }) }}
            </text>
          </view>
          <view class="flex flex-wrap p-t-8rpx">
            <view
              v-for="tag in tagOptions"
              :key="tag"
              class="mb-12rpx mr-12rpx rounded-full px-20rpx py-8rpx text-24rpx"
              :class="state.unifiedTags.includes(tag)
                ? 'bg-primary text-white'
                : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'"
              @click="toggleUnifiedTag(tag)"
            >
              {{ tag }}
            </view>
          </view>
        </template>

        <!-- 匿名 / 分开评价开关 -->
        <view class="mt-16rpx flex items-center justify-between border-t border-gray-100 p-t-20rpx dark:border-gray-700">
          <text class="text-26rpx text-gray-600 dark:text-gray-300">
            {{ t('order.appraise.anonymous') }}
          </text>
          <wd-switch v-model="state.anonymous" size="20px" />
        </view>
        <view class="p-t-16rpx flex items-center justify-between">
          <text class="text-26rpx text-gray-600 dark:text-gray-300">
            {{ t('order.appraise.separate') }}
          </text>
          <wd-switch v-model="state.separateMode" size="20px" />
        </view>
        <view v-if="state.separateMode" class="p-t-8rpx text-22rpx text-gray-400">
          {{ t('order.appraise.separateHint') }}
        </view>
      </view>

      <!-- ============ 商品卡 ============ -->
      <view
        v-for="(item, index) in state.items"
        :id="`appraise-item-${index}`"
        :key="item.orderItemId"
        class="appraise-card mt-20rpx rounded-xl bg-white p-20rpx dark:bg-[var(--wot-dark-background2)]"
        :class="{
          'appraise-card--highlight': highlightIndex === index,
          'appraise-card--reviewed': item.reviewed,
        }"
      >
        <view class="flex">
          <view class="h-140rpx w-140rpx flex-none overflow-hidden rounded-8rpx bg-gray-100 dark:bg-gray-700">
            <image v-if="item.picUrl" :src="item.picUrl" class="h-full w-full" mode="aspectFill" />
          </view>
          <view class="ml-20rpx flex flex-1 flex-col overflow-hidden">
            <view class="flex items-start justify-between">
              <wd-text custom-class="flex-1" :lines="2" size="26rpx" color="inherit" :text="item.spuName" />
              <wd-tag v-if="item.reviewed" type="success" plain>
                {{ t('order.appraise.reviewed') }}
              </wd-tag>
              <wd-tag v-else-if="item.skipped" type="warning" plain>
                {{ t('order.appraise.skipped') }}
              </wd-tag>
            </view>
            <view v-if="item.specsInfo" class="p-t-10rpx">
              <wd-text size="24rpx" color="#909090" :text="item.specsInfo" />
            </view>
            <view v-if="item.newbieSplitHint || item.newbieRemainderHint" class="p-t-6rpx">
              <wd-text size="22rpx" color="#909090" :text="item.newbieSplitHint ? t('checkout.goods.newbieFirstUnitHint') : t('checkout.goods.newbieRemainderHint')" />
            </view>
            <view v-if="item.reviewed" class="p-t-10rpx text-22rpx text-gray-400">
              <text v-if="item.systemDefault" class="p-r-8rpx">{{ t('order.appraise.systemDefault') }}</text>
              <text v-if="item.reviewedTime">{{ t('order.appraise.reviewedAt', { time: formatTime(item.reviewedTime) }) }}</text>
            </view>
          </view>
        </view>

        <!-- 已评价：只读星级，不可再评 -->
        <view v-if="item.reviewed" class="flex items-center p-t-14rpx">
          <wd-rate
            v-if="item.reviewedScore !== null"
            :model-value="item.reviewedScore"
            :num="5"
            readonly
            size="16px"
          />
          <text v-else class="text-24rpx text-gray-400">
            {{ t('order.appraise.scoreMissing') }}
          </text>
        </view>

        <!-- 已跳过：可撤销 -->
        <view v-else-if="item.skipped" class="flex items-center justify-between p-t-14rpx">
          <text class="text-24rpx text-gray-400">
            {{ t('order.appraise.skippedHint') }}
          </text>
          <wd-button size="small" plain @click="unskipItem(index)">
            {{ t('order.appraise.undoSkip') }}
          </wd-button>
        </view>

        <!-- 未评：星级 + 单独评价/跳过 -->
        <template v-else>
          <view class="flex items-center justify-between p-t-14rpx">
            <view class="flex items-center">
              <text class="p-r-10rpx text-26rpx text-gray-600 dark:text-gray-300">
                {{ t('order.appraise.itemRating') }}
              </text>
              <wd-rate
                v-model="item.goodsScore"
                :num="5"
                size="20px"
                @change="(event) => onItemScoreChange(index, event)"
              />
            </view>
            <view class="flex items-center">
              <text
                v-if="item.expanded"
                class="px-12rpx text-24rpx text-primary"
                @click="toggleExpand(index)"
              >
                {{ t('order.appraise.collapse') }}
              </text>
              <text
                v-else-if="!state.separateMode"
                class="px-12rpx text-24rpx text-primary"
                @click="toggleExpand(index)"
              >
                {{ t('order.appraise.writeSeparately') }}
              </text>
              <text class="pl-12rpx text-24rpx text-gray-400" @click="skipItem(index)">
                {{ t('order.appraise.skip') }}
              </text>
            </view>
          </view>

          <!-- 单独评价：文字/图片/快捷标签全部选填 -->
          <view v-if="item.expanded || state.separateMode" class="p-t-12rpx">
            <wd-textarea
              v-model="item.content"
              custom-class="wd-textarea"
              :maxlength="APPRAISE_MAX_CONTENT"
              :show-word-limit="true"
              :auto-height="true"
              :placeholder="t('order.appraise.placeholder')"
            />
            <view class="p-t-10rpx">
              <wd-upload
                v-model:file-list="item.uploadList"
                multiple
                :header="headers"
                image-mode="aspectFill"
                :action="action"
                :limit="APPRAISE_MAX_PICS"
              />
            </view>
            <view class="flex items-center p-t-12rpx">
              <text class="text-24rpx text-gray-400">{{ t('order.appraise.quickTags') }}</text>
              <text class="p-l-8rpx text-22rpx text-gray-300">
                {{ t('order.appraise.quickTagsLimit', { count: MAX_SELECTED_TAGS }) }}
              </text>
            </view>
            <view class="flex flex-wrap p-t-8rpx">
              <view
                v-for="tag in tagOptions"
                :key="tag"
                class="mb-12rpx mr-12rpx rounded-full px-20rpx py-8rpx text-24rpx"
                :class="item.tags.includes(tag)
                  ? 'bg-primary text-white'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'"
                @click="toggleItemTag(index, tag)"
              >
                {{ tag }}
              </view>
            </view>
          </view>
        </template>
      </view>

      <wd-gap height="160" />

      <!-- 底部提交 -->
      <view class="fixed bottom-0 left-0 right-0 bg-white p-10rpx pb-[max(env(safe-area-inset-bottom),16rpx)] dark:bg-[var(--wot-dark-background)]">
        <wd-button block type="primary" :disabled="!canSubmit" :loading="state.submitting" @click="onsubmit">
          {{ t('order.appraise.submit') }}
        </wd-button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
:deep(.wd-textarea) {
  padding: 12rpx 16rpx;
  border: 1px solid var(--app-border);
  border-radius: 10rpx;
}

.appraise-card {
  transition: box-shadow 160ms ease;

  &--reviewed {
    opacity: 0.72;
  }

  &--highlight {
    box-shadow: 0 0 0 3rpx var(--theme-color-primary, #ff2237);
  }
}
</style>
