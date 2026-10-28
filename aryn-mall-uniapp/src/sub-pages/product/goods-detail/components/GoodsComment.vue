<script setup lang="ts">
import { computed, getCurrentInstance, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage as getAppraisePage, getCount } from '@/sub-pages/api/product/appraise'
import { formatTime } from '@/utils/index'

interface Props {
  spuId: string
}
interface AppraiseCount {
  allCount: number
  goodCount: number
  badCount: number
  negativeCount: number
  imageCount: number
}
interface Emits {
  (e: 'loaded'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const router = useRouter()
const { t, locale } = useI18n()
const goodsAppraiseList = ref<any[]>([])
const appraiseCount = ref<AppraiseCount>(emptyCount())

function emptyCount(): AppraiseCount {
  return {
    allCount: 0,
    goodCount: 0,
    badCount: 0,
    negativeCount: 0,
    imageCount: 0,
  }
}

watch(
  () => props.spuId,
  (newValue) => {
    appraiseCount.value = emptyCount()
    goodsAppraiseList.value = []
    if (newValue)
      loadReviews(newValue)
  },
  { immediate: true },
)

/**
 * 评价数量与列表并行拉取：两者互不依赖，任一失败都只影响自己那一块，
 * 不把整段评价打成空白。加载结束通知父页面重新量测锚点——
 * 评价卡高度会随「有评价/空态」变化，父页面首次量测可能发生在数据回来之前。
 */
async function loadReviews(id: string) {
  const [countResult, pageResult] = await Promise.allSettled([
    getCount({ spuId: id }),
    getAppraisePage({
      spuId: id,
      current: 1,
      size: 2,
      desc: 'create_time',
    }),
  ])
  if (countResult.status === 'fulfilled') {
    const count = countResult.value || {}
    appraiseCount.value = {
      allCount: Number(count.allCount) || 0,
      goodCount: Number(count.goodCount) || 0,
      badCount: Number(count.badCount) || 0,
      negativeCount: Number(count.negativeCount) || 0,
      imageCount: Number(count.imageCount) || 0,
    }
  }
  if (pageResult.status === 'fulfilled')
    goodsAppraiseList.value = pageResult.value?.records || []
  emit('loaded')
}

const reviewTotal = computed(() => {
  const total = Number(appraiseCount.value.allCount) || 0
  return total || goodsAppraiseList.value.length
})

const hasReviews = computed(() => reviewTotal.value > 0)

/** 好评率保留一位小数，整数不补 .0；数量接口缺失时显示占位符 */
const positiveRateText = computed(() => {
  const total = Number(appraiseCount.value.allCount) || 0
  const good = Number(appraiseCount.value.goodCount) || 0
  if (total <= 0)
    return '—'
  const rate = Math.round((good / total) * 1000) / 10
  return `${Number.isInteger(rate) ? rate : rate.toFixed(1)}%`
})

const filterOptions = computed(() => [
  { value: 'good', label: t('product.review.good'), count: Number(appraiseCount.value.goodCount) || 0 },
  { value: 'neutral', label: t('product.review.neutral'), count: Number(appraiseCount.value.badCount) || 0 },
  { value: 'bad', label: t('product.review.bad'), count: Number(appraiseCount.value.negativeCount) || 0 },
  { value: 'pic', label: t('product.review.withImage'), count: Number(appraiseCount.value.imageCount) || 0 },
])

function formatCount(value: number | string | null | undefined) {
  const count = Number(value) || 0
  if (count >= 10000) {
    // 中文按「万」，英文按「K」：同一数量级用各自习惯的短单位，避免窄屏换行
    const english = locale.value === 'en-US'
    const scaled = count / (english ? 1000 : 10000)
    return `${scaled.toFixed(1).replace(/\.0$/, '')}${english ? 'K' : '万'}`
  }
  return String(count)
}

function scoreOf(value: any) {
  const score = Number(value)
  return Number.isFinite(score) ? score : 0
}

function avatarText(nickname: string) {
  return String(nickname || t('product.review.anonymous')).slice(0, 1)
}

function onRateTypeChange(value: string) {
  toAppraise(value)
}

const proxy = getCurrentInstance()?.proxy

// 获取scroll-comment元素位置的方法
function getScrollCommentRect() {
  return new Promise((resolve, reject) => {
    if (!proxy) {
      reject(new Error('组件实例 proxy 为 null'))
      return
    }
    const query = uni.createSelectorQuery().in(proxy)

    query
      .select('#scroll-comment')
      .boundingClientRect((data) => {
        if (data) {
          resolve(data)
        }
        else {
          reject(new Error('Element not found or query failed'))
        }
      })
      .exec()
  })
}

function toAppraise(type = 'all') {
  router.push({
    name: 'goods-appraise',
    params: {
      spuId: props.spuId,
      rateType: type,
    },
  })
}

function previewImage(index: number, urls: string[]) {
  if (!urls?.length)
    return
  uni.previewImage({
    current: index,
    urls,
  })
}

// 暴露方法给父组件
defineExpose({
  getScrollCommentRect,
})
</script>

<template>
  <view id="scroll-comment" class="comment-card bg-white dark:bg-[var(--wot-dark-background2)]">
    <view class="comment-head">
      <view class="comment-head__title">
        <text>{{ t('product.reviews') }}</text>
        <text class="comment-head__count">({{ formatCount(reviewTotal) }})</text>
      </view>
      <view v-if="hasReviews" class="comment-head__more" @click="toAppraise()">
        <text>{{ t('product.viewAll') }}</text>
        <wd-icon :size="12" name="arrow-right" color="var(--wot-cell-arrow-color)" />
      </view>
    </view>

    <template v-if="hasReviews">
      <view v-if="reviewTotal > 0" class="comment-summary">
        <view class="comment-summary__rate">
          <view class="comment-summary__rate-line">
            <wd-icon name="star-filled" size="20rpx" color="var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237))" />
            <text class="comment-summary__rate-value">
              {{ positiveRateText }}
            </text>
          </view>
          <text class="comment-summary__rate-label">{{ t('product.positiveRate') }}</text>
        </view>
        <view class="comment-summary__grid">
          <view
            v-for="option in filterOptions"
            :key="option.value"
            class="comment-chip"
            hover-class="comment-chip--pressed"
            @click="onRateTypeChange(option.value)"
          >
            <text class="comment-chip__label">{{ option.label }}</text>
            <text class="comment-chip__count">{{ formatCount(option.count) }}</text>
          </view>
        </view>
      </view>

      <view class="comment-list">
        <view
          v-for="(item, index) in goodsAppraiseList"
          :key="item.id || index"
          class="comment-item"
        >
          <view class="comment-item__head">
            <view class="comment-item__user">
              <view class="comment-item__avatar">
                <wd-img
                  v-if="item.avatarUrl" width="32px" height="32px" round
                  :src="item.avatarUrl"
                />
                <text v-else class="comment-item__avatar-text">
                  {{ avatarText(item.nickname) }}
                </text>
              </view>
              <view class="comment-item__user-meta">
                <text class="comment-item__nickname">
                  {{ item.nickname || t('product.review.anonymous') }}
                </text>
                <view v-if="scoreOf(item.goodsScore) > 0" class="comment-item__stars">
                  <wd-rate :model-value="scoreOf(item.goodsScore)" :num="5" readonly size="12px" />
                </view>
              </view>
            </view>
            <text class="comment-item__time">{{ formatTime(item.createTime) }}</text>
          </view>

          <view class="comment-item__content-row">
            <view class="comment-item__content">
              {{ item.content || t('product.review.emptyContent') }}
            </view>
            <!-- 1~2 张图放在文字右侧，避免短评被拉成一大块空白 -->
            <view
              v-if="item.picUrls && item.picUrls.length > 0 && item.picUrls.length <= 2"
              class="comment-item__side-pics"
            >
              <view
                v-for="(pic, picIndex) in item.picUrls.slice(0, 2)"
                :key="picIndex"
                class="comment-item__side-pic"
              >
                <wd-img
                  width="100%" height="100%" mode="aspectFill" radius="12rpx"
                  :src="pic" @click="previewImage(picIndex, item.picUrls)"
                />
              </view>
            </view>
          </view>

          <!-- 3 张及以上：一行三张，最后一张叠加剩余数量 -->
          <view
            v-if="item.picUrls && item.picUrls.length >= 3"
            class="comment-item__grid-pics"
          >
            <view
              v-for="(pic, picIndex) in item.picUrls.slice(0, 3)"
              :key="picIndex"
              class="comment-item__grid-pic"
            >
              <wd-img
                width="100%" height="100%" mode="aspectFill" radius="12rpx"
                :src="pic" @click="previewImage(picIndex, item.picUrls)"
              />
              <view
                v-if="picIndex === 2 && item.picUrls.length > 3"
                class="comment-item__grid-more"
              >
                +{{ item.picUrls.length - 3 }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <view
        v-if="reviewTotal > 2"
        class="comment-all"
        hover-class="comment-all--pressed"
        @click="toAppraise()"
      >
        <text>{{ t('product.review.viewAll', { count: formatCount(reviewTotal) }) }}</text>
        <wd-icon :size="12" name="arrow-right" color="var(--wot-cell-arrow-color)" />
      </view>
    </template>

    <view v-else class="comment-empty">
      <view class="comment-empty__icon">
        <wd-icon name="chat" size="44rpx" color="#b5bac3" />
      </view>
      <view class="comment-empty__title">
        {{ t('product.review.empty') }}
      </view>
      <view class="comment-empty__desc">
        {{ t('product.review.emptyDesc') }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.comment-card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
}

.comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  &__title {
    display: flex;
    align-items: baseline;
    gap: 8rpx;
    color: #1f2329;
    font-size: 28rpx;
    font-weight: 600;
  }

  &__count {
    color: #8a8f99;
    font-size: 22rpx;
    font-weight: 400;
  }

  &__more {
    display: flex;
    align-items: center;
    gap: 4rpx;
    color: #8a8f99;
    font-size: 22rpx;
  }
}

/**
 * 好评率区：左侧大数字承担「一眼可信」，右侧 2x2 标签承担「快速筛选」。
 * 浅灰底把评价摘要与下方纯白评价正文分开，避免整张卡没有层级。
 */
.comment-summary {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 20rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f7f8fa;

  &__rate {
    display: flex;
    width: 148rpx;
    flex: none;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
  }

  &__rate-line {
    display: flex;
    align-items: center;
    gap: 4rpx;
  }

  &__rate-value {
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    font-size: 36rpx;
    font-weight: 700;
    line-height: 1.1;
  }

  &__rate-label {
    color: #8a8f99;
    font-size: 22rpx;
  }

  &__grid {
    display: grid;
    flex: 1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10rpx;
  }
}

.comment-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 16rpx;
  border: 1rpx solid #eef0f3;
  border-radius: 10rpx;
  background: #fff;
  transition: transform 120ms ease, opacity 120ms ease;

  &__label {
    color: #5f6673;
    font-size: 22rpx;
  }

  &__count {
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    font-size: 22rpx;
    font-weight: 600;
  }

  &--pressed {
    opacity: 0.72;
    transform: scale(0.97);
  }
}

.comment-list {
  margin-top: 24rpx;
}

.comment-item {
  & + & {
    margin-top: 24rpx;
    padding-top: 24rpx;
    border-top: 1rpx solid #f2f3f5;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16rpx;
  }

  &__user {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 14rpx;
  }

  &__avatar {
    display: flex;
    width: 32px;
    height: 32px;
    flex: none;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    border-radius: 50%;
    background: #f0f2f5;
  }

  &__avatar-text {
    color: #8a8f99;
    font-size: 24rpx;
    font-weight: 600;
  }

  &__user-meta {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 2rpx;
  }

  &__nickname {
    overflow: hidden;
    color: #3a3f47;
    font-size: 24rpx;
    font-weight: 500;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__stars {
    transform: scale(0.86);
    transform-origin: left center;
  }

  &__time {
    flex: none;
    color: #a8abb2;
    font-size: 20rpx;
    line-height: 32px;
  }

  &__content-row {
    display: flex;
    align-items: flex-start;
    gap: 16rpx;
    margin-top: 14rpx;
  }

  &__content {
    display: -webkit-box;
    overflow: hidden;
    flex: 1;
    color: #3a3f47;
    font-size: 26rpx;
    line-height: 1.6;
    word-break: break-word;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    white-space: pre-wrap;
  }

  &__side-pics {
    display: flex;
    flex: none;
    gap: 8rpx;
  }

  &__side-pic {
    width: 64px;
    height: 64px;
    overflow: hidden;
    border-radius: 12rpx;
    background: #f5f6f8;
  }

  &__grid-pics {
    display: flex;
    gap: 8rpx;
    margin-top: 16rpx;
  }

  &__grid-pic {
    position: relative;
    width: 72px;
    height: 72px;
    overflow: hidden;
    border-radius: 12rpx;
    background: #f5f6f8;
  }

  &__grid-more {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgb(0 0 0 / 42%);
    color: #fff;
    font-size: 24rpx;
    font-weight: 600;
  }
}

.comment-all {
  display: flex;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  margin-top: 24rpx;
  border-radius: 999rpx;
  background: #f7f8fa;
  color: #5f6673;
  font-size: 22rpx;
  transition: opacity 120ms ease;

  &--pressed {
    opacity: 0.72;
  }
}

.comment-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36rpx 0 40rpx;

  &__icon {
    display: flex;
    width: 88rpx;
    height: 88rpx;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: #f5f6f8;
  }

  &__title {
    margin-top: 16rpx;
    color: #3a3f47;
    font-size: 26rpx;
    font-weight: 500;
  }

  &__desc {
    margin-top: 6rpx;
    color: #a8abb2;
    font-size: 22rpx;
  }
}
</style>
