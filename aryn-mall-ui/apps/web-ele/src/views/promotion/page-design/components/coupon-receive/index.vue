<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  showStyle: string;
  showNum: number;
  showReceiveBtn: boolean;
}

const props = defineProps<{ showData: ShowData }>();

const defaultCommonStyle: DiyCommonStyle = {
  styleTopMargin: 0,
  styleBottomMargin: 0,
  styleLeftMargin: 0,
  styleRightMargin: 0,
  styleTopPadding: 0,
  styleBottomPadding: 0,
  styleLeftPadding: 0,
  styleRightPadding: 0,
  styleLtRadius: 0,
  styleRtRadius: 0,
  styleLbRadius: 0,
  styleRbRadius: 0,
  bgColorDirection: 'to right',
  bgStartColor: '',
  bgEndColor: '',
  bgPicUrl: '',
};

const dynamicStyles = computed(() => {
  const commonStyle = props.showData.commonStyle ?? defaultCommonStyle;
  return {
    marginTop: `${commonStyle.styleTopMargin}px`,
    marginLeft: `${commonStyle.styleLeftMargin}px`,
    marginRight: `${commonStyle.styleRightMargin}px`,
    marginBottom: `${commonStyle.styleBottomMargin}px`,
    paddingTop: `${commonStyle.styleTopPadding}px`,
    paddingLeft: `${commonStyle.styleLeftPadding}px`,
    paddingRight: `${commonStyle.styleRightPadding}px`,
    paddingBottom: `${commonStyle.styleBottomPadding}px`,
    borderTopLeftRadius: `${commonStyle.styleLtRadius}px`,
    borderTopRightRadius: `${commonStyle.styleRtRadius}px`,
    borderBottomLeftRadius: `${commonStyle.styleLbRadius}px`,
    borderBottomRightRadius: `${commonStyle.styleRbRadius}px`,
    ...(commonStyle.bgPicUrl && {
      background: `url(${commonStyle.bgPicUrl})`,
      backgroundRepeat: 'no-repeat',
      backgroundPosition: 'center',
      backgroundSize: '100% 100%',
    }),
    ...(!commonStyle.bgPicUrl && {
      background: `linear-gradient(${commonStyle.bgColorDirection || 'to right'}, ${commonStyle.bgStartColor || ''}, ${commonStyle.bgEndColor || commonStyle.bgStartColor || ''})`,
    }),
  };
});

const couponList = computed(() => {
  return Array.from({ length: props.showData.showNum }, (_, i) => i);
});
</script>

<template>
  <div class="coupon-receive-box" :style="dynamicStyles">
    <div
      class="coupon-list"
      :class="{
        'list-style': showData.showStyle === '1',
        'card-style': showData.showStyle === '2',
      }"
    >
      <div v-for="i in couponList" :key="i" class="coupon-item">
        <div class="coupon-face">
          <div class="coupon-notch coupon-notch--left"></div>
          <div class="coupon-notch coupon-notch--right"></div>
          <div class="coupon-pill">满减券</div>
          <div class="amount-row">
            <span class="coupon-symbol">¥</span>
            <span class="coupon-value">10</span>
            <span class="coupon-unit"></span>
          </div>
          <div class="coupon-name">优惠券</div>
        </div>
        <div class="coupon-band">
          <span class="coupon-condition">满100元可用</span>
          <div v-if="showData.showReceiveBtn" class="coupon-btn">领取</div>
        </div>
      </div>
    </div>
  </div>
</template>

<!--
  样式与小程序 diy-coupon-receive 逐值对齐（票面 / 状态带 / 票根缺口）。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px。
  配色与小程序 src/styles/coupon-ticket.scss 同源，改一侧必须同步另一侧。
-->
<style scoped lang="scss">
.coupon-receive-box {
  padding: 12px;

  .coupon-list {
    &.list-style {
      display: flex;
      gap: 8px;
      overflow-x: auto;

      &::-webkit-scrollbar {
        display: none;
      }

      .coupon-item {
        box-sizing: border-box;
        flex: 0 0 auto;
        flex-direction: column;
        width: 110px;
      }
    }

    &.card-style {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .coupon-item {
        flex-direction: column;
      }
    }

    .coupon-item {
      display: flex;
      // 渐变描边：与小程序一致
      padding: 3px;
      overflow: hidden;
      background: linear-gradient(135deg, #ff6a00 0%, #ff2d2d 100%);
      border-radius: 10px;
    }

    .coupon-face {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 8px 6px;
      background: linear-gradient(180deg, #fff9f9 0%, #fff0eb 100%);
      border-radius: 7px 7px 0 0;
    }

    // 票根缺口：贴在票面左右边缘垂直中线上，外侧半圆被外框裁掉
    .coupon-notch {
      position: absolute;
      top: 50%;
      z-index: 2;
      width: 9px;
      height: 9px;
      border-radius: 50%;
      transform: translateY(-50%);

      &--left {
        left: -4.5px;
        background: #f9531a;
      }

      &--right {
        right: -4.5px;
        background: #fa2431;
      }
    }

    .coupon-pill {
      padding: 0.5px 6px;
      font-size: 9px;
      color: #e8402f;
      background: #fde1d6;
      border-radius: 999px;
    }

    .amount-row {
      display: flex;
      align-items: baseline;
      justify-content: center;
      margin-top: 3px;
      color: #ec3d2a;
    }

    .coupon-symbol {
      font-size: 12px;
      font-weight: 600;
    }

    .coupon-value {
      font-size: 22px;
      font-weight: 700;
      line-height: 1.05;
    }

    .coupon-unit {
      font-size: 12px;
      font-weight: 600;
    }

    .coupon-name {
      width: 100%;
      margin-top: 3px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 11px;
      color: #222;
      text-align: center;
      white-space: nowrap;
    }

    .coupon-band {
      box-sizing: border-box;
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: space-between;
      height: 26px;
      padding: 0 6px;
      background: linear-gradient(180deg, #ff8a7e 0%, #ff343d 100%);
      border-radius: 0 0 7px 7px;
    }

    .coupon-condition {
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 9px;
      color: #fff;
      white-space: nowrap;
    }

    .coupon-btn {
      flex-shrink: 0;
      padding: 1px 6px;
      margin-left: 4px;
      font-size: 10px;
      color: #ec3d2a;
      background: #fff;
      border-radius: 999px;
    }
  }
}
</style>
