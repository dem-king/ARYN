<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByIds, getPage } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRequireLogin } from '@/composables/useRequireLogin'
import {
  couponSubtitle,
  couponThresholdText,
  couponType,
  couponValueParts,
} from '@/utils/coupon-display'
import { couponCardLayout } from '../coupon-card-layout'
import { orderCouponsByIds, resolveCouponReceiveSource } from '../coupon-receive-source'
import { mergeReceivedCouponIds } from '../coupon-received-state'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const { locale, t } = useI18n()

const showStyle = computed(() => props.showData.showStyle || '1')
const showNum = computed(() => {
  const value = Math.floor(Number(props.showData.showNum) || 3)
  return Math.min(10, Math.max(1, value))
})
const source = computed(() => resolveCouponReceiveSource(props.showData.dataSource, showNum.value))
const showReceiveBtn = computed(() => props.showData.showReceiveBtn !== false)
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))
const { ensureLoggedIn } = useRequireLogin()

const couponList = ref<any[]>([])
// 本地记录已领取的优惠券ID，避免领取后卡片消失
const receivedIds = ref<Set<string>>(new Set())
// 卡片宽度按实际券数决定：券不足一行时长大填满，但单张不超过半行
const cardLayout = computed(() => couponCardLayout(couponList.value.length, showNum.value))
const couponCardStyle = computed(() => showStyle.value === '1' ? cardLayout.value.style : undefined)

async function fetchCoupons() {
  try {
    if (source.value.mode === 'manual') {
      couponList.value = source.value.ids.length
        ? orderCouponsByIds(await getByIds(source.value.ids), source.value.ids)
        : []
    }
    else {
      const res = await getPage({
        current: 1,
        size: showNum.value,
      })
      couponList.value = res.records || []
    }
    // 匿名领券列表不含个人领取计数；登录用户数据用于恢复刷新后的状态。
    receivedIds.value = mergeReceivedCouponIds(receivedIds.value, couponList.value)
  }
  catch {
    couponList.value = []
  }
}

function isReceived(item: any) {
  return receivedIds.value.has(item.id)
}

async function handleReceive(couponId: string) {
  // 已领取过的不再重复点击
  if (receivedIds.value.has(couponId))
    return
  // 游客点领取：记录回跳并打开登录页，不发出需登录的领券请求
  if (!ensureLoggedIn())
    return
  try {
    await addObj({ couponId })
    uni.showToast({ title: t('diy.coupon.receiveSuccess'), icon: 'success' })
    // 本地标记为已领取，不重新拉列表，避免卡片因库存不足被后端过滤而消失
    receivedIds.value.add(couponId)
  }
  catch (err: any) {
    const msg = err?.message || t('diy.coupon.receiveFailed')
    uni.showToast({ title: msg, icon: 'none' })
  }
}

/**
 * 金额区：拆成 符号 / 数值 / 单位 三段，与券卡组件同一口径。
 *
 * 中文折扣券展示「8折」；英文没有「折」的概念，换算成「20% OFF」才等价可读，
 * 与优惠券卡组件（components/coupon-card）保持同一换算，避免同一张券两处口径不同。
 */
function valueParts(item: any) {
  const parts = couponValueParts(item)
  if (couponType(item) !== '2' || !String(locale.value).startsWith('en'))
    return parts
  const discount = Number(parts.value)
  const percent = Number.isFinite(discount) && discount > 0 ? (10 - discount) * 10 : 0
  return {
    ...parts,
    symbol: '',
    unit: t('checkout.coupon.discountUnit'),
    value: String(Number(percent.toFixed(2))),
  }
}

/** 券类型标签，对应券卡上的浅橙胶囊 */
function typeLabel(item: any) {
  return couponType(item) === '2'
    ? t('diy.coupon.typeDiscount')
    : t('diy.coupon.typeAmount')
}

/** 门槛文案 */
function thresholdText(item: any) {
  return couponThresholdText(item)
}

