<script setup lang="ts">
import type { CouponLike } from '@/utils/coupon-display'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
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
  /** 状态/截止文案（今天过期 / 11-15 截止…）；为空则整段不渲染 */
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
  /**
   * 动作按钮样式：
   * - primary：品牌实底，用于「立即领取」这类主行动；
   * - ghost：白底描边，用于券包里全是已领券时的次级「去使用」。
   */
  actionVariant?: 'primary' | 'ghost'
  /**
   * 票根缺口颜色（即卡片下面露出的背景色）。
   * 默认走 `--wot-color-theme-background`（页面底色）；结算页选券弹层是白底，
   * 由父组件传 `#ffffff`，否则缺口会露出页面灰底形成脏点。
   */
  notchColor?: string
}

const props = withDefaults(defineProps<Props>(), {
  band: '',
  actionText: '',
  actionEnabled: true,
  selector: false,
  selected: false,
  tone: 'active',
  showScope: true,
  actionVariant: 'primary',
  notchColor: '',
})

const emit = defineEmits<{
  action: [coupon: CouponCardData]
  select: [coupon: CouponCardData]
}>()

const { t, locale } = useI18n()

const isDiscountCoupon = computed(() => couponType(props.coupon) === '2')

/**
 * 金额区数值：满减券直接展示面额；折扣券中文展示「8折」，
 * 英文没有「折」的概念，换算成「20 % OFF」才等价可读。
 */
const valueParts = computed(() => {
  const parts = couponValueParts(props.coupon)
  const unit = t('checkout.coupon.discountUnit')
  if (!isDiscountCoupon.value)
    return parts
  if (String(locale.value).startsWith('en')) {
    const discount = Number(parts.value)
    const percent = Number.isFinite(discount) && discount > 0 ? (10 - discount) * 10 : 0
    return { ...parts, symbol: '', value: String(Number(percent.toFixed(2))), unit }
  }
  return { ...parts, unit }
})
const typeLabel = computed(() =>
  isDiscountCoupon.value ? t('checkout.coupon.typeDiscount') : t('checkout.coupon.typeAmount'),
)
const threshold = computed(() => {
  const value = Number(props.coupon?.threshold ?? 0)
  if (!Number.isFinite(value) || value <= 0)
    return t('checkout.coupon.noThreshold')
  return t('checkout.coupon.threshold', { amount: String(Number(value.toFixed(2))) })
})
const desc = computed(() => {
  if (!props.showScope)
    return ''
  // 后端下发的使用说明是业务文案，保持原文；没有时回落到适用范围
  const description = (props.coupon?.useDescription ?? '').trim()
  return description || t(
    props.coupon?.useRange === '1' ? 'checkout.coupon.scopeAll' : 'checkout.coupon.scopePartial',
  )
})

/**
 * 票根缺口的颜色通过 CSS 变量下放：
 * 只在传了 notchColor 时写内联变量，其余情况由样式里的
 * `var(--coupon-notch-bg, var(--wot-color-theme-background, #f5f5f5))` 兜底。
 *
 * 不把默认值也拼成内联样式：默认链里已经包含 var() 回退，再包一层内联变量
 * 只是把回退链搬到 style 上，反而更难排错（小程序端内联 style 里嵌 var 的
 * 兼容性也比静态 CSS 差）。
 */
const notchStyle = computed<Record<string, string>>(() => {
  const style: Record<string, string> = {}
  if (props.notchColor)
    style['--coupon-notch-bg'] = props.notchColor
  return style
})

/**
 * 单选图标颜色：wd-icon 的 color 只接受色值字符串，拿不到 SCSS 变量，
 * 因此这里与 coupon-ticket.scss 的令牌保持同值（品牌色 #ff5500、灰阶 #c8c8c8）。
 */
const selectorColor = computed(() => {
  if (props.selected)
    return props.tone === 'muted' ? '#b6b9c0' : '#ff5500'
  return props.tone === 'muted' ? '#d0d0d0' : '#c8c8c8'
})

