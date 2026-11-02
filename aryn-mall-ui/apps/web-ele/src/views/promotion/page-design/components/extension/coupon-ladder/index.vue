<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import type { DecorationLink } from '../../../../page-designer/schema/types';

import { computed } from 'vue';

import { commonStyleRadiusOverflow } from '../../common/common-style-clip';

interface CouponLadderShowData {
  badgeText?: string;
  commonStyle?: DiyCommonStyle | null;
  ctaLink?: DecorationLink | null;
  ctaText?: string;
  dataSource?: null | { mode?: string; targetIds?: string[] };
  noteText?: string;
  showClaimBtn?: boolean;
  showNum?: number;
  subtitle?: string;
  title?: string;
}

const props = defineProps<{ showData: CouponLadderShowData }>();

const defaultCommonStyle: DiyCommonStyle = {
  styleTopMargin: 0,
  styleBottomMargin: 10,
  styleLeftMargin: 12,
  styleRightMargin: 12,
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

/**
 * 画布用示例档位：真实档位来自 coupon_info（门槛 + 面额），画布不拉券列表，
 * 与「优惠券」组件一致按上限画。数字仅示意形状，线上完全由券决定。
 */
const PLACEHOLDER_TIERS = [
  { amount: 10, threshold: 99 },
  { amount: 30, threshold: 199 },
  { amount: 50, threshold: 299 },
  { amount: 80, threshold: 399 },
];

const showNum = computed(() => {
  const value = Math.floor(Number(props.showData.showNum) || 3);
  return Math.min(4, Math.max(2, value));
});

const manualIds = computed(() =>
  (props.showData.dataSource?.targetIds ?? []).filter(Boolean),
);

const isManual = computed(() => props.showData.dataSource?.mode === 'manual');

const tierCount = computed(() =>
  isManual.value
    ? Math.min(manualIds.value.length, showNum.value)
    : showNum.value,
);

const tiers = computed(() => PLACEHOLDER_TIERS.slice(0, tierCount.value));

const showClaimBtn = computed(() => props.showData.showClaimBtn !== false);

const ctaVisible = computed(
  () =>
    Boolean(String(props.showData.ctaText ?? '').trim()) &&
    Boolean(props.showData.ctaLink?.path),
);

const dynamicStyles = computed(() => {
  const commonStyle = props.showData.commonStyle ?? defaultCommonStyle;
  const bgStart = commonStyle.bgStartColor;
  const bgEnd = commonStyle.bgEndColor;
  const hasGradient = Boolean(bgStart || bgEnd);
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
    ...commonStyleRadiusOverflow(commonStyle),
    ...(commonStyle.bgPicUrl
      ? {
          background: `url(${commonStyle.bgPicUrl})`,
          backgroundRepeat: 'no-repeat',
          backgroundPosition: 'center',
          backgroundSize: '100% 100%',
        }
      : {
          background: hasGradient
            ? `linear-gradient(${commonStyle.bgColorDirection || 'to right'}, ${bgStart || bgEnd}, ${bgEnd || bgStart})`
            : 'transparent',
        }),
  };
});
</script>

<template>
  <div class="ladder-floor" :style="dynamicStyles">
    <div class="ladder-hero">
      <span v-if="showData.badgeText" class="ladder-badge">
        {{ showData.badgeText }}
      </span>
      <span v-else class="ladder-badge ladder-badge--ghost">活动标签</span>
      <div class="ladder-title">{{ showData.title || '满减狂欢' }}</div>
      <div v-if="showData.subtitle" class="ladder-subtitle">
        {{ showData.subtitle }}
      </div>
    </div>

    <div class="ladder-card">
      <div class="ladder-card-head">
        <span class="ladder-card-title">阶梯满减</span>
        <span class="ladder-card-hint">买得越多，省得越多</span>
      </div>

      <div v-if="tierCount === 0" class="ladder-empty">未选择优惠券</div>
      <div v-else class="ladder-tiers">
        <div
          v-for="(tier, index) in tiers"
          :key="index"
          class="ladder-tier"
          :class="{ 'ladder-tier--active': index === tiers.length - 1 }"
        >
          <span v-if="index === tiers.length - 1" class="ladder-tier-flag">
            最划算
          </span>
          <div class="ladder-tier-threshold">满{{ tier.threshold }}元</div>
          <div class="ladder-tier-amount">
            <span class="ladder-tier-symbol">¥</span>
            <span class="ladder-tier-num">{{ tier.amount }}</span>
          </div>
          <div v-if="showClaimBtn" class="ladder-tier-claim">领取</div>
        </div>
      </div>

      <div class="ladder-divider"></div>

      <div class="ladder-section-title">怎么参与</div>
      <div class="ladder-steps">
        <div class="ladder-step">
          <div class="ladder-step-index">1</div>
          <div class="ladder-step-name">领取优惠券</div>
        </div>
        <div class="ladder-step">
          <div class="ladder-step-index">2</div>
          <div class="ladder-step-name">加购物车</div>
        </div>
        <div class="ladder-step">
          <div class="ladder-step-index">3</div>
          <div class="ladder-step-name">下单自动减</div>
        </div>
      </div>

      <div v-if="showData.noteText" class="ladder-note">
        {{ showData.noteText }}
      </div>

      <div v-if="ctaVisible" class="ladder-cta">{{ showData.ctaText }} ›</div>

      <div class="ladder-canvas-tip">
        画布为示例档位，线上按所选满减券实时渲染
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
/*
  与小程序 diy-coupon-ladder 逐值对齐：画布 375px，1rpx = 0.5px。
  结构：hero 渐变 → 白卡上浮压住 hero 底 32rpx(16px) → 卡头 → 上楼梯档位 → 分隔线 →
  「怎么参与」虚线三步 → 说明 → CTA。颜色 / 尺寸取 C 端同值（px = rpx / 2）。
*/
.ladder-floor {
  position: relative;
}