/** 适用范围：横版票（card-style）才展示，与券卡组件同一口径（使用说明优先） */
function scopeText(item: any) {
  return couponSubtitle(item)
}

onMounted(() => {
  fetchCoupons()
})
</script>

<template>
  <view class="coupon-receive" :style="dynamicStyles">
    <!--
      2026-11 券体系重构：领券组件与四处券面统一到「白卡 + 轻投影 + 红橙只给钱」的语言，
      不再用渐变描边外框和整条红色状态带。

      showStyle=1（列表样式）：紧凑竖版券卡，一排最多 3 张，超出横滑；
        卡片宽度由 couponCardLayout() 按实际券数与「显示数量」内联给出。
      showStyle=2（卡片样式）：一卡一行横版票，左值块（胶囊/面额/门槛）+ 右信息（券名/适用范围）+ 右侧动作。
    -->
    <view
      class="coupon-list"
      :class="{
        'list-style': showStyle === '1',
        'card-style': showStyle === '2',
        'list-style-centered': showStyle === '1' && !cardLayout.fillsRow,
      }"
    >
      <view
        v-for="(item, index) in couponList"
        :key="index"
        class="coupon-item"
        :style="couponCardStyle"
      >
        <view class="coupon-face">
          <view class="coupon-pill">
            {{ typeLabel(item) }}
          </view>
          <view class="amount-row">
            <text v-if="valueParts(item).symbol" class="coupon-symbol">
              {{ valueParts(item).symbol }}
            </text>
            <text class="coupon-value">
              {{ valueParts(item).value }}
            </text>
            <text v-if="valueParts(item).unit" class="coupon-unit">
              {{ valueParts(item).unit }}
            </text>
          </view>
          <view class="coupon-threshold">
            {{ thresholdText(item) }}
          </view>
          <!-- 竖版票：券名放在券面下方，横版票的券名在右侧信息区 -->
          <view v-if="showStyle === '1'" class="coupon-name">
            {{ item.couponName }}
          </view>
        </view>

        <view v-if="showStyle === '2'" class="coupon-info">
          <view class="coupon-name">
            {{ item.couponName }}
          </view>
          <view class="coupon-desc">
            {{ scopeText(item) }}
          </view>
        </view>

        <view v-if="showReceiveBtn" class="coupon-band">
          <view
            class="coupon-btn"
            :class="{ 'coupon-btn--received': isReceived(item) }"
            @click.stop="handleReceive(item.id)"
          >
            {{ isReceived(item) ? t('diy.coupon.claimed') : t('diy.coupon.claim') }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/coupon-ticket.scss';

.coupon-receive {
  padding: 12px;

  .coupon-list {
    &.list-style {
      display: flex;
      gap: 16rpx;
      overflow-x: auto;

      &::-webkit-scrollbar {
        display: none;
      }

      // 券不足一行时居中，避免空白全部堆在右侧
      &.list-style-centered {
        justify-content: center;
      }

      .coupon-item {
        flex-direction: column;
      }
    }

    &.card-style {
      display: flex;
      flex-direction: column;
      gap: 16rpx;

      .coupon-item {
        flex-direction: row;
        align-items: stretch;
        width: 100%;
      }
    }

    // 卡面：白底 + 24rpx 圆角 + 轻投影，与券卡组件同一套形状令牌
    .coupon-item {
      position: relative;
      display: flex;
      box-sizing: border-box;
      overflow: hidden;
      background: $coupon-surface;
      border-radius: $coupon-card-radius;
      @include coupon-card-elevation;
    }

    // 竖版票上区：浅橙渐变到白，放类型胶囊 / 面额 / 门槛 / 券名
    .coupon-face {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 20rpx 12rpx 14rpx;
      text-align: center;
      background: linear-gradient(180deg, $coupon-tint 0%, #fff 100%);
    }

    // 券类型胶囊：品牌小字压在浅橙底上（对 $coupon-tint-strong ≈ 5:1），
    // 18rpx 小字不能直接压 $coupon-brand 实底（只有 3.2:1）
    .coupon-pill {
      padding: 2rpx 12rpx;
      font-size: 18rpx;
      font-weight: 600;
      color: $coupon-brand-ink;
      white-space: nowrap;
      background: $coupon-tint-strong;
      border-radius: 8rpx;
    }

    .amount-row {
      display: flex;
      align-items: baseline;
      justify-content: center;
      margin-top: 12rpx;
      line-height: 1;
      color: $coupon-brand;
    }

    .coupon-symbol {
      margin-right: 2rpx;
      font-size: 22rpx;
      font-weight: 700;
    }

    // 管理端预览按 1rpx = 0.5px 锁 48rpx ↔ 24px，改字号要同步管理端
    .coupon-value {
      font-size: 48rpx;
      font-weight: 800;
      letter-spacing: -1rpx;
    }

    .coupon-unit {
      margin-left: 2rpx;
      font-size: 22rpx;
      font-weight: 700;
    }

    .coupon-threshold {
      max-width: 100%;
      margin-top: 10rpx;
      overflow: hidden;
      font-size: 18rpx;
      color: $coupon-desc;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    // 竖版票的券名是辅助信息，走中性灰，不与「钱」抢视觉
    .coupon-name {
      width: 100%;
      margin-top: 6rpx;
      overflow: hidden;
      font-size: 18rpx;
      color: $coupon-desc;
      text-align: center;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    // 下方动作区：虚线撕口 + 领取按钮。margin/padding 与 mock 的
    // 7px 留白 / 7px 行距同值换算（14rpx），保证「一屏 3 张」时仍紧凑
    .coupon-band {
      margin-top: 14rpx;
      padding: 14rpx 16rpx 16rpx;
      border-top: $coupon-divider-width dashed $coupon-line;
    }

    .coupon-btn {
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: center;
      box-sizing: border-box;
      width: 100%;
      height: 52rpx;
      font-size: 22rpx;
      font-weight: 600;
      color: #fff;
      white-space: nowrap;
      background: $coupon-brand;
      border-radius: 999rpx;

      // 已领取：灰底灰字，视觉上明显不可再点
      &--received {
        color: $coupon-desc;
        background: #f4f5f7;
      }
    }

    // 横版票：左值块固定 $coupon-value-width，中间券名/范围，右侧动作区
    &.card-style {
      .coupon-face {
        flex: 0 0 $coupon-value-width;
        box-sizing: border-box;
        width: $coupon-value-width;
        padding: 24rpx 8rpx;
        background: linear-gradient(180deg, $coupon-tint 0%, #fff 82%);
      }

      .amount-row {
        margin-top: 12rpx;
      }

      .coupon-info {
        display: flex;
        flex-direction: column;
        flex: 1;
        justify-content: center;
        box-sizing: border-box;
        min-width: 0;
        padding: 26rpx 16rpx 24rpx 24rpx;
      }

      // 横版票的券名是主信息：29rpx 附近字号 + 深色 + 单行省略
      .coupon-name {
        margin-top: 0;
        font-size: 26rpx;
        font-weight: 600;
        color: $coupon-text;
        text-align: left;
      }

      // 使用说明与券卡组件同口径：完整换行展示，不截断使用限制
      .coupon-desc {
        margin-top: 8rpx;
        font-size: 20rpx;
        line-height: 1.4;
        color: $coupon-desc;
        overflow-wrap: break-word;
        word-break: normal;
        white-space: normal;
      }

      .coupon-band {
        display: flex;
        flex-direction: row;
        flex-shrink: 0;
        align-items: center;
        justify-content: center;
        margin-top: 0;
        padding: 0 24rpx 0 12rpx;
        border-top: none;
      }

      .coupon-btn {
        width: 128rpx;
        height: 56rpx;
      }
    }
  }
}
</style>
