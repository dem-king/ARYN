<script setup lang="ts">
import type { AppSeckillGoodsVO, AppSeckillVO } from '@/api/promotion'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { getSessionGoods, getSeckillSessions } from '@/api/promotion'
import { useGlobalLoading } from '@/composables/useGlobalLoading'

definePage({
  name: 'seckill',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时秒杀',
  },
})

const globalLoading = useGlobalLoading()

/** 场次列表 */
const sessionList = ref<AppSeckillVO[]>([])
/** 当前选中场次索引 */
const currentSessionIndex = ref(0)
/** 当前场次商品列表 */
const goodsList = ref<AppSeckillGoodsVO[]>([])
/** 是否还有更多商品（上拉加载） */
const noMore = ref(true)

/** 倒计时状态 */
const countdown = reactive({
  hours: 0,
  minutes: 0,
  seconds: 0,
  finished: false,
})
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 当前场次 */
const currentSession = computed<AppSeckillVO | null>(() => {
  return sessionList.value[currentSessionIndex.value] || null
})

/** 倒计时显示文本 */
const countdownText = computed(() => {
  if (countdown.finished)
    return '已结束'
  const h = String(countdown.hours).padStart(2, '0')
  const m = String(countdown.minutes).padStart(2, '0')
  const s = String(countdown.seconds).padStart(2, '0')
  return `${h}:${m}:${s}`
})

/** 倒计时标签 */
const countdownLabel = computed(() => {
  const session = currentSession.value
  if (!session)
    return ''
  // 进行中 → 距结束；未开始 → 距开始
  return session.status === 1 ? '距本场结束' : '距本场开始'
})

onLoad(async () => {
  await loadSessions()
})

onUnload(() => {
  stopCountdown()
})

/** 加载场次列表 */
async function loadSessions() {
  globalLoading.loading('加载中...')
  try {
    const data = await getSeckillSessions()
    sessionList.value = data || []
    if (sessionList.value.length > 0) {
      // 默认选中第一个进行中的场次，否则选第一个
      const activeIndex = sessionList.value.findIndex(s => s.status === 1)
      currentSessionIndex.value = activeIndex >= 0 ? activeIndex : 0
      await selectSession(currentSessionIndex.value)
    }
    else {
      goodsList.value = []
    }
  }
  catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
  finally {
    globalLoading.close()
  }
}

/** 选择场次 */
async function selectSession(index: number) {
  currentSessionIndex.value = index
  const session = sessionList.value[index]
  if (!session)
    return
  goodsList.value = []
  await loadSessionGoods(session.sessionId)
  startCountdown()
}

/** 加载场次商品（后端返回全部商品，无分页） */
async function loadSessionGoods(sessionId: string) {
  try {
    const session = await getSessionGoods(sessionId)
    goodsList.value = session.goodsList || []
    noMore.value = true
  }
  catch {
    uni.showToast({ title: '加载商品失败', icon: 'none' })
  }
}

/** 下拉刷新 */
async function onRefresh() {
  await loadSessions()
}

/** 上拉加载更多（后端无分页，保留空实现以兼容模板事件） */
async function onLoadMore() {
  // 后端一次性返回全部商品，无需分页加载
}