.ladder-hero {
  position: relative;
  padding: 15px 16px 30px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(118deg, #ff7a1f 0%, #ff4a10 56%, #ff2d2d 100%);
  border-radius: 16px 16px 0 0;

  /* 两枚半透明白圆，与 C 端同位置同透明度 */
  &::before {
    position: absolute;
    top: -64px;
    right: -52px;
    width: 150px;
    height: 150px;
    content: '';
    background: rgb(255 255 255 / 13%);
    border-radius: 50%;
  }

  &::after {
    position: absolute;
    right: 58px;
    bottom: -60px;
    width: 86px;
    height: 86px;
    content: '';
    background: rgb(255 255 255 / 9%);
    border-radius: 50%;
  }
}

.ladder-badge {
  position: relative;
  z-index: 1;
  display: inline-block;
  padding: 3px 9px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.3px;
  background: rgb(255 255 255 / 22%);
  border-radius: 999px;

  &--ghost {
    color: rgb(255 255 255 / 70%);
    background: rgb(255 255 255 / 12%);
  }
}

.ladder-title {
  position: relative;
  z-index: 1;
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.5px;
}

.ladder-subtitle {
  position: relative;
  z-index: 1;
  margin-top: 6px;
  font-size: 11px;
  color: rgb(255 255 255 / 90%);
}

.ladder-card {
  position: relative;
  z-index: 2;
  padding: 14px;
  margin-top: -16px;
  background: #fff;
  border-radius: 16px;
  box-shadow:
    0 1px 3px rgb(15 23 42 / 3%),
    0 6px 16px rgb(15 23 42 / 5%);
}

.ladder-card-head {
  display: flex;
  gap: 6px;
  align-items: baseline;
}

.ladder-card-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
}

.ladder-card-hint {
  font-size: 10px;
  color: #8a8f99;
}

.ladder-empty {
  padding: 20px 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.ladder-tiers {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  margin-top: 12px;
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
  padding: 10px 4px;
  overflow: hidden;
  text-align: center;
  background: linear-gradient(180deg, #fff6f1 0%, #fff 100%);
  border: 1px solid #ffe3d6;
  border-radius: 10px;

  /*
    上楼梯：档位越高卡片越高，最高档再换成实底强调。
    高度按「2 档时第一档 106px、第二档 118px」定，肉眼可见 12px 台阶；
    内容高度约 99px，所以 4 档依次 106/118/130/142px。
  */
  &:nth-child(1) {
    min-height: 106px;
  }

  &:nth-child(2) {
    min-height: 118px;
  }

  &:nth-child(3) {
    min-height: 130px;
  }

  &:nth-child(4) {
    min-height: 142px;
  }

  &--active {
    color: #fff;
    background: linear-gradient(118deg, #ff7a1f 0%, #ff4a10 56%, #ff2d2d 100%);
    border-color: transparent;
    box-shadow: 0 6px 14px rgb(255 85 0 / 22%);

    .ladder-tier-threshold {
      color: rgb(255 255 255 / 88%);
    }

    .ladder-tier-amount {
      color: #fff;
    }

    .ladder-tier-claim {
      color: #f50;
      background: #fff;
      border-color: transparent;
      box-shadow: 0 2px 6px rgb(0 0 0 / 10%);
    }
  }
}

.ladder-tier-flag {
  position: absolute;
  top: 0;
  right: 0;
  padding: 2px 6px;
  font-size: 8px;
  font-weight: 700;
  line-height: 1.4;
  color: #e01b2e;
  background: #fff;
  border-radius: 0 10px 0 8px;
}

.ladder-tier-threshold {
  font-size: 10px;
  color: #8a8f99;
}

.ladder-tier-amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  margin-top: 4px;
  line-height: 1;
  color: #f50;
}

.ladder-tier-symbol {
  margin-right: 1px;
  font-size: 11px;
  font-weight: 700;
}

.ladder-tier-num {
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.5px;
}

.ladder-tier-claim {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  padding: 0 12px;
  margin-top: 8px;
  font-size: 10px;
  font-weight: 600;
  color: #f50;
  white-space: nowrap;
  background: #fff;
  border: 1px solid #ffc9ad;
  border-radius: 999px;
}

.ladder-divider {
  height: 1px;
  margin: 14px 0 12px;
  background: #f1f2f4;
}

.ladder-section-title {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  font-weight: 700;
  color: #1f2329;

  &::before {
    width: 3px;
    height: 12px;
    content: '';
    background: #f50;
    border-radius: 2px;
  }
}

.ladder-steps {
  position: relative;
  display: flex;
  margin-top: 12px;

  /* 虚线串联三步：两端各让出 1/6 宽度，线正好穿过三个圆点的圆心 */
  &::before {
    position: absolute;
    top: 13px;
    right: 16.6%;
    left: 16.6%;
    content: '';
    border-top: 1px dashed #ffe3d6;
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
  width: 26px;
  height: 26px;
  font-size: 11px;
  font-weight: 700;
  color: #f50;
  background: #fff6f1;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #ffe3d6;
}

.ladder-step-name {
  margin-top: 6px;
  font-size: 10px;
  color: #5b616e;
  white-space: nowrap;
}

.ladder-note {
  padding: 10px 12px;
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.7;
  color: #5b616e;
  background: #fafbfc;
  border-radius: 8px;
}

.ladder-cta {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  margin-top: 14px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: #f50;
  border-radius: 999px;
  box-shadow: 0 8px 18px rgb(255 85 0 / 22%);
}

.ladder-canvas-tip {
  margin-top: 10px;
  font-size: 10px;
  color: #c0c4cc;
  text-align: center;
}
</style>
