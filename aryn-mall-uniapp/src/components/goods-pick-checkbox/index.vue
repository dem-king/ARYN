<script setup lang="ts">
/**
 * 商品卡勾选方块（首页批量加购用）。
 *
 * 与 `quick-cart-button` 同属卡片内的二级操作，都需要 `@tap.stop`：
 * 卡片本身点击进详情，勾选与加购都必须阻止冒泡，否则用户勾一下
 * 就被弹到商品详情页。
 *
 * 热区说明：视觉方块 40rpx，但外层 `.pick-tap` 撑到 56rpx 并做负外边距，
 * 保证小程序端实际可点区域接近 44px 无障碍下限；视觉密度不变的条件下
 * 把误触率降下来（卡片只有两列，热区太小会频繁点到卡片本身）。
 */
import { computed } from 'vue'

import { useGoodsPickStore } from '@/store/goodsPickStore'

const props = withDefaults(defineProps<{
  /** 商品 SPU ID */
  spuId: string
  /** 视觉方块边长（rpx） */
  size?: string
}>(), {
  size: '40rpx',
})

const pickStore = useGoodsPickStore()
const picked = computed(() => pickStore.isPicked(props.spuId))

function handleToggle() {
  pickStore.toggle(props.spuId)
}
</script>

<template>
  <view class="pick-tap" @tap.stop="handleToggle">
    <view
      class="pick-box"
      :class="{ 'pick-box--on': picked }"
      :style="{ width: size, height: size }"
    >
      <wd-icon
        v-if="picked"
        name="check"
        :size="`calc(${size} * 0.6)`"
        color="#fff"
      />
    </view>
  </view>
</template>

<style scoped>
.pick-tap {
  /* 视觉 40rpx，热区撑到 56rpx（约 28px，移动端可接受），负外边距避免撑破卡片 */
  display: inline-flex;
  padding: 8rpx;
  margin: -8rpx;
  align-items: center;
  justify-content: center;
}

.pick-box {
  display: flex;
  box-sizing: border-box;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #d5dce4;
  border-radius: 10rpx;
  background: #fff;
  transition: background-color 0.15s, border-color 0.15s;
}

.pick-box--on {
  border-color: var(--theme-color-primary, var(--wot-color-theme-primary));
  background: var(--theme-color-primary, var(--wot-color-theme-primary));
}
</style>