/** 启动倒计时 */
function startCountdown() {
  stopCountdown()
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

/** 停止倒计时 */
function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 更新倒计时数值 */
function updateCountdown() {
  const session = currentSession.value
  if (!session) {
    countdown.finished = true
    return
  }
  // 进行中算距结束时间，未开始算距开始时间
  const targetTime = session.status === 1 ? session.endTime : session.startTime
  if (!targetTime) {
    countdown.finished = true
    return
  }
  const target = new Date(targetTime).getTime()
  const diff = target - Date.now()
  if (diff <= 0) {
    countdown.hours = 0
    countdown.minutes = 0
    countdown.seconds = 0
    countdown.finished = true
    stopCountdown()
    // 倒计时结束，刷新场次列表
    loadSessions()
    return
  }
  countdown.hours = Math.floor(diff / (1000 * 60 * 60))
  countdown.minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  countdown.seconds = Math.floor((diff % (1000 * 60)) / 1000)
  countdown.finished = false
}

/** 库存进度百分比 */
function stockPercent(goods: AppSeckillGoodsVO): number {
  if (!goods.seckillStock || goods.seckillStock <= 0)
    return 0
  const remaining = goods.remainingStock || 0
  const percent = Math.round((remaining / goods.seckillStock) * 100)
  return Math.max(0, Math.min(100, percent))
}

/** 是否已抢完 */
function isSoldOut(goods: AppSeckillGoodsVO): boolean {
  return (goods.remainingStock || 0) <= 0
}

/** 抢购按钮文本 */
function buyButtonText(goods: AppSeckillGoodsVO): string {
  const session = currentSession.value
  if (!session)
    return '不可抢'
  if (session.status === 0)
    return '即将开始'
  if (session.status === 2)
    return '已结束'
  if (isSoldOut(goods))
    return '已抢完'
  return '立即抢购'
}

/** 抢购按钮是否可点击 */
function canBuy(goods: AppSeckillGoodsVO): boolean {
  const session = currentSession.value
  if (!session || session.status !== 1)
    return false
  return !isSoldOut(goods)
}

/** 处理抢购 */
function handleBuy(goods: AppSeckillGoodsVO) {
  if (!canBuy(goods))
    return
  // 跳转到商品详情页，由详情页发起秒杀下单
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}&skuId=${goods.skuId}&from=seckill`,
  })
}

/** 跳转商品详情 */
function goGoodsDetail(goods: AppSeckillGoodsVO) {
  uni.navigateTo({
    url: `/sub-pages/product/goods-detail/index?id=${goods.spuId}`,
  })
}
</script>

<template>
  <view class="seckill-page">
    <hr-navbar title="限时秒杀" />

    <!-- 场次标签栏 -->
    <scroll-view scroll-x class="session-bar" :show-scrollbar="false">
      <view class="session-list">
        <view
          v-for="(session, index) in sessionList"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: index === currentSessionIndex }"
          @click="selectSession(index)"
        >
          <view class="session-name">
            {{ session.sessionName }}
          </view>
          <view class="session-status">
            {{ session.status === 0 ? '即将开始' : session.status === 1 ? '抢购中' : '已结束' }}
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 倒计时区域 -->
    <view v-if="currentSession" class="countdown-bar">
      <view class="countdown-label">
        {{ countdownLabel }}
      </view>
      <view class="countdown-time">
        <text class="time-block">
          {{ String(countdown.hours).padStart(2, '0') }}
        </text>
        <text class="time-sep">
          :
        </text>
        <text class="time-block">
          {{ String(countdown.minutes).padStart(2, '0') }}
        </text>
        <text class="time-sep">
          :
        </text>
        <text class="time-block">
          {{ String(countdown.seconds).padStart(2, '0') }}
        </text>
      </view>
    </view>

    <!-- 商品列表 -->
    <scroll-view
      scroll-y
      class="goods-scroll"
      refresher-enabled
      :refresher-triggered="false"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <!-- 空状态 -->
      <view v-if="goodsList.length === 0" class="empty-state">
        <view class="empty-text">
          暂无秒杀商品
        </view>
      </view>

      <!-- 商品卡片 -->
      <view
        v-for="goods in goodsList"
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
            <view class="seckill-price">
              <text class="price-symbol">
                ¥
              </text>
              <text class="price-value">
                {{ goods.seckillPrice }}
              </text>
            </view>
            <view class="original-price">
              ¥{{ goods.originalPrice }}
            </view>
          </view>
          <!-- 库存进度条 -->
          <view class="stock-bar">
            <view class="stock-track">
              <view
                class="stock-fill"
                :style="{ width: `${stockPercent(goods)}%` }"
              />
            </view>
            <view class="stock-text">
              <text v-if="isSoldOut(goods)">
                已抢完
              </text>
              <text v-else>
                剩{{ goods.remainingStock }}件
              </text>
            </view>
          </view>
          <!-- 抢购按钮 -->
          <view
            class="buy-btn"
            :class="{ disabled: !canBuy(goods) }"
            @click.stop="handleBuy(goods)"
          >
            {{ buyButtonText(goods) }}
          </view>
        </view>
      </view>

      <!-- 加载更多提示 -->
      <view v-if="goodsList.length > 0" class="load-more">
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
.seckill-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f5f5;
}

// 场次标签栏
.session-bar {
  background: linear-gradient(135deg, #ff6b35, #ff4500);
  white-space: nowrap;
  padding: 16rpx 0;

  .session-list {
    display: inline-flex;
    padding: 0 16rpx;
  }

  .session-item {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-width: 160rpx;
    padding: 12rpx 24rpx;
    margin-right: 16rpx;
    border-radius: 12rpx;
    opacity: 0.7;
    transition: all 0.2s;

    &.active {
      opacity: 1;
      background: rgba(255, 255, 255, 0.25);
    }

    .session-name {
      font-size: 28rpx;
      font-weight: 600;
      color: #fff;
    }

    .session-status {
      font-size: 22rpx;
      color: #fff;
      margin-top: 4rpx;
    }
  }
}

// 倒计时区域
.countdown-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  background: #fff;
  padding: 24rpx 32rpx;

  .countdown-label {
    font-size: 26rpx;
    color: #666;
  }

  .countdown-time {
    display: flex;
    align-items: center;
    gap: 4rpx;

    .time-block {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 48rpx;
      height: 48rpx;
      background: #333;
      color: #fff;
      font-size: 28rpx;
      font-weight: 700;
      border-radius: 8rpx;
      padding: 0 8rpx;
    }

    .time-sep {
      font-size: 28rpx;
      font-weight: 700;
      color: #333;
    }
  }
}

// 商品列表
.goods-scroll {
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

// 商品卡片
.goods-card {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);

  .goods-image {
    width: 200rpx;
    height: 200rpx;
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

    .seckill-price {
      color: #ff4500;

      .price-symbol {
        font-size: 24rpx;
      }

      .price-value {
        font-size: 40rpx;
        font-weight: 700;
      }
    }

    .original-price {
      font-size: 24rpx;
      color: #999;
      text-decoration: line-through;
    }
  }

  // 库存进度条
  .stock-bar {
    display: flex;
    align-items: center;
    gap: 12rpx;
    margin-top: 12rpx;

    .stock-track {
      flex: 1;
      height: 12rpx;
      background: #f0f0f0;
      border-radius: 6rpx;
      overflow: hidden;

      .stock-fill {
        height: 100%;
        background: linear-gradient(90deg, #ff6b35, #ff4500);
        border-radius: 6rpx;
        transition: width 0.3s;
      }
    }

    .stock-text {
      font-size: 22rpx;
      color: #ff4500;
      white-space: nowrap;
    }
  }

  // 抢购按钮
  .buy-btn {
    align-self: flex-end;
    background: linear-gradient(135deg, #ff6b35, #ff4500);
    color: #fff;
    font-size: 26rpx;
    font-weight: 600;
    padding: 12rpx 40rpx;
    border-radius: 32rpx;
    margin-top: 16rpx;

    &.disabled {
      background: #ccc;
      color: #fff;
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