/**
 * 整卡可点：选择态下派发 select，其余情况派发 action。
 *
 * 不用父组件 `@click` 透传 —— 小程序端组件上的原生事件透传不可靠，
 * 且卡内已有自身点击逻辑，两者叠加会重复触发。
 * 右侧 CTA 只做视觉、不单独绑事件，点击热区统一落在整卡上。
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
    券票结构（2026-11 券体系重构 v2）：
    白底卡 + 左侧浅色金额区 + 竖向虚线撕口（上下各一个票根缺口）+ 右侧信息区 + 动作区。

    与旧版的最大差别是「红橙只留给钱」：不再有渐变描边外框、不再有整条红色状态带，
    面额/主 CTA 用品牌色，券名/适用范围/截止时间走中性灰，四类券面（领券中心 /
    我的券包 / 结算选券 / 首页领券卡）共用这套语言。

    金额/门槛/副文案统一走 utils/coupon-display，避免各页面各写一套文案。
  -->
  <view
    class="coupon-ticket"
    :class="[
      `coupon-ticket--${tone}`,
      {
        'coupon-ticket--selector': selector,
        'coupon-ticket--selected': selector && selected,
      },
    ]"
    :style="notchStyle"
    @click="handleAction"
  >
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
      <view class="coupon-ticket__headline">
        {{ coupon.couponName || t('checkout.coupon.fallbackName') }}
      </view>
      <view v-if="desc" class="coupon-ticket__desc">
        {{ desc }}
      </view>
      <view class="coupon-ticket__meta">
        <view class="coupon-ticket__pill">
          {{ typeLabel }}
        </view>
        <view v-if="band" class="coupon-ticket__band">
          <!-- 时钟图标不传 color，颜色由外层 .coupon-ticket__band-icon 的 color 继承 -->
          <view class="coupon-ticket__band-icon">
            <wd-icon name="time-filled" size="10px" />
          </view>
          <text class="coupon-ticket__band-text">{{ band }}</text>
        </view>
      </view>
    </view>

    <view v-if="selector || actionText" class="coupon-ticket__action">
      <template v-if="selector">
        <wd-icon
          v-if="selected"
          name="check-circle-filled"
          :color="selectorColor"
          size="22px"
        />
        <wd-icon v-else name="circle1" :color="selectorColor" size="22px" />
      </template>
      <view
        v-else-if="actionText"
        class="coupon-ticket__button"
        :class="[
          `coupon-ticket__button--${actionVariant}`,
          { 'coupon-ticket__button--disabled': !actionEnabled },
        ]"
      >
        {{ actionText }}
      </view>
    </view>

    <!-- 票根缺口：圆心落在值块右缘（撕口处）与卡片上下边缘，被 overflow 裁成半圆 -->
    <view class="coupon-ticket__notch coupon-ticket__notch--top" />
    <view class="coupon-ticket__notch coupon-ticket__notch--bottom" />

    <!-- 选中描边：用覆盖层而不是 border，避免选中时卡片尺寸跳动导致文字重排 -->
    <view v-if="selector && selected" class="coupon-ticket__outline" />
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/coupon-ticket.scss';

.coupon-ticket {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
  background: $coupon-surface;
  border-radius: $coupon-card-radius;
  @include coupon-card-elevation;
}

/*
  左值块：金额 + 门槛纵向居中，浅橙渐变到白，负责「这张券值多少钱」这一层信息。
  宽度固定 $coupon-value-width（176rpx）：撕口虚线与票根缺口都按它的右缘对位，
  值块宽度一变，缺口和虚线就会错位。
*/
.coupon-ticket__value {
  position: relative;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: $coupon-value-width;
  padding: 24rpx 8rpx;
  background: linear-gradient(180deg, $coupon-tint 0%, #fff 82%);

  // 竖向虚线撕口：上下各留 22rpx 收口，避免虚线顶到卡片圆角
  &::after {
    position: absolute;
    top: 22rpx;
    right: 0;
    bottom: 22rpx;
    content: '';
    border-left: $coupon-divider-width dashed $coupon-line;
  }

  .coupon-ticket--muted & {
    background: linear-gradient(180deg, $coupon-muted-tint 0%, #fff 82%);
  }

  // 撕口虚线跟着色调降级，否则灰卡上会留一条橙线（「已过期」看起来还能用）
  .coupon-ticket--muted &::after {
    border-left-color: $coupon-muted-line;
  }

  .coupon-ticket--frozen & {
    background: linear-gradient(180deg, $coupon-frozen-tint 0%, #fff 82%);
  }

  .coupon-ticket--frozen &::after {
    border-left-color: $coupon-frozen-line;
  }

  .coupon-ticket--selected & {
    background: linear-gradient(180deg, $coupon-tint-strong 0%, #fff 82%);
  }
}

.coupon-ticket__amount {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: center;
  line-height: 1;
  color: $coupon-brand;

  .coupon-ticket--muted & {
    color: $coupon-muted-brand;
  }

  .coupon-ticket--frozen & {
    color: $coupon-frozen-brand;
  }
}

// 面额数字是全卡唯一的大字号，800 字重把「钱」的分量压住
.coupon-ticket__number {
  font-size: 56rpx;
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: -1rpx;
}

.coupon-ticket__symbol {
  margin-right: 2rpx;
  font-size: 28rpx;
  font-weight: 700;
}

// 折扣券的「折 / OFF」跟在数字后面，与 ¥ 同一量级
.coupon-ticket__unit {
  margin-left: 2rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.coupon-ticket__threshold {
  margin-top: 14rpx;
  font-size: 20rpx;
  color: $coupon-desc;
  white-space: nowrap;
}

/*
  右信息区：券名（主）/ 适用范围（次）/ 类型胶囊 + 截止（元信息）。
  字号按 mock 的 14 / 10.5 / 9 / 10px 换算为 28 / 21 / 18 / 20rpx，
  比旧版整体收一档，给 CTA 和值块让出横向空间。
*/
.coupon-ticket__info {
  display: flex;
  flex-direction: column;
  flex: 1;
  justify-content: center;
  box-sizing: border-box;
  min-width: 0;
  padding: 26rpx 16rpx 24rpx 24rpx;
}

.coupon-ticket__headline {
  overflow: hidden;
  font-size: 28rpx;
  font-weight: 600;
  color: $coupon-text;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*
  使用说明是「能不能用」的关键信息（例如「秒杀商品不可用」），券面又没有详情入口，
  单行省略等于把使用限制藏掉一半。这里按可用宽度自然换行，卡片高度随文案增长；
  长英文/数字串用 overflow-wrap 兜底，避免撑破信息区。
*/
.coupon-ticket__desc {
  margin-top: 10rpx;
  font-size: 21rpx;
  color: $coupon-desc;
  line-height: 1.4;
  overflow-wrap: break-word;
  word-break: normal;
  white-space: normal;
}

.coupon-ticket__meta {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-width: 0;
  margin-top: 20rpx;
  gap: 12rpx;
}

// 券类型胶囊：浅橙底 + 深橙字（$coupon-brand-ink 对 $coupon-tint-strong ≈ 5:1，
// 18rpx 小字压实底品牌色只有 3.2:1，达不到可读性要求）
.coupon-ticket__pill {
  flex: none;
  padding: 4rpx 12rpx;
  font-size: 18rpx;
  font-weight: 600;
  color: $coupon-brand-ink;
  white-space: nowrap;
  background: $coupon-tint-strong;
  border-radius: 8rpx;

  .coupon-ticket--muted & {
    color: $coupon-desc;
    background: $coupon-hairline;
  }

  .coupon-ticket--frozen & {
    color: $coupon-frozen-ink;
    background: $coupon-frozen-tint;
  }
}

// 截止/状态：中性灰小字 + 时钟图标，与 pill 同一行；券名再长也不会把这里挤换行
.coupon-ticket__band {
  display: flex;
  flex-direction: row;
  flex: 1;
  align-items: center;
  min-width: 0;
  gap: 6rpx;
  overflow: hidden;
  font-size: 20rpx;
  color: $coupon-desc;
  white-space: nowrap;
}

.coupon-ticket__band-icon {
  display: flex;
  flex: none;
  color: $coupon-placeholder;
}

.coupon-ticket__band-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// 动作区：flex 行内垂直居中；padding 让 CTA 不贴卡片右边缘
.coupon-ticket__action {
  display: flex;
  flex-direction: row;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: 0 24rpx 0 12rpx;
}

/*
  CTA：64rpx 高胶囊。primary 用品牌实底 + 橙色投影，是「立即领取」的主行动；
  ghost 白底 + 浅橙描边，用于券包/已领取状态下的「去使用」，不与主行动抢眼。
*/
.coupon-ticket__button {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: $coupon-action-height;
  padding: 0 28rpx;
  overflow: hidden;
  font-size: 24rpx;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  background: $coupon-brand;
  border-radius: 999rpx;
  box-shadow: 0 8rpx 20rpx rgba(255, 85, 0, 0.2);

  &--ghost {
    color: $coupon-brand;
    background: $coupon-surface;
    border: 2rpx solid #ffc9ad;
    box-shadow: none;
  }

  // 置灰但仍可见：仍保持 64rpx 高，避免禁用时卡片高度抽动
  &--disabled {
    color: $coupon-placeholder;
    background: $coupon-hairline;
    border-color: transparent;
    box-shadow: none;
  }
}

/*
  票根缺口：直径 $coupon-notch-size，圆心在「值块右缘 × 卡片上下边缘」的交点，
  一半被 overflow:hidden 裁掉，只留卡内半圆。背景取卡外页面底色，视觉上等于
  「把卡挖掉一块」，这是票券语言的关键暗号；结算弹层白底时由 notchColor 覆盖。
*/
.coupon-ticket__notch {
  position: absolute;
  z-index: 2;
  width: $coupon-notch-size;
  height: $coupon-notch-size;
  border-radius: 50%;
  background: var(--coupon-notch-bg, var(--wot-color-theme-background, #f5f5f5));
  pointer-events: none;

  &--top {
    top: -($coupon-notch-size * 0.5);
    left: $coupon-value-width - ($coupon-notch-size * 0.5);
  }

  &--bottom {
    bottom: -($coupon-notch-size * 0.5);
    left: $coupon-value-width - ($coupon-notch-size * 0.5);
  }
}

// 选中描边：覆盖层内描边，不占布局、不随卡片宽度变化引起换行
.coupon-ticket__outline {
  position: absolute;
  z-index: 3;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  border-radius: $coupon-card-radius;
  box-shadow: inset 0 0 0 $coupon-divider-width $coupon-brand;
  pointer-events: none;
}
</style>
