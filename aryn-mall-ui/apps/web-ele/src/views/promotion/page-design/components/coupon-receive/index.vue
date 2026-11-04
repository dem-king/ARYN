<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { commonStyleRadiusOverflow } from '../common/common-style-clip';

interface CouponDataSource {
  mode?: string;
  targetIds?: string[];
}

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  dataSource?: CouponDataSource | null;
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
    // 圆角 > 0 时裁切子内容：票卡贴满根节点，直角会盖住圆角（与 C 端 getCommonStyle 同口径）
    ...commonStyleRadiusOverflow(commonStyle),
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

/** 展示样式：1 = 列表（紧凑竖版票），2 = 卡片（一卡一行横版票） */
const showStyle = computed(() => props.showData.showStyle || '1');

const showNum = computed(() => {
  const value = Math.floor(Number(props.showData.showNum) || 3);
  return Math.min(10, Math.max(1, value));
});

const manualMode = computed(() => props.showData.dataSource?.mode === 'manual');

const manualIds = computed(() =>
  (props.showData.dataSource?.targetIds ?? []).filter(Boolean),
);

// 自动模式按上限画（画布展示最坏高度）；手选模式只画真正选中的券
const previewCount = computed(() =>
  manualMode.value
    ? Math.min(manualIds.value.length, showNum.value)
    : showNum.value,
);

const couponList = computed(() => {
  return Array.from({ length: previewCount.value }, (_, i) => i);
});
</script>

<template>
  <div class="coupon-receive-box" :style="dynamicStyles">
    <div v-if="manualMode && previewCount === 0" class="coupon-empty">
      未选择优惠券
    </div>
    <div
      v-else
      class="coupon-list"
      :class="{
        'list-style': showStyle === '1',
        'card-style': showStyle === '2',
      }"
    >
      <div v-for="i in couponList" :key="i" class="coupon-item">
        <!--
          2026-11 券体系重构：与 C 端 diy-coupon-receive 同构 —— 白卡 + 轻投影，
          去掉渐变描边外框与整条状态带；红橙只给面额与领取按钮。
        -->
        <div class="coupon-face">
          <div class="coupon-pill">满减券</div>
          <div class="amount-row">
            <span class="coupon-symbol">¥</span>
            <span class="coupon-value">10</span>
            <span class="coupon-unit"></span>
          </div>
          <div class="coupon-threshold">满100元可用</div>
          <!-- 竖版票：券名放在券面下方；横版票的券名在右侧信息区 -->
          <div v-if="showStyle === '1'" class="coupon-name">优惠券</div>
        </div>

        <div v-if="showStyle === '2'" class="coupon-info">
          <div class="coupon-name">优惠券</div>
          <div class="coupon-desc">全场通用</div>
        </div>

        <div v-if="showData.showReceiveBtn" class="coupon-band">
          <div class="coupon-btn">领取</div>
        </div>
      </div>
    </div>
  </div>
</template>

<!--
  样式与小程序 diy-coupon-receive 逐值对齐（券面 / 撕口虚线 / 领取按钮）。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px。
  配色与小程序 src/styles/coupon-ticket.scss 同源，改一侧必须同步另一侧。
