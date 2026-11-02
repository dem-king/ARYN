<script setup lang="ts">
import type { CouponLadderTier } from '../coupon-ladder-source'

import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { getByIds, getPage } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRequireLogin } from '@/composables/useRequireLogin'

import { normalizeCouponLadderTiers, normalizeLadderShowNum } from '../coupon-ladder-source'
import { mergeReceivedCouponIds } from '../coupon-received-state'
import { followDecorationLink } from '../link-resolver'

const props = defineProps<{
  showData: Record<string, unknown>
}>()

const { t } = useI18n()

const showNum = computed(() => normalizeLadderShowNum(props.showData.showNum as number))
const showClaimBtn = computed(() => props.showData.showClaimBtn !== false)
const badgeText = computed(() => String(props.showData.badgeText ?? '').trim())
const title = computed(() => String(props.showData.title ?? '').trim())
const subtitle = computed(() => String(props.showData.subtitle ?? '').trim())
const noteText = computed(() => String(props.showData.noteText ?? '').trim())
const ctaText = computed(() => String(props.showData.ctaText ?? '').trim())
const ctaLink = computed(() => props.showData.ctaLink as any)
const ctaVisible = computed(() => Boolean(ctaText.value) && Boolean(ctaLink.value?.path))

const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))
const { ensureLoggedIn } = useRequireLogin()

const tiers = ref<CouponLadderTier[]>([])
const receivedIds = ref<Set<string>>(new Set())

async function fetchTiers() {
  const dataSource = props.showData.dataSource as
    | { mode?: string, targetIds?: string[] }
    | undefined
  const manualIds = (dataSource?.targetIds ?? [])
    .map(id => String(id ?? '').trim())
    .filter(Boolean)
    .slice(0, showNum.value)

  try {
    if (dataSource?.mode === 'manual') {
      if (!manualIds.length) {
        tiers.value = []
        return
      }
      const rows = await getByIds(manualIds)
      tiers.value = normalizeCouponLadderTiers(rows, showNum.value)
      receivedIds.value = mergeReceivedCouponIds(receivedIds.value, rows ?? [])
      return
    }

    // 自动模式只取满减券；多要几条是因为无门槛券会在归一化时被剔除
    const res = await getPage({
      couponType: '1',
      current: 1,
      size: showNum.value + 3,
    })
    const rows = res.records ?? []
    tiers.value = normalizeCouponLadderTiers(rows, showNum.value)
    receivedIds.value = mergeReceivedCouponIds(receivedIds.value, rows)
  }
  catch {
    // 券接口异常时整块不渲染，避免落地页出现「满 undefined」的破图
    tiers.value = []
  }
}

function isReceived(couponId: string) {
  return receivedIds.value.has(couponId)
}

/**
 * 整档可点：只有开启领取按钮时响应，否则 showClaimBtn=false 的配置仍会点出 toast。
 * 领取按钮本身不再单独绑事件，避免小程序事件透传重复触发。
 */
function handleTierClick(couponId: string) {
  if (!showClaimBtn.value)
    return
  handleReceive(couponId)
}

async function handleReceive(couponId: string) {
  if (isReceived(couponId))
    return
  // 游客点领取：记录回跳并打开登录页，不发出需登录的领券请求
  if (!ensureLoggedIn())
    return
  try {
    await addObj({ couponId })
    uni.showToast({ title: t('diy.coupon.receiveSuccess'), icon: 'success' })
    // 本地标记，不重新拉列表：券被领完后列表会过滤掉，卡片会凭空消失
    receivedIds.value.add(couponId)
  }
  catch (err: any) {
    uni.showToast({
      title: err?.message || t('diy.coupon.receiveFailed'),
      icon: 'none',
    })
  }
}

onMounted(() => {
  fetchTiers()
})
</script>

