<script setup lang="ts">
import type { BusinessCardType } from '@/utils/message'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { businessCardRoute } from '@/utils/message'
import { navigateToUrl } from '@/utils/tab-bar'

const props = defineProps<{
  payload: Record<string, any>
  /**
   * 外部算好的安全跳转路由（站内信详情页传入）。
   *
   * 新版 cardPayload 改为 `{bizType,bizId}`，卡片自带的路由推导只看 orderId
   * 等旧字段，会推导失败；通知侧已用 notice-jump 统一解析，这里优先消费，
   * 避免卡片「可点但没反应」。
   */
  route?: string
  type: BusinessCardType
}>()

const { t } = useI18n()

const labelKeys: Record<BusinessCardType, string> = {
  NOTICE_CARD: 'notice',
  ORDER_CARD: 'order',
  PRODUCT_CARD: 'product',
  REFUND_CARD: 'refund',
}

/** 无媒体图时的兜底图标：统一走 wd-icon 字体，不用 emoji */
const iconNames: Record<BusinessCardType, string> = {
  NOTICE_CARD: 'notification',
  ORDER_CARD: 'note',
  PRODUCT_CARD: 'goods',
  REFUND_CARD: 'money-circle',
}

const typeLabel = computed(() =>
  t(`message.businessCard.type.${labelKeys[props.type]}`),
)
const detailLabel = computed(() =>
  t(`message.businessCard.detail.${labelKeys[props.type]}`),
)
const iconName = computed(() => iconNames[props.type])
/** 标题/摘要回退链沿用旧实现口径，避免通知详情页文案变化 */
const title = computed(
  () => props.payload.title || props.payload.name || detailLabel.value,
)
const summary = computed(
  () =>
    props.payload.summary
    || props.payload.statusText
    || props.payload.amount
    || t('message.businessCard.tapForStatus'),
)
const ariaLabel = computed(() => `${typeLabel.value} ${title.value}`)

function openCard() {
  // 站内信详情传入的 route 已做白名单校验；没有时回退卡片自身的业务路由推导
  const url = props.route || businessCardRoute(props.type, props.payload)
  if (url)
    navigateToUrl(url)
}
</script>

<template>
  <view
    class="business-card"
    hover-class="business-card--pressed"
    aria-role="button"
    :aria-label="ariaLabel"
    @click="openCard"
  >
    <!-- 媒体块 112rpx：有图用图，无图用类型图标兜底 -->
    <view class="business-card__media">
      <image
        v-if="payload.image"
        class="business-card__image"
        :src="payload.image"
        mode="aspectFill"
      />
      <wd-icon
        v-else
        :name="iconName"
        size="44rpx"
        custom-style="color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237))"
      />
    </view>

    <view class="business-card__copy">
      <view class="business-card__type">
        {{ typeLabel }}
      </view>
      <text class="business-card__title">
        {{ title }}
      </text>
      <text class="business-card__summary">
        {{ summary }}
      </text>
    </view>

    <view class="business-card__arrow">
      <wd-icon
        name="arrow-right"
        size="28rpx"
        custom-style="color: var(--app-text-3)"
      />
    </view>
  </view>
</template>

<style scoped lang="scss">
.business-card {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx;
  border: 1rpx solid var(--app-border);
  border-radius: 24rpx;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgb(17 27 38 / 6%);
  transition: transform 150ms ease-out;

  /* 按压态只压暗/轻微缩放，不产生布局位移 */
  &--pressed {
    background: var(--app-card-pressed, var(--app-card-2));
    transform: scale(0.99);
  }

  &__media {
    display: flex;
    width: 112rpx;
    height: 112rpx;
    flex: none;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    border-radius: 16rpx;
    background: var(--app-card-2);
  }

  &__image {
    display: block;
    width: 100%;
    height: 100%;
  }

  &__copy {
    display: flex;
    min-width: 0;
    flex: 1;
    flex-direction: column;
  }

  &__type {
    display: inline-block;
    align-self: flex-start;
    padding: 0 12rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    border-radius: 999rpx;
    background: var(--app-card-2);
    font-size: 20rpx;
    line-height: 32rpx;
  }

  &__title {
    display: -webkit-box;
    overflow: hidden;
    margin-top: 6rpx;
    color: var(--app-text);
    font-size: 28rpx;
    font-weight: 600;
    line-height: 1.4;
    word-break: break-word;
    text-overflow: ellipsis;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  &__summary {
    overflow: hidden;
    margin-top: 4rpx;
    color: var(--app-text-3);
    font-size: 22rpx;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__arrow {
    display: flex;
    flex: none;
    align-items: center;
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .business-card {
    transition: none;
  }
}
</style>
