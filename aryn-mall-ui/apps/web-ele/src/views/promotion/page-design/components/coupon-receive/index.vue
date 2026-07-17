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
        <div class="coupon-amount">
          <span class="coupon-symbol">¥</span>
          <span class="coupon-value">10</span>
        </div>
        <div class="coupon-info">
          <div class="coupon-name">优惠券</div>
          <div class="coupon-condition">满100可用</div>
          <div v-if="showData.showReceiveBtn" class="coupon-btn">领取</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.coupon-receive-box {
  .coupon-list {
    &.list-style {
      display: flex;
      gap: 8px;
      overflow-x: auto;

      .coupon-item {
        flex-shrink: 0;
        flex-direction: column;
        align-items: center;
        width: 110px;

        .coupon-amount {
          width: 100%;
          height: 50px;
          border-radius: 6px 6px 0 0;
        }

        .coupon-info {
          width: 100%;
          padding: 6px 8px;
          border-radius: 0 0 6px 6px;

          .coupon-name {
            font-size: 12px;
          }

          .coupon-condition {
            font-size: 10px;
          }

          .coupon-btn {
            margin-top: 4px;
            font-size: 10px;
          }
        }
      }
    }

    &.card-style {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .coupon-item {
        flex-direction: row;
        height: 70px;

        .coupon-amount {
          width: 80px;
          height: 100%;
          border-radius: 6px 0 0 6px;
        }

        .coupon-info {
          flex: 1;
          padding: 8px 12px;
          border-radius: 0 6px 6px 0;

          .coupon-name {
            font-size: 14px;
          }

          .coupon-condition {
            font-size: 12px;
          }

          .coupon-btn {
            margin-top: 6px;
            font-size: 12px;
          }
        }
      }
    }

    .coupon-item {
      display: flex;
      overflow: hidden;
      background: #fff;
      border-radius: 6px;
      box-shadow: 0 1px 4px rgb(0 0 0 / 8%);

      .coupon-amount {
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        background: linear-gradient(135deg, #f44, #ff6b6b);

        .coupon-symbol {
          font-size: 12px;
        }

        .coupon-value {
          font-size: 22px;
          font-weight: bold;
        }
      }

      .coupon-info {
        display: flex;
        flex-direction: column;
        justify-content: center;
        background: #fff;

        .coupon-name {
          font-weight: 500;
          color: #333;
        }

        .coupon-condition {
          margin-top: 2px;
          color: #999;
        }

        .coupon-btn {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: fit-content;
          padding: 2px 10px;
          color: #f44;
          cursor: pointer;
          border: 1px solid #f44;
          border-radius: 10px;
        }
      }
    }
  }
}
</style>