<template>
  <view v-if="tiers.length" class="ladder-floor" :style="dynamicStyles">
    <view class="ladder-hero">
      <view v-if="badgeText" class="ladder-badge">
        {{ badgeText }}
      </view>
      <view class="ladder-title">
        {{ title }}
      </view>
      <view v-if="subtitle" class="ladder-subtitle">
        {{ subtitle }}
      </view>
    </view>

    <view class="ladder-card">
      <view class="ladder-card-head">
        <text class="ladder-card-title">
          {{ t('diy.couponLadder.tierTitle') }}
        </text>
        <text class="ladder-card-hint">
          {{ t('diy.couponLadder.tierHint') }}
        </text>
      </view>

      <view class="ladder-tiers">
        <view
          v-for="(tier, index) in tiers"
          :key="tier.couponId"
          class="ladder-tier"
          :class="{
            'ladder-tier--active': index === tiers.length - 1,
            'ladder-tier--done': isReceived(tier.couponId),
          }"
          @click.stop="handleTierClick(tier.couponId)"
        >
          <view
            v-if="index === tiers.length - 1"
            class="ladder-tier-flag"
          >
            {{ t('diy.couponLadder.bestValue') }}
          </view>
          <view class="ladder-tier-threshold">
            {{ t('diy.couponLadder.threshold', { amount: tier.threshold }) }}
          </view>
          <view class="ladder-tier-amount">
            <text class="ladder-tier-symbol">
              ¥
            </text>
            <text class="ladder-tier-num">
              {{ tier.amount }}
            </text>
          </view>
          <view
            v-if="showClaimBtn"
            class="ladder-tier-claim"
          >
            {{ isReceived(tier.couponId) ? t('diy.coupon.claimed') : t('diy.coupon.claim') }}
          </view>
        </view>
      </view>

      <view class="ladder-divider" />

      <view class="ladder-section-title">
        {{ t('diy.couponLadder.stepTitle') }}
      </view>
      <view class="ladder-steps">
        <view class="ladder-step">
          <view class="ladder-step-index">
            1
          </view>
          <view class="ladder-step-name">
            {{ t('diy.couponLadder.stepClaim') }}
          </view>
        </view>
        <view class="ladder-step">
          <view class="ladder-step-index">
            2
          </view>
          <view class="ladder-step-name">
            {{ t('diy.couponLadder.stepCart') }}
          </view>
        </view>
        <view class="ladder-step">
          <view class="ladder-step-index">
            3
          </view>
          <view class="ladder-step-name">
            {{ t('diy.couponLadder.stepCheckout') }}
          </view>
        </view>
      </view>

      <view v-if="noteText" class="ladder-note">
        {{ noteText }}
      </view>

      <view v-if="ctaVisible" class="ladder-cta" @click="followDecorationLink(ctaLink)">
        {{ ctaText }} ›
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
@import '@/styles/coupon-ticket.scss';

/*
  与管理端 extension/coupon-ladder/index.vue 逐值对齐：1rpx = 0.5px（375px 画布）。
  结构：hero 渐变 → 白卡上浮压住 hero 底 32rpx → 卡头 → 上楼梯档位 → 分隔线 →
  「怎么参与」虚线三步 → 说明 → CTA。颜色全部取 coupon-ticket.scss 令牌。
*/
.ladder-floor {
  position: relative;
}

.ladder-hero {
  position: relative;
  padding: 30rpx 32rpx 60rpx;
  overflow: hidden;
  color: #fff;
  border-radius: 32rpx 32rpx 0 0;
  @include coupon-hero-bg;

  /* 两枚半透明白圆：右上大圆、右下小圆，纯渐变上补一点层次（装饰层，不参与布局） */
  &::before {
    position: absolute;
    top: -128rpx;
    right: -104rpx;
    width: 300rpx;
    height: 300rpx;
    content: '';
    background: rgba(255, 255, 255, 0.13);
    border-radius: 50%;
  }

  &::after {
    position: absolute;
    right: 116rpx;
    bottom: -120rpx;
    width: 172rpx;
    height: 172rpx;
    content: '';
    background: rgba(255, 255, 255, 0.09);
    border-radius: 50%;
  }
}

.ladder-badge {
  position: relative;
  z-index: 1;
  display: inline-block;
  padding: 6rpx 18rpx;
  font-size: 20rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.6rpx;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 999rpx;
}

.ladder-title {
  position: relative;
  z-index: 1;
  margin-top: 20rpx;
  font-size: 48rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1rpx;
}

.ladder-subtitle {
  position: relative;
  z-index: 1;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.9);
}

.ladder-card {
  position: relative;
  z-index: 2;
  padding: 28rpx;
  margin-top: -32rpx;
  background: #fff;
  border-radius: 32rpx;
  @include coupon-card-elevation;
}

