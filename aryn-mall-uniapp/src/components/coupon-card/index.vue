<script setup lang="ts">
import type { CouponLike } from '@/utils/coupon-display'
import { computed } from 'vue'
import {
  couponSubtitle,
  couponThresholdText,
  couponType,
  couponValueParts,
} from '@/utils/coupon-display'

export interface CouponCardData extends CouponLike {
  id?: string
  remainNum?: number | string | null
  userReceiveCount?: number | string | null
}

interface Props {
  coupon: CouponCardData
  /** 底部状态带文案（今天过期 / 已使用…）；为空则整条不渲染 */
  band?: string
  /** 右侧动作文案（立即领取 / 去使用）；为空则不渲染动作区 */
  actionText?: string
  /** 动作区是否可点，false 时置灰但仍可见 */
  actionEnabled?: boolean
  /** 动作区改为单选图标（下单页选券用） */
  selector?: boolean
  /** 单选选中态 */
  selected?: boolean
  /** 视觉色调：muted 已使用/已过期/本单不可用，frozen 冻结中 */
  tone?: 'active' | 'frozen' | 'muted'
  /** 是否展示适用范围副文案 */
  showScope?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  band: '',
  actionText: '',
  actionEnabled: true,
  selector: false,
  selected: false,
  tone: 'active',
  showScope: true,
})

const emit = defineEmits<{
  action: [coupon: CouponCardData]
  select: [coupon: CouponCardData]
}>()

const valueParts = computed(() => couponValueParts(props.coupon))
const typeLabel = computed(() => (couponType(props.coupon) === '2' ? '折扣券' : '满减券'))
const threshold = computed(() => couponThresholdText(props.coupon))
const desc = computed(() => (props.showScope ? couponSubtitle(props.coupon) : ''))

/**
 * 整卡可点：选择态下派发 select，其余情况派发 action。
 *
 * 不用父组件 `@click` 透传 —— 小程序端组件上的原生事件透传不可靠，
 * 且卡内已有自身点击逻辑，两者叠加会重复触发。
 */
function handleAction() {
  if (props.selector) {
    if (props.actionEnabled)
      emit('select', props.coupon)
    return
  }
  if (!props.actionEnabled)
    return
  emit('action', props.coupon)
}
</script>

