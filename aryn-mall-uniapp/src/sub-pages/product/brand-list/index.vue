<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getList } from '@/api/product/brand'

definePage({
  name: 'brand-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '品牌专区',
  },
})

interface BrandGroup {
  letter: string
  brands: any[]
}

interface State {
  brandGroups: BrandGroup[]
}

const router = useRouter()
const loading = ref(true)
const state = reactive<State>({
  brandGroups: [],
})

onLoad(async () => {
  await getBrandList()
})

async function getBrandList() {
  loading.value = true
  try {
    const response = await getList()
    const brands = response || []
    // 按首字母分组
    const groupMap = new Map<string, any[]>()
    for (const brand of brands) {
      const letter = (brand.firstLetter || '#').toUpperCase()
      if (!groupMap.has(letter)) {
        groupMap.set(letter, [])
      }
      groupMap.get(letter)!.push(brand)
    }
    // 按字母排序
    const letters = [...groupMap.keys()].sort()
    state.brandGroups = letters.map(letter => ({
      letter,
      brands: groupMap.get(letter)!,
    }))
  }
  finally {
    loading.value = false
  }
}

function toGoodsList(brand: any) {
  router.push({
    path: '/sub-pages/product/goods-list/index',
    query: { brandId: brand.id },
  })
}

</script>

<template>
  <view class="brand-list-page">
    <hr-navbar title="品牌专区" />

    <view v-if="loading" class="flex items-center justify-center p-40px">
      <wd-loading />
    </view>

    <scroll-view v-else scroll-y class="brand-scroll">
      <view v-if="brandGroups.length === 0" class="flex flex-col items-center justify-center p-40px">
        <wd-text text="暂无品牌数据" color="gray" />
      </view>

      <view v-for="group in state.brandGroups" :key="group.letter" class="brand-group">
        <view class="letter-header">
          <wd-text :text="group.letter" size="28rpx" color="#FF2237" bold />
        </view>
        <view class="brand-grid">
          <view
            v-for="brand in group.brands"
            :key="brand.id"
            class="brand-item"
            @click="toGoodsList(brand)"
          >
            <image
              v-if="brand.logo"
              class="brand-logo"
              :src="brand.logo"
              mode="aspectFit"
              lazy-load
            />
            <view v-else class="brand-logo-placeholder">
              <wd-text :text="brand.name?.substring(0, 1)" size="36rpx" color="#999" bold />
            </view>
            <view class="brand-name">
              <wd-text :text="brand.name" size="24rpx" color="#333" :lines="1" />
            </view>
          </view>
        </view>
      </view>

      <wd-gap :height="5" />
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.brand-list-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f4f4f4;
}

.brand-scroll {
  flex: 1;
  // #ifdef MP
  height: calc(100vh - var(--window-top) - 44px - env(safe-area-inset-bottom));
  // #endif
  // #ifdef H5
  height: calc(100vh - var(--window-top) - 44px - env(safe-area-inset-bottom));
  // #endif
}

.brand-group {
  margin-bottom: 20rpx;
  background-color: #fff;
  border-radius: 16rpx;
  padding: 16rpx;
  margin-left: 20rpx;
  margin-right: 20rpx;
}

.letter-header {
  padding: 10rpx 16rpx 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 16rpx;
}

.brand-grid {
  display: flex;
  flex-wrap: wrap;
}

.brand-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16rpx 8rpx;
}

.brand-logo {
  width: 100rpx;
  height: 100rpx;
  border-radius: 12rpx;
  border: 1rpx solid #eee;
}

.brand-logo-placeholder {
  width: 100rpx;
  height: 100rpx;
  border-radius: 12rpx;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  margin-top: 8rpx;
  text-align: center;
  width: 100%;
}
</style>