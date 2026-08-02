<script setup lang="ts">
import type { AppDiscountGoodsVO, AppDiscountVO } from '@/api/promotion'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { getDiscountActivities } from '@/api/promotion'
import { useGlobalLoading } from '@/composables/useGlobalLoading'

definePage({
  name: 'discount',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时折扣',
  },
})

const globalLoading = useGlobalLoading()

/** 折扣活动列表 */
const activityList = ref<AppDiscountVO[]>([])
/** 是否还有更多 */
const noMore = ref(false)
/** 分页参数 */
const currentPage = ref(1)
const PAGE_SIZE = 10

/** 倒计时状态映射（按活动ID） */
const countdownMap = reactive<Record<string, {
  hours: number
  minutes: number
  seconds: number
  finished: boolean
}>>({})

let countdownTimer: ReturnType<typeof setInterval> | null = null

onLoad(async () => {
  await loadActivities(true)
  startCountdown()
})

onUnload(() => {
  stopCountdown()
})

/** 加载折扣活动列表 */
async function loadActivities(isRefresh = false) {
  if (isRefresh) {
    currentPage.value = 1
    noMore.value = false
  }
  if (noMore.value && !isRefresh)
    return

  if (isRefresh)
    globalLoading.loading('加载中...')

  try {
    const response = await getDiscountActivities({
      current: currentPage.value,
      size: PAGE_SIZE,
    })
    const records = response.records || []
    if (isRefresh) {
      activityList.value = records
    }
    else {
      activityList.value = [...activityList.value, ...records]
    }
    if (records.length < PAGE_SIZE || activityList.value.length >= response.total) {
      noMore.value = true
    }
    // 初始化倒计时
    activityList.value.forEach((activity) => {
      if (!countdownMap[activity.activityId]) {
        countdownMap[activity.activityId] = {
          hours: 0,
          minutes: 0,
          seconds: 0,
          finished: false,
        }
      }
    })
    updateAllCountdowns()
  }
  catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
  finally {
    if (isRefresh)
      globalLoading.close()
  }
}

/** 下拉刷新 */
async function onRefresh() {
  await loadActivities(true)
}

/** 上拉加载更多 */
async function onLoadMore() {
  if (noMore.value)
    return
  currentPage.value++
  await loadActivities(false)
}

/** 启动全局倒计时 */
function startCountdown() {
  stopCountdown()
  updateAllCountdowns()
  countdownTimer = setInterval(updateAllCountdowns, 1000)
}

/** 停止倒计时 */
function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 更新所有活动倒计时 */
function updateAllCountdowns() {
  activityList.value.forEach((activity) => {
    const state = countdownMap[activity.activityId]
    if (!state)
      return
    const targetTime = activity.status === 1 ? activity.endTime : activity.startTime
    if (!targetTime) {
      state.finished = true
      return
    }
    const diff = new Date(targetTime).getTime() - Date.now()
    if (diff <= 0) {
      state.hours = 0
      state.minutes = 0
      state.seconds = 0
      state.finished = true
      return
    }
    state.hours = Math.floor(diff / (1000 * 60 * 60))
    state.minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    state.seconds = Math.floor((diff % (1000 * 60)) / 1000)
    state.finished = false
  })
}

/** 获取活动倒计时显示文本 */
function getCountdownText(activity: AppDiscountVO): string {
  const state = countdownMap[activity.activityId]
  if (!state || state.finished)
    return '已结束'
  const h = String(state.hours).padStart(2, '0')
  const m = String(state.minutes).padStart(2, '0')
  const s = String(state.seconds).padStart(2, '0')
  return `${h}:${m}:${s}`
}

/** 倒计时标签 */
function getCountdownLabel(activity: AppDiscountVO): string {
  return activity.status === 1 ? '距结束' : '距开始'
}

/** 折扣标签文本 */
function discountTagText(activity: AppDiscountVO): string {
  switch (activity.discountType) {
    case 1:
      return `${Math.round(activity.discountValue * 10)}折`
    case 2:
      return `减¥${activity.discountValue}`
    case 3:
      return '一口价'
    default:
      return '折扣'
  }
}

/** 折扣标签颜色类 */
function discountTagClass(activity: AppDiscountVO): string {
  switch (activity.discountType) {
    case 1:
      return 'tag-discount'
    case 2:
      return 'tag-reduce'
    case 3:
      return 'tag-fixed'
    default:
      return 'tag-default'
  }
}

