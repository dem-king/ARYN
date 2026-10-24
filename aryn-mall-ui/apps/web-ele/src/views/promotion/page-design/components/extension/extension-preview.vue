<script setup lang="ts">
import type { Component } from 'vue';

import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import {
  CircleCheck,
  Clock,
  Picture,
  RefreshRight,
  Service,
  Wallet,
} from '@element-plus/icons-vue';
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
 * 服务承诺内置图标兜底：装修设置项没有图标上传入口，iconUrl 实际恒为空，
 * 不兜底保障条就只剩裸文字。按承诺文案关键词映射（与 C 端
 * diy-service-promise 的 wd-icon 映射同一关键词表，preview-parity.test.ts
 * 守卫两端同表），未命中回落对勾。
 */
const PROMISE_ICON_RULES: Array<[RegExp, Component]> = [
  [/退|换/, RefreshRight],
  [/[保正质]/, CircleCheck],
  [/服|客|售后/, Service],
  [/[速快时达]/, Clock],
  [/票|结算|款|支付/, Wallet],
];

function promiseFallbackIcon(title: string): Component {
  return (
    PROMISE_ICON_RULES.find(([pattern]) => pattern.test(title))?.[1] ??
    CircleCheck
  );
}

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
 * commonStyle 内联零值覆盖），画布以 10px 对应；图片魔方在 C 端
 * 满宽贴边，画布同样不加假留白。
 */
const flushInner = computed(() => props.type === 'image-cube');
</script>

<template>
  <div class="extension-preview">
    <div class="preview-frame" :style="frameStyle">
      <div
        class="preview-inner"
        :class="{
          'preview-inner--card': type === 'service-promise',
          'preview-inner--flush': flushInner,
        }"
      >
        <template v-if="type === 'member-benefits'">
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
        <template v-else-if="type === 'service-promise'">
          <p v-if="title" class="preview-promise-title">{{ title }}</p>
          <div v-if="entries.length === 0" class="preview-promise-empty">
            暂无服务承诺，请在装修中配置
          </div>
          <div v-else class="preview-promise-row">
            <div
              v-for="(entry, index) in entries"
              :key="index"
              class="preview-promise-item"
            >
              <span class="preview-promise-badge">
                <img
                  v-if="String(entry.iconUrl ?? '')"
                  alt=""
                  class="preview-promise-badge-image"
                  :src="String(entry.iconUrl)"
                />
                <ElIcon v-else :size="16">
                  <component
                    :is="promiseFallbackIcon(String(entry.title ?? ''))"
                  />
                </ElIcon>
              </span>
              <span class="preview-promise-text">{{ entry.title }}</span>
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
  /* 图片魔方在 C 端满宽贴边，画布同样不加假留白 */
  padding: 0;
}

.preview-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
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

/* 服务承诺保障条（与 C 端 diy-service-promise 同源，1rpx = 0.5px）：
   白卡本体挂 preview-inner--card（对应 C 端 promise-inner），圆底徽标、
   主题色 tint 与内置图标兜底和 C 端同值同表 */
.preview-inner--card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 16px rgb(31 35 41 / 6%);
}

.preview-promise-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}

.preview-promise-row {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
  justify-content: space-around;
}

.preview-promise-item {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.preview-promise-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-primary-soft, rgb(255 34 55 / 9%));
  border-radius: 50%;
}

.preview-promise-badge-image {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.preview-promise-text {
  margin-top: 4px;
  font-size: 11px;
  color: #606266;
  text-align: center;
}

.preview-promise-empty {
  padding: 20px;
  font-size: 13px;
  color: #999;
  text-align: center;
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
