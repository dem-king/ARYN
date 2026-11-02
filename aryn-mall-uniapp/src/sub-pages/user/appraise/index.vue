<script setup lang="ts">
import type { MessageResult } from 'wot-design-uni/components/wd-message-box/types'
import { onShow } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { buildApiUrl } from '@/api/core/api-base-url'
import { parseOpenBoot, rewriteBootUrl } from '@/api/core/boot-url'
import {
  orderAppraiseAdvance,
  orderAppraiseDelete,
  orderAppraiseModify,
} from '@/api/order/orderInfo'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { getUserPage } from '@/sub-pages/api/product/appraise'
import {
  APPRAISE_MAX_CONTENT,
  APPRAISE_MAX_PICS,
  extractUploadedUrls,
  normalizeContent,
  normalizeScore,
} from '@/sub-pages/order/order-appraise/payload'
import { formatTime } from '@/utils/index'
import {
  canAdvanceAppraise,
  canDeleteAppraise,
  canModifyAppraise,
  hasAdvancedAppraise,
  hasAnyAppraiseAction,
  isAnonymousAppraise,
  isSystemDefaultAppraise,
} from './appraise-actions'
import AppraiseImageGrid from './components/AppraiseImageGrid.vue'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'user-appraise',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的评价',
  },
})

/**
 * 我的评价列表项（GoodsAppraise + 后端补充字段）。
 * `goodsScore` 对系统默认评价为 null；后端已对匿名评价脱敏昵称/头像。
 */
interface AppraiseRecord {
  id: string
  spuId: string
  spuName?: string
  spuUrls?: string[]
  orderId: string
  orderItemId: string
  goodsScore?: number | null
  content?: string
  picUrls?: string[]
  tags?: string[]
  isAnonymous?: number | string | null
  appraiseType?: number | string | null
  advanceContent?: string
  advancePicUrls?: string[]
  advanceTime?: string
  modifyTime?: string
  createTime: string
  replyTime?: string
  businessReply?: string
  avatarUrl?: string
  nickname?: string
}

/** 追评/修改弹窗状态：两者共用一份草稿，靠 mode 区分提交接口 */
interface AppraiseDialogState {
  show: boolean
  mode: 'advance' | 'modify'
  record: AppraiseRecord | null
  content: string
  uploadList: any[]
  goodsScore: number
  submitting: boolean
}

// 定义变量
const { t } = useI18n()
const { success, error } = useGlobalToast()
const { confirm } = useGlobalMessage()
const globalLoading = useGlobalLoading()
const authStore = useAuthStore()
const list = ref<AppraiseRecord[]>([])
const pagingRef = ref()

const uploadPath = rewriteBootUrl(
  '/upms/file/app/upload',
  parseOpenBoot(import.meta.env.VITE_OPEN_BOOT),
) ?? '/upms/file/app/upload'
const action = ref(buildApiUrl(uploadPath))
const headers = ref({
  'satoken': authStore.getToken,
  'tenant-id': import.meta.env.VITE_TENANT_ID,
})

const dialog = reactive<AppraiseDialogState>({
  show: false,
  mode: 'advance',
  record: null,
  content: '',
  uploadList: [],
  goodsScore: 5,
  submitting: false,
})

onShow(async () => {
  await nextTick()
  pagingRef.value?.reload()
})