<template>
  <!--
    券票样式对标小象超市：渐变描边外框 + 浅色票面 + 底部红色状态带，
    票根缺口落在票面左右边缘的垂直中线上，外侧一半被外框裁掉后只留内半圆。
    金额/门槛/副文案统一走 utils/coupon-display，避免各页面各写一套文案。
  -->
  <view
    class="coupon-ticket"
    :class="[
      `coupon-ticket--${tone}`,
      { 'coupon-ticket--banded': !!band, 'coupon-ticket--selector': selector },
    ]"
    @click="handleAction"
  >
    <view class="coupon-ticket__face">
      <view class="coupon-ticket__notch coupon-ticket__notch--left" />
      <view class="coupon-ticket__notch coupon-ticket__notch--right" />

      <view class="coupon-ticket__value">
        <view class="coupon-ticket__amount">
          <text v-if="valueParts.symbol" class="coupon-ticket__symbol">
            {{ valueParts.symbol }}
          </text>
          <text class="coupon-ticket__number">
            {{ valueParts.value }}
          </text>
          <text v-if="valueParts.unit" class="coupon-ticket__unit">
            {{ valueParts.unit }}
          </text>
        </view>
        <view class="coupon-ticket__threshold">
          {{ threshold }}
        </view>
      </view>

      <view class="coupon-ticket__info">
        <view class="coupon-ticket__pill">
          {{ typeLabel }}
        </view>
        <view class="coupon-ticket__name">
          {{ coupon.couponName || '优惠券' }}
        </view>
        <view v-if="desc" class="coupon-ticket__desc">
          {{ desc }}
        </view>
      </view>

      <view v-if="selector" class="coupon-ticket__action">
        <wd-icon
          v-if="selected"
          name="check-outline"
          :color="tone === 'muted' ? '#b8b8b8' : '#ff2d2d'"
          size="22px"
        />
        <wd-icon v-else name="circle1" :color="tone === 'muted' ? '#d0d0d0' : '#c8c8c8'" size="22px" />
      </view>
      <view
        v-else-if="actionText"
        class="coupon-ticket__action coupon-ticket__button"
        :class="{ 'coupon-ticket__button--disabled': !actionEnabled }"
      >
        {{ actionText }}
      </view>
    </view>

    <view v-if="band" class="coupon-ticket__band">
      {{ band }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/coupon-ticket.scss';

.coupon-ticket {
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  padding: $coupon-rim-width;
  // 裁掉票根缺口伸到卡外的一半，外轮廓保持齐边
  overflow: hidden;
  border-radius: $coupon-radius;
  @include coupon-rim($coupon-rim-start, $coupon-rim-end);

  &--muted {
    @include coupon-rim($coupon-muted-rim-start, $coupon-muted-rim-end);
  }

  &--frozen {
    @include coupon-rim($coupon-frozen-rim-start, $coupon-frozen-rim-end);
  }
}

.coupon-ticket__face {
  position: relative;
  display: flex;
  flex-direction: row;
  flex: 1;
  align-items: center;
  box-sizing: border-box;
  min-height: 100rpx;
  padding: 14rpx 20rpx;
  border-radius: $coupon-radius - $coupon-rim-width;
  @include coupon-face($coupon-face-start, $coupon-face-end);

  /*
    有状态带时，票面下沿改成向上拱起的弧，压在状态带上（对标参考图里
    「票面鼓进红带」的那道弧）：底部两个超大圆角从左右角向中间收，
    中间自然形成弧顶。直接给下沿加 border-radius 只会做圆角，做不出这道弧。
  */
  .coupon-ticket--banded & {
    margin-bottom: -14rpx;
    border-bottom-left-radius: 50% 28rpx;
    border-bottom-right-radius: 50% 28rpx;
  }

  .coupon-ticket--muted & {
    @include coupon-face($coupon-muted-face-start, $coupon-muted-face-end);
  }

  .coupon-ticket--frozen & {
    @include coupon-face($coupon-frozen-face-start, $coupon-frozen-face-end);
  }
}

// 缺口整圆按票面边缘居中：贴外框的半圆与描边同色自然融掉，只留内半圆
.coupon-ticket__notch {
  position: absolute;
  top: 50%;
  z-index: 2;
  width: $coupon-notch-size;
  height: $coupon-notch-size;
  border-radius: 50%;
  transform: translateY(-50%);

  &--left {
    left: -($coupon-notch-size * 0.5);
    background: $coupon-notch-left;
  }

  &--right {
    right: -($coupon-notch-size * 0.5);
    background: $coupon-notch-right;
  }

  .coupon-ticket--muted &--left {
    background: $coupon-muted-notch-left;
  }

  .coupon-ticket--muted &--right {
    background: $coupon-muted-notch-right;
  }

  .coupon-ticket--frozen &--left {
    background: $coupon-frozen-notch-left;
  }

  .coupon-ticket--frozen &--right {
    background: $coupon-frozen-notch-right;
  }
}

.coupon-ticket__value {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 152rpx;
}

.coupon-ticket__amount {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: center;
  color: $coupon-value;

  .coupon-ticket--muted & {
    color: $coupon-muted-value;
  }

  .coupon-ticket--frozen & {
    color: $coupon-frozen-value;
  }
}

.coupon-ticket__symbol {
  font-size: 26rpx;
  font-weight: 600;
}

.coupon-ticket__number {
  font-size: 56rpx;
  font-weight: 700;
  line-height: 1.05;
}

.coupon-ticket__unit {
  font-size: 28rpx;
  font-weight: 600;
}

.coupon-ticket__threshold {
  margin-top: 2rpx;
  font-size: 20rpx;
  color: $coupon-desc;
}

.coupon-ticket__info {
  flex: 1;
  min-width: 0;
  padding-left: 16rpx;
}

.coupon-ticket__pill {
  display: inline-block;
  padding: 2rpx 14rpx;
  font-size: 20rpx;
  color: $coupon-pill-text;
  background: $coupon-pill-bg;
  border-radius: 999rpx;

  .coupon-ticket--muted & {
    color: $coupon-muted-pill-text;
    background: $coupon-muted-pill-bg;
  }

  .coupon-ticket--frozen & {
    color: $coupon-frozen-pill-text;
    background: $coupon-frozen-pill-bg;
  }
}

.coupon-ticket__name {
  margin-top: 6rpx;
  overflow: hidden;
  font-size: 26rpx;
  font-weight: 600;
  color: $coupon-title;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-ticket__desc {
  margin-top: 4rpx;
  overflow: hidden;
  font-size: 20rpx;
  color: $coupon-desc;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-ticket__action {
  display: flex;
  flex-direction: row;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-end;
  padding-left: 12rpx;
}

.coupon-ticket__button {
  padding: 8rpx 22rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: #fff;
  border-radius: 999rpx;
  @include coupon-rim($coupon-rim-start, $coupon-rim-end);

  &--disabled {
    color: #fff;
    @include coupon-rim($coupon-muted-rim-start, $coupon-muted-rim-end);
  }
}

.coupon-ticket__band {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  height: $coupon-band-height + 14rpx;
  padding-top: 14rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #fff;
  border-radius: 0 0 ($coupon-radius - $coupon-rim-width) ($coupon-radius - $coupon-rim-width);
  @include coupon-band($coupon-band-start, $coupon-band-end);

  .coupon-ticket--muted & {
    @include coupon-band($coupon-muted-band-start, $coupon-muted-band-end);
  }

  .coupon-ticket--frozen & {
    @include coupon-band($coupon-frozen-band-start, $coupon-frozen-band-end);
  }
}
</style>
