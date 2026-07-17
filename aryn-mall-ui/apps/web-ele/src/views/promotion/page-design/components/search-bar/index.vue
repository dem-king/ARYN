<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  placeholder: string;
  style: string;
  bgColor: string;
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
</script>

<template>
  <div class="search-bar-box" :style="dynamicStyles">
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
        <span class="search-placeholder">{{ showData.placeholder }}</span>
      </div>
      <div v-if="showData.showScan" class="search-bar-right">
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="#666"
          stroke-width="2"
        >
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <line x1="3" y1="8" x2="21" y2="8" />
          <line x1="8" y1="3" x2="8" y2="8" />
        </svg>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.search-bar-box {
  .search-bar-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 36px;
    padding: 0 12px;

    .search-bar-left {
      display: flex;
      gap: 6px;
      align-items: center;

      .search-icon {
        flex-shrink: 0;
      }

      .search-placeholder {
        font-size: 13px;
        color: #999;
      }
    }

    .search-bar-right {
      display: flex;
      align-items: center;
    }
  }
}
</style>