-->
<style scoped lang="scss">
.coupon-receive-box {
  padding: 12px;

  .coupon-empty {
    padding: 20px 0;
    font-size: 12px;
    color: #909399;
    text-align: center;
  }

  .coupon-list {
    // 票卡：白底 + 12px 圆角 + 轻投影（与券卡组件同一套形状令牌）
    .coupon-item {
      position: relative;
      box-sizing: border-box;
      display: flex;
      overflow: hidden;
      background: #fff;
      border-radius: 12px;
      box-shadow:
        0 1px 3px rgb(15 23 42 / 3%),
        0 6px 16px rgb(15 23 42 / 5%);
    }

    // 竖版票上区：浅橙渐变到白，放类型胶囊 / 面额 / 门槛 / 券名
    .coupon-face {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 10px 6px 7px;
      text-align: center;
      background: linear-gradient(180deg, #fff6f1 0%, #fff 100%);
    }

    // 券类型胶囊：品牌小字压在浅橙底上（对 $coupon-tint-strong ≈ 5:1）
    .coupon-pill {
      padding: 1px 6px;
      font-size: 9px;
      font-weight: 600;
      color: #c94300;
      white-space: nowrap;
      background: #ffece2;
      border-radius: 4px;
    }

    .amount-row {
      display: flex;
      align-items: baseline;
      justify-content: center;
      margin-top: 6px;
      line-height: 1;
      color: #f50;
    }

    .coupon-symbol {
      margin-right: 1px;
      font-size: 11px;
      font-weight: 700;
    }

    // 管理端预览按 1rpx = 0.5px 锁 48rpx ↔ 24px，改字号要同步小程序
    .coupon-value {
      font-size: 24px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }

    .coupon-unit {
      margin-left: 1px;
      font-size: 11px;
      font-weight: 700;
    }

    .coupon-threshold {
      max-width: 100%;
      margin-top: 5px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 9px;
      color: #8a8f99;
      white-space: nowrap;
    }

    // 竖版票的券名是辅助信息，走中性灰，不与「钱」抢视觉
    .coupon-name {
      width: 100%;
      margin-top: 3px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 9px;
      color: #8a8f99;
      text-align: center;
      white-space: nowrap;
    }

    // 下方动作区：虚线撕口 + 领取按钮（与券卡撕口同宽 2rpx ↔ 1px）
    .coupon-band {
      padding: 7px 8px 8px;
      margin-top: 7px;
      border-top: 1px dashed #ffe3d6;
    }

    .coupon-btn {
      box-sizing: border-box;
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 26px;
      font-size: 11px;
      font-weight: 600;
      color: #fff;
      white-space: nowrap;
      background: #f50;
      border-radius: 999px;
    }

    &.list-style {
      display: flex;
      gap: 8px;
      overflow-x: auto;

      &::-webkit-scrollbar {
        display: none;
      }

      // 画布按上限画卡：单卡 110px（220rpx 最小宽度），一排最多 3 张不换行
      .coupon-item {
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
        flex-direction: row;
        align-items: stretch;
        width: 100%;
      }

      // 横版票：左值块固定 88px（$coupon-value-width: 176rpx）
      .coupon-face {
        box-sizing: border-box;
        flex: 0 0 88px;
        width: 88px;
        padding: 12px 4px;
        background: linear-gradient(180deg, #fff6f1 0%, #fff 82%);
      }

      .amount-row {
        margin-top: 6px;
      }

      .coupon-info {
        box-sizing: border-box;
        display: flex;
        flex: 1;
        flex-direction: column;
        justify-content: center;
        min-width: 0;
        padding: 13px 8px 12px 12px;
      }

      // 横版票的券名是主信息：13px + 深色 + 单行省略
      .coupon-name {
        margin-top: 0;
        font-size: 13px;
        font-weight: 600;
        color: #1f2329;
        text-align: left;
      }

      // 与小程序券卡同口径：完整换行展示使用说明，不截断使用限制
      .coupon-desc {
        margin-top: 4px;
        font-size: 10px;
        line-height: 1.4;
        color: #8a8f99;
        word-break: normal;
        overflow-wrap: break-word;
        white-space: normal;
      }

      .coupon-band {
        display: flex;
        flex-shrink: 0;
        flex-direction: row;
        align-items: center;
        justify-content: center;
        padding: 0 12px 0 6px;
        margin-top: 0;
        border-top: none;
      }

      // 动作区按钮 128rpx × 56rpx ↔ 64px × 28px
      .coupon-btn {
        width: 64px;
        height: 28px;
      }
    }
  }
}
</style>
