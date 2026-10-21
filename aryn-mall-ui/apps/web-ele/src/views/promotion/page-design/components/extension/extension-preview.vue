<script setup lang="ts">
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

import { commonStyleRadiusOverflow } from '../common/common-style-clip';

const props = defineProps<{
  showData: Record<string, unknown>;
  type: string;
}>();

const entries = computed(
  () =>
    (props.showData.entries ?? props.showData.items ?? []) as Array<
      Record<string, unknown>
    >,
);
const title = computed(() => (props.showData.title ?? '') as string);

/**
 * 图片魔方：各布局的格位结构（编辑器画布 px；小程序端 1rpx = 0.5px）。
 * cells 与 C 端 diy-image-cube 的 items 顺序一致：一左多右布局时首格是大图。
 */
interface ImageCubePreviewCell {
  height: number;
  isBig?: boolean;
}

interface ImageCubePreviewSpec {
  bigSpan: number;
  cells: ImageCubePreviewCell[];
  cols: number;
  rows: string;
}

const imageCubeLayouts: Record<string, ImageCubePreviewSpec> = {
  '1': {
    bigSpan: 0,
    cells: [{ height: 188 }],
    cols: 1,
    rows: '188px',
  },
  '1+2': {
    bigSpan: 2,
    cells: [{ height: 188, isBig: true }, { height: 92 }, { height: 92 }],
    cols: 2,
    rows: '92px 92px',
  },
  '1+3': {
    bigSpan: 3,
    cells: [
      { height: 188, isBig: true },
      { height: 60 },
      { height: 60 },
      { height: 60 },
    ],
    cols: 2,
    rows: 'repeat(3, 60px)',
  },
  '2h': {
    bigSpan: 0,
    cells: [{ height: 92 }, { height: 92 }],
    cols: 1,
    rows: 'repeat(2, 92px)',
  },
  '2v': {
    bigSpan: 0,
    cells: [{ height: 188 }, { height: 188 }],
    cols: 2,
    rows: '188px',
  },
  '4': {
    bigSpan: 0,
    cells: [{ height: 92 }, { height: 92 }, { height: 92 }, { height: 92 }],
    cols: 2,
    rows: 'repeat(2, 92px)',
  },
};
const imageCube = computed(() => {
  const layout = String(props.showData.layout ?? '4');
  const spec = imageCubeLayouts[layout] ?? imageCubeLayouts['4']!;
  const items = (props.showData.items ?? []) as Array<{
    url?: string;
  }>;
  const cells = spec.cells.map((cell, index) => ({
    ...cell,
    url: items[index]?.url ?? '',
  }));
  return {
    cells,
    cols: spec.cols,
    bigSpan: spec.bigSpan,
    rows: spec.rows,
  };
});

/**
 * 通用样式 → 画布框：与 C 端 `utils/common-style.ts` 的 getCommonStyle 同源
 * （commonStyle 长度值两端都按 px 直出，不涉及 rpx 换算）。
 * 圆角 > 0 时必须同时裁切子内容，否则贴满框的图片/色块直角会盖住圆角弧线，
 * 运营在画布上看不到自己设置的圆角（与 C 端 getCommonStyle 同一守卫）。
 */
const frameStyle = computed(() => {
  const style = (props.showData.commonStyle ?? {}) as Record<string, unknown>;
  const number = (value: unknown) => Number(value) || 0;
  const radius = [
    style.styleLtRadius,
    style.styleRtRadius,
    style.styleLbRadius,
    style.styleRbRadius,
  ].map((value) => number(value));
  const bgStart = String(style.bgStartColor ?? '');
  const bgEnd = String(style.bgEndColor ?? '');
  const bgPicUrl = String(style.bgPicUrl ?? '');

  // 背景口径与 C 端 getCommonStyle 一致：背景图 > 渐变 > 透明
  let background = 'transparent';
  if (bgPicUrl) background = `url(${bgPicUrl}) center / 100% 100% no-repeat`;
  else if (bgStart || bgEnd)
    background = `linear-gradient(${String(style.bgColorDirection || 'to right')}, ${bgStart || bgEnd}, ${bgEnd || bgStart})`;

  return {
    background,
    borderTopLeftRadius: `${radius[0]}px`,
    borderTopRightRadius: `${radius[1]}px`,
    borderBottomLeftRadius: `${radius[2]}px`,
    borderBottomRightRadius: `${radius[3]}px`,
    margin: `${number(style.styleTopMargin)}px ${number(style.styleRightMargin)}px ${number(style.styleBottomMargin)}px ${number(style.styleLeftMargin)}px`,
    padding: `${number(style.styleTopPadding)}px ${number(style.styleRightPadding)}px ${number(style.styleBottomPadding)}px ${number(style.styleLeftPadding)}px`,
    ...commonStyleRadiusOverflow(
      props.showData.commonStyle as DiyCommonStyle | null,
    ),
  };
});

/**
 * 内层留白：多数组件的 C 端把 20rpx 留白挂内容内层（根节点 padding 会被
 * commonStyle 内联零值覆盖），画布以 10px 对应；底部导航/图片魔方在 C 端
 * 满宽贴边，画布同样不加假留白。
 */
const flushInner = computed(
  () => props.type === 'bottom-nav' || props.type === 'image-cube',
);
</script>

