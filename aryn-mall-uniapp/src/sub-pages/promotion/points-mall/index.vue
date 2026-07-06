<script setup lang="ts">
import type { PointsGoods } from '@/api/promotion/points-goods'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getPointsGoodsList } from '@/api/promotion/points-goods'
import { getPointsInfo } from '@/api/user/points'

definePage({
  name: 'points-mall',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '积分商城',
  },
})

const pagingRef = ref()
const state = reactive<{
  list: PointsGoods[]
  pointsBalance: number
}>({
  list: [],
  pointsBalance: 0,
})
const globalLoading = useGlobalLoading()

onLoad(async () => {
  await loadPointsBalance()
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function loadPointsBalance() {
  try {
    const info = await getPointsInfo()
    state.pointsBalance = info.point || 0
  }
  catch {
    state.pointsBalance = 0
  }
}

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
  try {
    const response = await getPointsGoodsList({
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

function goDetail(goods: PointsGoods) {
  if (goods.id) {
    uni.navigateTo({
      url: `/sub-pages/promotion/points-mall/detail?id=${goods.id}`,
    })
  }
}

function getTypeLabel(type?: string) {
  if (type === 'goods') return '实物'
  if (type === 'coupon') return '优惠券'
  if (type === 'gift') return '赠品'
  return ''
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="积分商城" />
    </template>
    <!-- 积分余额 -->
    <view class="points-header">
      <view class="points-balance">
        <text class="points-label">
          我的积分
        </text>
        <text class="points-value">
          {{ state.pointsBalance }}
        </text>
      </view>
    </view>
    <!-- 商品列表 -->
    <view class="goods-grid">
      <view
        v-for="item in state.list"
        :key="item.id"
        class="goods-card"
        @tap="goDetail(item)"
      >
        <image
          v-if="item.cover"
          class="goods-cover"
          :src="item.cover"
          mode="aspectFill"
        />
        <view v-else class="goods-cover goods-cover-placeholder">
          <text class="placeholder-text">
            {{ getTypeLabel(item.type) }}
          </text>
        </view>
        <view class="goods-info">
          <view class="goods-name">
            {{ item.name }}
          </view>
          <view class="goods-bottom">
            <view class="goods-price">
              <text class="price-value">
                {{ item.pointsPrice }}
              </text>
              <text class="price-unit">
                积分
              </text>
            </view>
            <view class="goods-stock">
              库存: {{ item.stock }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.points-header {
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 40rpx 32rpx;
  margin: 0 24rpx;
  border-radius: 16rpx;
  margin-bottom: 24rpx;

  .points-balance {
    display: flex;
    align-items: baseline;
    gap: 12rpx;

    .points-label {
      color: rgba(255, 255, 255, 0.8);
      font-size: 26rpx;
    }

    .points-value {
      color: #fff;
      font-size: 56rpx;
      font-weight: 700;
    }
  }
}

.goods-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 0 16rpx;
  gap: 16rpx;
}

.goods-card {
  width: calc(50% - 8rpx);
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);

  .goods-cover {
    width: 100%;
    height: 280rpx;
    display: block;

    &.goods-cover-placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f5f5;

      .placeholder-text {
        font-size: 28rpx;
        color: #ccc;
      }
    }
  }

  .goods-info {
    padding: 16rpx 20rpx 20rpx;

    .goods-name {
      font-size: 26rpx;
      color: #333;
      line-height: 1.4;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      min-height: 72rpx;
    }

    .goods-bottom {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 12rpx;

      .goods-price {
        .price-value {
          font-size: 36rpx;
          font-weight: 700;
          color: #764ba2;
        }

        .price-unit {
          font-size: 22rpx;
          color: #764ba2;
          margin-left: 4rpx;
        }
      }

      .goods-stock {
        font-size: 22rpx;
        color: #999;
      }
    }
  }
}
</style>