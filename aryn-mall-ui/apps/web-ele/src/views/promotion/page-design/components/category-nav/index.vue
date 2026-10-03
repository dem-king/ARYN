<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed, ref, watch } from 'vue';

import { getPage as getCategoryTree } from '#/api/product/goods-category';

import { resolveCategoryFallback } from './category-fallback';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  navList: any[];
  showNum: number;
  imgSize: number;
  imgRadius: number;
  fontColor: string;
  scrollShow: boolean;
  /** 数据来源：static 手动配置（默认）；category 跟随分类（实时拉取一级分类） */
  source?: 'category' | 'static';
  /** 跟随分类模式下最多展示的一级分类数量 */
  categoryMax?: number;
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

const placeholderList = computed(() => {
  return Array.from({ length: props.showData.showNum }, (_, i) => i);
});

const itemWidth = computed(() => {
  return `${100 / props.showData.showNum}%`;
});

// ---------- 跟随分类（动态数据源） ----------

const isDynamic = computed(() => props.showData.source === 'category');

interface CategoryEntry {
  id: string;
  name: string;
  pic: string;
}

const categoryEntries = ref<CategoryEntry[]>([]);

/** 已填图但加载失败的分类，同样走首字色块兜底（与小程序实机行为一致） */
const failedImages = ref<Record<number, boolean>>({});

function handleImageError(index: number) {
  failedImages.value[index] = true;
}

watch(
  () =>
    [props.showData.source, Number(props.showData.categoryMax) || 8] as const,
  async ([source, max], _previous, onCleanup) => {
    if (source !== 'category') {
      categoryEntries.value = [];
      return;
    }
    let active = true;
    onCleanup(() => {
      active = false;
    });
    try {
      const tree = (await getCategoryTree()) ?? [];
      if (!active) return;
      failedImages.value = {};
      categoryEntries.value = tree
        .filter((node: any) => node.status !== '1')
        .sort((a: any, b: any) => (Number(a.sort) || 0) - (Number(b.sort) || 0))
        .slice(0, max)
        .map((node: any) => ({
          id: String(node.id ?? ''),
          name: node.name,
          pic: node.categoryPic || '',
        }));
    } catch {
      // 预览拉取失败时退化为占位渲染，不阻塞编辑器
      if (active) categoryEntries.value = [];
    }
  },
  { immediate: true },
);

const displayList = computed(() => {
  if (!isDynamic.value) return props.showData.navList;
  return categoryEntries.value.map((entry) => ({
    id: entry.id,
    text: entry.name,
    url: entry.pic,
  }));
});

/** 跟随分类模式下没有分类时不渲染空壳（与小程序实机一致） */
const visible = computed(() => {
  return !isDynamic.value || displayList.value.length > 0;
});

function showFallback(item: any, index: number) {
  return isDynamic.value && (!item.url || failedImages.value[index] === true);
}

const fallbackFontSize = computed(() => {
  return Math.max(10, Math.round((props.showData.imgSize || 25) * 0.45));
});
</script>

<template>
  <div v-if="visible" class="category-nav-box" :style="dynamicStyles">
    <div
      class="category-nav-grid"
      :class="{ 'scroll-mode': showData.scrollShow }"
    >
      <template v-if="displayList.length > 0">
        <div
          v-for="(item, index) in displayList"
          :key="item.id || index"
          class="nav-item"
          :style="{ width: itemWidth }"
        >
          <!--
            跟随分类模式下未配图/图挂掉的分类渲染首字色块，
            色板与小程序 resolveCategoryFallback 镜像（category-fallback.ts）
          -->
          <div
            v-if="showFallback(item, index)"
            class="nav-icon nav-fallback"
            :style="{
              width: `${showData.imgSize}px`,
              height: `${showData.imgSize}px`,
              borderRadius: `${showData.imgRadius}px`,
              backgroundColor: resolveCategoryFallback(item.text, item.id)
                .bgColor,
              fontSize: `${fallbackFontSize}px`,
            }"
          >
            {{ resolveCategoryFallback(item.text, item.id).text }}
          </div>
          <div class="nav-icon" v-else-if="item.url">
            <img
              :src="item.url"
              :style="{
                width: `${showData.imgSize}px`,
                height: `${showData.imgSize}px`,
                borderRadius: `${showData.imgRadius}px`,
              }"
              @error="handleImageError(index)"
            />
          </div>
          <div
            class="nav-icon nav-icon-placeholder"
            v-else
            :style="{
              width: `${showData.imgSize}px`,
              height: `${showData.imgSize}px`,
              borderRadius: `${showData.imgRadius}px`,
            }"
          >
            <svg
              viewBox="0 0 24 24"
              :width="showData.imgSize * 0.6"
              :height="showData.imgSize * 0.6"
              fill="none"
              stroke="#ccc"
              stroke-width="1.5"
            >
              <rect x="3" y="3" width="18" height="18" rx="2" />
            </svg>
          </div>
          <span class="nav-text" :style="{ color: showData.fontColor }">
            {{ item.text }}
          </span>
        </div>
      </template>
      <template v-else>
        <div
          v-for="i in placeholderList"
          :key="i"
          class="nav-item"
          :style="{ width: itemWidth }"
        >
          <div
            class="nav-icon nav-icon-placeholder"
            :style="{
              width: `${showData.imgSize}px`,
              height: `${showData.imgSize}px`,
              borderRadius: `${showData.imgRadius}px`,
            }"
          >
            <svg
              viewBox="0 0 24 24"
              :width="showData.imgSize * 0.6"
              :height="showData.imgSize * 0.6"
              fill="none"
              stroke="#ccc"
              stroke-width="1.5"
            >
              <rect x="3" y="3" width="18" height="18" rx="2" />
            </svg>
          </div>
          <span class="nav-text" :style="{ color: showData.fontColor }">
            导航
          </span>
        </div>
      </template>
    </div>
  </div>
</template>

<!--
  样式与小程序 diy-category-nav 逐值对齐：容器上下内边距 12px，
  单元上下 8px、文字上边距 6px / 12px 字号并单行截断。
-->
<style scoped lang="scss">
.category-nav-box {
  padding: 12px 0;

  .category-nav-grid {
    display: flex;
    flex-wrap: wrap;

    &.scroll-mode {
      flex-wrap: nowrap;
      overflow-x: auto;
      white-space: nowrap;
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 8px 0;

      .nav-icon {
        display: flex;
        align-items: center;
        justify-content: center;

        img {
          display: block;
          object-fit: contain;
        }
      }

      .nav-icon-placeholder {
        background-color: #f5f7fa;
      }

      /* 与小程序 .category-nav-fallback 逐值对齐（字号为图标的 0.45 倍，白字加粗） */
      .nav-fallback {
        align-items: center;
        justify-content: center;
        overflow: hidden;
        font-weight: 600;
        line-height: 1;
        color: #fff;
      }

      .nav-text {
        max-width: 100%;
        margin-top: 6px;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 12px;
        line-height: 1.2;
        text-align: center;
        white-space: nowrap;
      }
    }
  }
}
</style>