/** 跳转商品详情 */
function goGoodsDetail(goods: AppDiscountGoodsVO) {
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}`,
  })
}

/** 活动状态文本 */
function statusText(status: number): string {
  return status === 0 ? '即将开始' : status === 1 ? '进行中' : '已结束'
}
</script>

<template>
  <view class="discount-page">
    <hr-navbar title="限时折扣" />

    <scroll-view
      scroll-y
      class="content-scroll"
      refresher-enabled
      :refresher-triggered="false"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <!-- 空状态 -->
      <view v-if="activityList.length === 0" class="empty-state">
        <view class="empty-text">
          暂无折扣活动
        </view>
      </view>

      <!-- 按活动分组展示 -->
      <view
        v-for="activity in activityList"
        :key="activity.activityId"
        class="activity-section"
      >
        <!-- 活动头部 -->
        <view class="activity-header">
          <view class="activity-title-row">
            <view class="activity-name">
              {{ activity.activityName }}
            </view>
            <view class="discount-tag" :class="discountTagClass(activity)">
              {{ discountTagText(activity) }}
            </view>
            <view class="activity-status" :class="`status-${activity.status}`">
              {{ statusText(activity.status) }}
            </view>
          </view>
          <view class="activity-countdown">
            <text class="countdown-label">
              {{ getCountdownLabel(activity) }}
            </text>
            <text class="countdown-value">
              {{ getCountdownText(activity) }}
            </text>
          </view>
        </view>

        <!-- 折扣商品列表 -->
        <view class="goods-list">
          <view
            v-for="goods in activity.goodsList"
            :key="goods.skuId"
            class="goods-card"
            @click="goGoodsDetail(goods)"
          >
            <image
              class="goods-image"
              :src="goods.goodsImage"
              mode="aspectFill"
            />
            <view class="goods-info">
              <view class="goods-name">
                {{ goods.goodsName }}
              </view>
              <view class="price-row">
                <view class="discount-price">
                  <text class="price-symbol">
                    ¥
                  </text>
                  <text class="price-value">
                    {{ goods.discountPrice }}
                  </text>
                </view>
                <view class="original-price">
                  ¥{{ goods.originalPrice }}
                </view>
              </view>
              <view class="goods-tag" :class="discountTagClass(activity)">
                {{ discountTagText(activity) }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 加载更多提示 -->
      <view v-if="activityList.length > 0" class="load-more">
        <text v-if="noMore">
          没有更多了
        </text>
        <text v-else>
          加载中...
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.discount-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f5f5;
}

.content-scroll {
  flex: 1;
  padding: 16rpx;
}

// 空状态
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  .empty-text {
    font-size: 28rpx;
    color: #999;
  }
}

// 活动分组
.activity-section {
  margin-bottom: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

// 活动头部
.activity-header {
  background: linear-gradient(135deg, #ff9a56, #ff6b35);
  padding: 24rpx 32rpx;
  color: #fff;

  .activity-title-row {
    display: flex;
    align-items: center;
    gap: 12rpx;

    .activity-name {
      font-size: 30rpx;
      font-weight: 700;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .discount-tag {
      font-size: 22rpx;
      padding: 4rpx 16rpx;
      border-radius: 20rpx;
      background: rgba(255, 255, 255, 0.25);
      white-space: nowrap;
    }

    .activity-status {
      font-size: 22rpx;
      padding: 4rpx 16rpx;
      border-radius: 4rpx;
      white-space: nowrap;

      &.status-0 {
        background: rgba(255, 255, 255, 0.25);
      }

      &.status-1 {
        background: #fff;
        color: #ff4500;
      }

      &.status-2 {
        background: rgba(0, 0, 0, 0.2);
      }
    }
  }

  .activity-countdown {
    display: flex;
    align-items: center;
    gap: 12rpx;
    margin-top: 12rpx;
    font-size: 24rpx;
    opacity: 0.9;

    .countdown-value {
      font-weight: 700;
    }
  }
}

// 商品列表
.goods-list {
  padding: 16rpx;
}

// 商品卡片
.goods-card {
  display: flex;
  padding: 20rpx;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  .goods-image {
    width: 180rpx;
    height: 180rpx;
    border-radius: 12rpx;
    flex-shrink: 0;
    background: #f5f5f5;
  }

  .goods-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    margin-left: 24rpx;
    min-width: 0;
  }

  .goods-name {
    font-size: 28rpx;
    color: #333;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .price-row {
    display: flex;
    align-items: baseline;
    gap: 16rpx;
    margin-top: 12rpx;

    .discount-price {
      color: #ff4500;

      .price-symbol {
        font-size: 24rpx;
      }

      .price-value {
        font-size: 36rpx;
        font-weight: 700;
      }
    }

    .original-price {
      font-size: 24rpx;
      color: #999;
      text-decoration: line-through;
    }
  }

  // 折扣标签
  .goods-tag {
    align-self: flex-start;
    font-size: 20rpx;
    padding: 4rpx 12rpx;
    border-radius: 4rpx;
    margin-top: 12rpx;

    &.tag-discount {
      color: #ff4500;
      background: #fff2f0;
    }

    &.tag-reduce {
      color: #ff8c00;
      background: #fff7e6;
    }

    &.tag-fixed {
      color: #1890ff;
      background: #e6f7ff;
    }

    &.tag-default {
      color: #666;
      background: #f5f5f5;
    }
  }
}

// 加载更多
.load-more {
  text-align: center;
  padding: 32rpx 0;
  font-size: 24rpx;
  color: #999;
}
</style>