<template>
  <div class="extension-preview">
    <div class="preview-frame" :style="frameStyle">
      <div
        class="preview-inner"
        :class="{ 'preview-inner--flush': flushInner }"
      >
        <template v-if="type === 'coupon-combo'">
          <div
            v-for="index in Number(showData.count) || 3"
            :key="index"
            class="preview-coupon"
          >
            <div class="preview-coupon__face">
              <div
                class="preview-coupon__notch preview-coupon__notch--left"
              ></div>
              <div
                class="preview-coupon__notch preview-coupon__notch--right"
              ></div>
              <span class="preview-coupon__value">¥10</span>
              <div class="preview-coupon__info">
                <span class="preview-coupon__pill">满减券</span>
                <span class="preview-coupon__title">优惠券</span>
                <span class="preview-coupon__threshold">满100元可用</span>
              </div>
            </div>
            <span class="preview-coupon__btn">领取</span>
          </div>
        </template>
        <template
          v-else-if="type === 'member-benefits' || type === 'service-promise'"
        >
          <p v-if="title" class="preview-title">{{ title }}</p>
          <div class="preview-grid">
            <div
              v-for="(entry, index) in entries"
              :key="index"
              class="preview-entry"
            >
              <span class="entry-icon"></span>
              <span class="entry-text">{{ entry.title }}</span>
            </div>
          </div>
        </template>
        <template v-else-if="type === 'bottom-nav'">
          <div class="preview-bottomnav">
            <div
              v-for="(item, index) in entries"
              :key="index"
              class="preview-bottomnav-item"
              :style="{ color: (showData.activeColor as string) || '#ff5000' }"
            >
              {{ item.text }}
            </div>
          </div>
        </template>
        <template v-else-if="type === 'video-live'">
          <p v-if="title" class="preview-title">{{ title }}</p>
          <div class="preview-video" :data-mode="showData.mode">
            视频 / 直播
          </div>
        </template>
        <template v-else-if="type === 'image-cube'">
          <div
            class="preview-cube"
            :style="{
              gridTemplateColumns: `repeat(${imageCube.cols}, minmax(0, 1fr))`,
              gridTemplateRows: imageCube.rows,
            }"
          >
            <div
              v-for="(cell, index) in imageCube.cells"
              :key="index"
              class="preview-cube__cell"
              :style="
                cell.isBig
                  ? { gridRow: `span ${imageCube.bigSpan}` }
                  : { height: `${cell.height}px` }
              "
            >
              <img v-if="cell.url" :src="cell.url" alt="" />
              <!-- 空格位：画布底色与格位填充色几乎同色，纯色格位看起来是「白屏」，
                   必须给出可见的空位提示（与 RetailPreviewFrame 占位态同一套视觉语言） -->
              <div v-else class="preview-cube__empty">
                <ElIcon><Picture /></ElIcon>
                <span>图片</span>
              </div>
            </div>
          </div>
        </template>
        <template v-else>{{ type }}</template>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 通用样式（外边距/内边距/背景/圆角）由 .preview-frame 的内联样式承载，
   壳本身不垫留白，否则会叠出 C 端不存在的偏移 */
.preview-inner {
  /* 与 C 端内容内层 20rpx 同口径（1rpx = 0.5px） */
  padding: 10px;
}

.preview-inner--flush {
  /* 底部导航/图片魔方在 C 端满宽贴边，画布同样不加假留白 */
  padding: 0;
}

.preview-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

/* 与小程序 diy-coupon-combo 票面同源（1rpx = 0.5px） */
.preview-coupon {
  display: flex;
  align-items: center;
  padding: 3px;
  margin-bottom: 6px;
  overflow: hidden;
  background: linear-gradient(135deg, #ff6a00 0%, #ff2d2d 100%);
  border-radius: 10px;

  &__face {
    position: relative;
    display: flex;
    flex: 1;
    align-items: center;
    min-height: 50px;
    padding: 7px 10px;
    background: linear-gradient(180deg, #fff9f9 0%, #fff0eb 100%);
    border-radius: 7px;
  }

  &__notch {
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

  &__value {
    flex-shrink: 0;
    width: 70px;
    font-size: 22px;
    font-weight: 700;
    color: #ec3d2a;
    text-align: center;
  }

  &__info {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    padding-left: 8px;
  }

  &__pill {
    align-self: flex-start;
    padding: 1px 7px;
    font-size: 10px;
    color: #e8402f;
    background: #fde1d6;
    border-radius: 999px;
  }

  &__title {
    margin-top: 3px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 13px;
    font-weight: 600;
    color: #222;
    white-space: nowrap;
  }

  &__threshold {
    margin-top: 2px;
    font-size: 10px;
    color: #9a8f8c;
  }

  &__btn {
    flex-shrink: 0;
    padding: 4px 11px;
    margin-left: 6px;
    font-size: 11px;
    font-weight: 600;
    color: #fff;
    background: linear-gradient(135deg, #ff6a00 0%, #ff2d2d 100%);
    border-radius: 999px;
  }
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.preview-entry {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px;
  background: var(--el-fill-color);
  border-radius: 6px;
}

.entry-icon {
  width: 18px;
  height: 18px;
  background: var(--el-color-primary-light-7);
  border-radius: 4px;
}

.entry-text {
  font-size: 12px;
}

.preview-bottomnav {
  display: flex;
  justify-content: space-around;
  padding: 10px 0;
  background: var(--el-fill-color-light);
}

.preview-bottomnav-item {
  font-size: 12px;
}

.preview-video {
  display: grid;
  place-items: center;
  height: 120px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 6px;
}

/* 与小程序 diy-image-cube 同源（1rpx = 0.5px）：格距 8rpx→4px，格高 376/184/120rpx→188/92/60px */
.preview-cube {
  display: grid;
  gap: 4px;

  &__cell {
    overflow: hidden;

    img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__empty {
    display: grid;
    gap: 2px;
    place-items: center;
    align-content: center;
    width: 100%;
    height: 100%;
    font-size: 11px;
    color: #94a3b8;
    background: rgb(248 250 252);
    border: 1px dashed #cbd5e1;
  }
}
</style>
