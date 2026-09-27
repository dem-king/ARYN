<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  placeholder: string;
  style: string;
  bgColor: string;
  hotWords?: string | string[];
  showScan: boolean;
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

const barRadius = computed(() => {
  return props.showData.style === '1' ? '20px' : '4px';
});

/** 小程序配置热词时轮播展示，未配置则用占位文案；预览取第一个热词呈现静态效果 */
const hotWords = computed<string[]>(() => {
  const raw = props.showData.hotWords;
  if (Array.isArray(raw)) {
    return raw.map((word) => String(word).trim()).filter(Boolean);
  }
  if (typeof raw === 'string') {
    return raw
      .split(/[,，]/)
      .map((word) => word.trim())
      .filter(Boolean);
  }
  return [];
});

const displayText = computed(
  () => hotWords.value[0] || props.showData.placeholder || '搜索商品',
);
</script>

<template>
  <div class="search-bar-box" :style="dynamicStyles">
    <div class="search-bar-row">
      <div
        class="search-bar-inner"
        :style="{
          backgroundColor: showData.bgColor,
          borderRadius: barRadius,
        }"
      >
        <div class="search-bar-left">
          <svg
            class="search-icon"
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="#999"
            stroke-width="2"
          >
            <circle cx="11" cy="11" r="7" />
            <line x1="16.5" y1="16.5" x2="21" y2="21" />
          </svg>
          <span class="search-placeholder">{{ displayText }}</span>
        </div>
      </div>
      <!-- 小程序端搜索栏右侧还有消息铃铛（带未读角标）与扫码入口，预览按同尺寸静态呈现 -->
      <div class="search-bar-bell">
        <svg
          viewBox="0 0 24 24"
          width="22"
          height="22"
          fill="none"
          stroke="#333"
          stroke-width="1.8"
        >
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" />
        </svg>
      </div>
      <div v-if="showData.showScan" class="search-bar-scan">
        <svg
          viewBox="0 0 24 24"
          width="22"
          height="22"
          fill="none"
          stroke="#333"
          stroke-width="1.8"
        >
          <path
            d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2"
          />
          <line x1="3" y1="12" x2="21" y2="12" />
        </svg>
      </div>
    </div>
  </div>
</template>

<!--
  样式与小程序 diy-search-bar 逐值对齐：外层左右内边距 12px、上下 6px，
  输入区高 36px，右侧铃铛与扫码图标 22px。
-->
<style scoped lang="scss">
.search-bar-box {
  .search-bar-row {
    display: flex;
    align-items: center;
    padding: 6px 12px;
  }

  .search-bar-inner {
    display: flex;
    flex: 1;
    align-items: center;
    height: 36px;
    padding: 0 12px;

    .search-bar-left {
      display: flex;
      gap: 6px;
      align-items: center;
      min-width: 0;

      .search-icon {
        flex-shrink: 0;
      }

      .search-placeholder {
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 14px;
        color: #999;
        white-space: nowrap;
      }
    }
  }

  .search-bar-bell,
  .search-bar-scan {
    display: flex;
    flex: none;
    align-items: center;
    margin-left: 10px;
  }
}
</style>
