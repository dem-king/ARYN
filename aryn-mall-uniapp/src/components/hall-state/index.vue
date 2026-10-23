<script setup lang="ts">
/**
 * 营销会场三态（空 / 错误 / 无数据）统一组件。
 *
 * 形态固定为「图形位 + 主标题 + 说明 + 主按钮（+ 文字链）」：
 * 图形位用品牌渐变圆 + 白色线性图标（`static/promotion/*.svg`），
 * 不用 emoji、不用中文单字方块 —— 后者在多租户主题下既非图标也不成体系。
 * 图标是白色实心，落在品牌渐变底上，因此任何租户主题都成立。
 */
const props = withDefaults(defineProps<{
  /** 图形语义：秒杀 / 拼团 / 折扣 / 失败 */
  art?: 'discount' | 'error' | 'group' | 'seckill'
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
  seckill: '/static/promotion/icon-flash.svg',
}
</script>

<template>
  <view class="hall-state">
    <view class="state-art">
      <image class="state-art-icon" :src="ART_ICON[props.art]" mode="aspectFit" />
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
  margin-top: 28rpx;
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