async function queryList(pageNo: number, pageSize: number) {
  try {
    globalLoading.loading(t('common.loading'))
    const response: any = await getUserPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}

/** 通用 confirm（与订单操作组件同一套全局弹窗，避免各页各写 uni.showModal） */
function useConfirm(title: string, msg: string) {
  return new Promise<MessageResult>((resolve) => {
    confirm({
      title,
      msg,
      closeOnClickModal: false,
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      success: resolve,
    })
  })
}

function hasScore(value?: number | string | null) {
  return normalizeScore(value) !== null
}

function scoreOf(value?: number | string | null) {
  return normalizeScore(value) ?? 0
}

function hasAdvance(item: AppraiseRecord) {
  return hasAdvancedAppraise(item) || Boolean(item.advanceContent) || (item.advancePicUrls?.length ?? 0) > 0
}

/** 追评：弹窗填写文字/图片（全部选填，但至少填一项） */
function openAdvance(record: AppraiseRecord) {
  dialog.mode = 'advance'
  dialog.record = record
  dialog.content = ''
  dialog.uploadList = []
  dialog.goodsScore = 5
  dialog.show = true
}

/** 修改：回填文字/图片/星级，提交后仅一次机会用完 */
function openModify(record: AppraiseRecord) {
  dialog.mode = 'modify'
  dialog.record = record
  dialog.content = record.content || ''
  dialog.uploadList = (record.picUrls || []).map(url => ({ url }))
  dialog.goodsScore = normalizeScore(record.goodsScore) ?? 5
  dialog.show = true
}

function closeDialog() {
  if (dialog.submitting)
    return
  dialog.show = false
}

async function submitDialog() {
  const record = dialog.record
  if (!record || dialog.submitting)
    return
  const content = normalizeContent(dialog.content)
  const picUrls = extractUploadedUrls(dialog.uploadList)
  if (dialog.mode === 'advance') {
    if (!content && picUrls.length === 0) {
      error(t('accountSettings.appraise.advanceEmpty'))
      return
    }
    dialog.submitting = true
    globalLoading.loading(t('common.loading'))
    try {
      const body: Record<string, any> = { orderItemId: record.orderItemId }
      if (content)
        body.content = content
      if (picUrls.length > 0)
        body.picUrls = picUrls
      await orderAppraiseAdvance(record.orderId, body)
      dialog.show = false
      success(t('accountSettings.appraise.actionSuccess'))
      pagingRef.value?.reload()
    }
    finally {
      dialog.submitting = false
      globalLoading.close()
    }
    return
  }
  // 修改是整体覆盖：文字/图片/星级都按弹窗当前值提交（允许清空文字）
  dialog.submitting = true
  globalLoading.loading(t('common.loading'))
  try {
    await orderAppraiseModify(record.orderId, record.orderItemId, {
      content,
      picUrls,
      goodsScore: normalizeScore(dialog.goodsScore) ?? 5,
    })
    dialog.show = false
    success(t('accountSettings.appraise.actionSuccess'))
    pagingRef.value?.reload()
  }
  finally {
    dialog.submitting = false
    globalLoading.close()
  }
}

async function onDelete(record: AppraiseRecord) {
  const res = await useConfirm(
    t('accountSettings.appraise.deleteTitle'),
    t('accountSettings.appraise.deleteMessage'),
  )
  if (res.action !== 'confirm')
    return
  globalLoading.loading(t('common.loading'))
  try {
    await orderAppraiseDelete(record.orderId, record.orderItemId)
    success(t('accountSettings.appraise.actionSuccess'))
    pagingRef.value?.reload()
  }
  finally {
    globalLoading.close()
  }
}
</script>

<template>
  <hr-navbar :title="t('accountSettings.appraise.navTitle')" />
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList" @scroll="handlePageScroll">
    <!-- 顶栏覆盖层等高占位：列表整屏滚动，首屏内容从顶栏下沿开始 -->
    <hr-navbar-space />

    <template #empty>
      <view class="p-t-120rpx text-center text-26rpx text-gray-400">
        {{ t('accountSettings.appraise.empty') }}
      </view>
    </template>

    <view
      v-for="(item, index) in list"
      :key="item.id || index"
      class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]"
    >
      <!-- 商品信息 + 评价状态 -->
      <view class="flex">
        <view class="h-[96px] w-[96px] flex-none overflow-hidden rounded-12rpx bg-gray-100 dark:bg-gray-700">
          <wd-img
            v-if="item.spuUrls && item.spuUrls.length > 0"
            width="100%" height="100%" mode="aspectFill" :src="item.spuUrls[0]"
          />
        </view>
        <view class="ml-16rpx flex flex-1 flex-col overflow-hidden">
          <view class="text-26rpx text-gray-800 font-medium dark:text-gray-200">
            {{ item.spuName || t('accountSettings.appraise.productFallback') }}
          </view>
          <view class="mt-8rpx flex flex-wrap items-center gap-8rpx">
            <wd-rate
              v-if="hasScore(item.goodsScore)"
              :model-value="scoreOf(item.goodsScore)" :num="5" readonly size="14px"
            />
            <text v-else class="text-22rpx text-gray-400">
              {{ t('accountSettings.appraise.scoreMissing') }}
            </text>
            <wd-tag v-if="isSystemDefaultAppraise(item)" type="warning" plain>
              {{ t('accountSettings.appraise.systemDefault') }}
            </wd-tag>
            <wd-tag v-if="isAnonymousAppraise(item)" type="default" plain>
              {{ t('accountSettings.appraise.anonymous') }}
            </wd-tag>
          </view>
          <view class="mt-8rpx text-22rpx text-gray-400">
            {{ formatTime(item.createTime) }}
          </view>
        </view>
      </view>

      <!-- 评价内容 -->
      <view class="mt-16rpx whitespace-pre-wrap break-words text-26rpx text-gray-800 leading-relaxed dark:text-gray-200">
        {{ item.content || t('accountSettings.appraise.emptyContent') }}
      </view>

      <!-- 快捷标签 -->
      <view v-if="item.tags && item.tags.length > 0" class="mt-10rpx flex flex-wrap gap-8rpx">
        <text
          v-for="tag in item.tags"
          :key="tag"
          class="rounded-full bg-gray-100 px-16rpx py-4rpx text-22rpx text-gray-600 dark:bg-gray-700 dark:text-gray-300"
        >
          {{ tag }}
        </text>
      </view>

      <!-- 评价图片 -->
      <AppraiseImageGrid :urls="item.picUrls || []" />

      <!-- 追评（后端仅一次，展示已追评内容与时间） -->
      <view
        v-if="hasAdvance(item)"
        class="mt-16rpx rounded-10rpx bg-gray-50 p-16rpx dark:bg-gray-700/50"
      >
        <view class="flex items-center justify-between">
          <text class="text-24rpx text-gray-500 font-medium dark:text-gray-300">
            {{ t('accountSettings.appraise.advanceLabel') }}
          </text>
          <text v-if="item.advanceTime" class="text-22rpx text-gray-400">
            {{ formatTime(item.advanceTime) }}
          </text>
        </view>
        <view
          v-if="item.advanceContent"
          class="mt-8rpx whitespace-pre-wrap break-words text-26rpx text-gray-700 leading-relaxed dark:text-gray-200"
        >
          {{ item.advanceContent }}
        </view>
        <AppraiseImageGrid :urls="item.advancePicUrls || []" />
      </view>

      <!-- 商家回复 -->
      <view
        v-if="item.replyTime && item.businessReply"
        class="mt-18rpx whitespace-pre-wrap break-words rounded-10rpx bg-gray-100 p-16rpx text-24rpx text-gray-700 leading-relaxed dark:bg-gray-700 dark:text-gray-200"
      >
        {{ t('accountSettings.appraise.sellerReply', { reply: item.businessReply }) }}
      </view>

      <!-- 操作：追评 / 修改 / 删除（30 天窗口 + 各仅一次，与后端强校验同口径） -->
      <view
        v-if="hasAnyAppraiseAction(item)"
        class="mt-16rpx flex items-center justify-end gap-16rpx border-t border-gray-100 p-t-16rpx dark:border-gray-700"
      >
        <wd-button v-if="canAdvanceAppraise(item)" size="small" plain @click="openAdvance(item)">
          {{ t('accountSettings.appraise.advance') }}
        </wd-button>
        <wd-button v-if="canModifyAppraise(item)" size="small" plain @click="openModify(item)">
          {{ t('accountSettings.appraise.modify') }}
        </wd-button>
        <wd-button v-if="canDeleteAppraise(item)" size="small" plain @click="onDelete(item)">
          {{ t('accountSettings.appraise.delete') }}
        </wd-button>
      </view>
    </view>
  </z-paging>

  <!-- 追评 / 修改弹窗 -->
  <wd-popup
    v-model="dialog.show"
    position="bottom"
    :z-index="1100"
    :safe-area-inset-bottom="true"
    custom-style="border-radius: 28rpx 28rpx 0 0; overflow: hidden;"
    @close="closeDialog"
  >
    <view class="p-24rpx">
      <view class="pb-20rpx text-center text-30rpx text-gray-800 font-bold dark:text-gray-100">
        {{ dialog.mode === 'advance' ? t('accountSettings.appraise.advanceTitle') : t('accountSettings.appraise.modifyTitle') }}
      </view>

      <view v-if="dialog.mode === 'modify'" class="flex items-center justify-between pb-20rpx">
        <text class="text-26rpx text-gray-600 dark:text-gray-300">
          {{ t('accountSettings.appraise.ratingLabel') }}
        </text>
        <wd-rate v-model="dialog.goodsScore" :num="5" size="20px" />
      </view>

      <wd-textarea
        v-model="dialog.content"
        custom-class="wd-textarea"
        :maxlength="APPRAISE_MAX_CONTENT"
        :show-word-limit="true"
        :auto-height="true"
        :placeholder="dialog.mode === 'advance'
          ? t('accountSettings.appraise.advancePlaceholder')
          : t('accountSettings.appraise.contentPlaceholder')"
      />

      <view class="mt-16rpx">
        <view class="pb-8rpx text-24rpx text-gray-400">
          {{ dialog.mode === 'advance' ? t('accountSettings.appraise.advancePics') : t('accountSettings.appraise.picsLabel') }}
        </view>
        <wd-upload
          v-model:file-list="dialog.uploadList"
          multiple
          :header="headers"
          image-mode="aspectFill"
          :action="action"
          :limit="APPRAISE_MAX_PICS"
        />
      </view>

      <view class="mt-24rpx flex gap-20rpx">
        <wd-button block plain :disabled="dialog.submitting" @click="closeDialog">
          {{ t('common.cancel') }}
        </wd-button>
        <wd-button block type="primary" :loading="dialog.submitting" @click="submitDialog">
          {{ dialog.mode === 'advance' ? t('accountSettings.appraise.advanceSubmit') : t('accountSettings.appraise.modifySubmit') }}
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
:deep(.wd-textarea) {
  padding: 12rpx 16rpx;
  border: 1px solid #bfbfbf;
  border-radius: 10rpx;
}
</style>
