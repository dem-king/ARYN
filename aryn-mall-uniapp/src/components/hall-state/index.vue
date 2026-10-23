<script setup lang="ts">
import { computed } from 'vue'
/**
 * 营销会场三态（空 / 错误 / 无数据）统一组件。
 *
 * 形态固定为「图形位 + 主标题 + 说明 + 主按钮（+ 文字链）」：
 * 图形位用品牌渐变圆 + 白色线性图标（`static/promotion/*.svg`），
 * 不用 emoji、不用中文单字方块 —— 后者在多租户主题下既非图标也不成体系。
 * 图标是白色实心，落在品牌渐变底上，因此任何租户主题都成立。
 */
const props = withDefaults(defineProps<{
  /** 图形语义：秒杀插画 / 购物车插画 / 订单插画 / 拼团图标 / 折扣图标 / 失败 */
  art?: 'discount' | 'empty-cart' | 'empty-order' | 'error' | 'group' | 'seckill'
  title: string
  desc?: string
  actionText?: string
  linkText?: string
}>(), {
  art: 'group',
  desc: '',
  actionText: '',
  linkText: '',
})

const emit = defineEmits<{
  (e: 'action'): void
  (e: 'link'): void
}>()

const ART_ICON: Record<string, string> = {
  discount: '/static/promotion/icon-discount.svg',
  error: '/static/promotion/icon-flash.svg',
  group: '/static/promotion/icon-group.svg',
}

/** 插画位：空态使用交付的扁平插画（280rpx），比渐变圆 + 矢量图标更有质感 */
const ART_ILLUSTRATION: Record<string, string> = {
  'empty-cart': '/static/promotion/empty/empty-cart.png',
  'empty-order': '/static/promotion/empty/empty-order.png',
  'seckill': '/static/promotion/empty/empty-seckill.png',
}

const illustration = computed(() => ART_ILLUSTRATION[props.art] ?? '')
const icon = computed(() => ART_ICON[props.art] ?? ART_ICON.group)
</script>

<template>
  <view class="hall-state">
    <image
      v-if="illustration"
      class="state-illustration"
      :src="illustration"
      mode="aspectFit"
    />
    <view v-else class="state-art">
      <image class="state-art-icon" :src="icon" mode="aspectFit" />
    </view>
    <text class="state-title">
      {{ title }}
    </text>
    <text v-if="desc" class="state-desc">
      {{ desc }}
    </text>
    <view v-if="actionText" class="state-action-hit" @tap="emit('action')">
      <view class="state-action">
        {{ actionText }}
      </view>
    </view>
    <text v-if="linkText" class="state-link" @tap="emit('link')">
      {{ linkText }}
    </text>
    <!-- 次要出口插槽：小程序分享必须由原生 button open-type 触发，故由调用方注入 -->
    <slot name="link" />
  </view>
</template>

<style lang="scss" scoped>
.hall-state {
  display: flex;
  padding: 120rpx 48rpx 40rpx;
  flex-direction: column;
  align-items: center;
}

/* 插画位：交付的扁平插画，280rpx（上下留白由页面 padding 负责） */
.state-illustration {
  width: 280rpx;
  height: 280rpx;
}

/* 图形位：品牌渐变圆 + 白色图标（无 emoji、无单字方块） */
.state-art {
  display: flex;
  width: 144rpx;
  height: 144rpx;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237));
  border-radius: 50%;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.12);
}

.state-art-icon {
  width: 68rpx;
  height: 68rpx;
}

.state-title {
  margin-top: 20rpx;
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
}

.state-desc {
  margin-top: 12rpx;
  color: #8a8f99;
  font-size: 22rpx;
  line-height: 1.6;
  text-align: center;
}

/* 主按钮：视觉 80rpx、热区 ≥88rpx */
.state-action-hit {
  display: flex;
  min-height: 88rpx;
  margin-top: 24rpx;
  align-items: center;
  justify-content: center;
}

.state-action {
  display: flex;
  height: 80rpx;
  padding: 0 56rpx;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237));
  border-radius: 999rpx;
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
}

.state-link {
  margin-top: 20rpx;
  padding: 12rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  font-weight: 600;
}
</style>