.ladder-card-head {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.ladder-card-title {
  font-size: 28rpx;
  font-weight: 700;
  color: $coupon-text;
}

.ladder-card-hint {
  font-size: 20rpx;
  color: $coupon-desc;
}

.ladder-tiers {
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
  margin-top: 24rpx;
}

.ladder-tier {
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 20rpx 8rpx;
  overflow: hidden;
  text-align: center;
  background: linear-gradient(180deg, $coupon-tint 0%, #fff 100%);
  border: 2rpx solid $coupon-line;
  border-radius: 20rpx;

  /*
    上楼梯：档位越高卡片越高，最高档再换成实底强调。
    高度按「2 档时第一档 106px、第二档 118px」定，肉眼可见 12px 台阶；
    内容高度约 198rpx，所以 4 档依次 212/236/260/284rpx。
  */
  &:nth-child(1) {
    min-height: 212rpx;
  }

  &:nth-child(2) {
    min-height: 236rpx;
  }

  &:nth-child(3) {
    min-height: 260rpx;
  }

  &:nth-child(4) {
    min-height: 284rpx;
  }

  &--active {
    color: #fff;
    border-color: transparent;
    @include coupon-hero-bg;
    box-shadow: 0 12rpx 28rpx rgba(255, 85, 0, 0.22);

    .ladder-tier-threshold {
      color: rgba(255, 255, 255, 0.88);
    }

    .ladder-tier-amount {
      color: #fff;
    }

    .ladder-tier-claim {
      color: $coupon-brand;
      background: #fff;
      border-color: transparent;
      box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);
    }
  }

  /* 已领取：金额降级为灰，领取按钮回灰底灰字（放在 --active 之后，最高档已领也走这里） */
  &--done {
    .ladder-tier-amount {
      color: $coupon-muted-brand;
    }

    .ladder-tier-claim {
      color: $coupon-desc;
      background: $coupon-hairline;
      border-color: transparent;
      box-shadow: none;
    }
  }
}

.ladder-tier-flag {
  position: absolute;
  top: 0;
  right: 0;
  padding: 4rpx 12rpx;
  font-size: 16rpx;
  font-weight: 700;
  line-height: 1.4;
  color: #e01b2e;
  background: #fff;
  border-radius: 0 20rpx 0 16rpx;
}

.ladder-tier-threshold {
  font-size: 20rpx;
  color: $coupon-desc;
}

.ladder-tier-amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  margin-top: 8rpx;
  line-height: 1;
  color: $coupon-brand;
}

.ladder-tier-symbol {
  margin-right: 2rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.ladder-tier-num {
  font-size: 50rpx;
  font-weight: 800;
  letter-spacing: -1rpx;
}

.ladder-tier-claim {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44rpx;
  padding: 0 24rpx;
  margin-top: 16rpx;
  font-size: 20rpx;
  font-weight: 600;
  color: $coupon-brand;
  white-space: nowrap;
  background: #fff;
  border: 2rpx solid #ffc9ad;
  border-radius: 999rpx;
}

.ladder-divider {
  height: 2rpx;
  margin: 28rpx 0 24rpx;
  background: $coupon-hairline;
}

.ladder-section-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: $coupon-text;

  &::before {
    width: 6rpx;
    height: 24rpx;
    content: '';
    background: $coupon-brand;
    border-radius: 4rpx;
  }
}

.ladder-steps {
  position: relative;
  display: flex;
  margin-top: 24rpx;

  /* 虚线串联三步：两端各让出 1/6 宽度，线正好穿过三个圆点的圆心 */
  &::before {
    position: absolute;
    top: 26rpx;
    right: 16.6%;
    left: 16.6%;
    content: '';
    border-top: 2rpx dashed $coupon-line;
  }
}

.ladder-step {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
}

.ladder-step-index {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 52rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: $coupon-brand;
  background: $coupon-tint;
  border: 4rpx solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 2rpx $coupon-line;
}

.ladder-step-name {
  margin-top: 12rpx;
  font-size: 20rpx;
  color: $coupon-text-2;
  white-space: nowrap;
}

.ladder-note {
  padding: 20rpx 24rpx;
  margin-top: 24rpx;
  font-size: 22rpx;
  line-height: 1.7;
  color: $coupon-text-2;
  background: $coupon-muted-tint;
  border-radius: 16rpx;
}

.ladder-cta {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 84rpx;
  margin-top: 28rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #fff;
  background: $coupon-brand;
  border-radius: 999rpx;
  box-shadow: 0 16rpx 36rpx rgba(255, 85, 0, 0.22);
}
</style>
