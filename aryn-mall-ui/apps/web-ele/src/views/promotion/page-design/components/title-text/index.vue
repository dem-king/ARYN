<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { ArrowRight } from '@element-plus/icons-vue';
import { ElDivider, ElIcon } from 'element-plus';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  titleSize: number;
  bgColorDirection: string;
  bgStartColor: string;
  bgEndColor: string;
  titleColor: string;
  titleWeight: string;
  title: string;
  moreBtnStyle: string;
  moreBtnSize: number;
  moreBtn: boolean;
  moreBtnText: string;
  moreBtnWeight: string;
  descSize: number;
  descColor: string;
  descWeight: string;
  desc: string;
  bottomLine: string;
  showDesc: boolean;
  moreBtnColor: string;
}

const props = defineProps<{
  showData: ShowData;
}>();
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
      background: `linear-gradient(${commonStyle.bgColorDirection || 'to right'},
				${commonStyle.bgStartColor || ''},
				${commonStyle.bgEndColor || commonStyle.bgStartColor || ''})`,
    }),
  };
});
</script>

<template>
  <div class="base" :style="dynamicStyles">
    <div class="title">
      <div
        class="diy-title"
        :style="{
          'font-size': `${showData.titleSize}px`,
          color: `${showData.titleColor}`,
          'font-weight': `${showData.titleWeight === '1' ? 'bold' : ''}`,
        }"
      >
        <p>{{ showData.title }}</p>
      </div>
      <div class="more-btn">
        <span
          v-if="showData.moreBtn && showData.moreBtnStyle !== '3'"
          :style="{
            color: showData.moreBtnColor,
            'font-weight': `${showData.moreBtnWeight === '1' ? 'bold' : ''}`,
            'font-size': `${showData.moreBtnSize}px`,
          }"
        >
          {{ showData.moreBtnText }}
        </span>
        <ElIcon
          :style="{
            color: showData.moreBtnColor,
            'font-size': `${showData.moreBtnSize}px`,
          }"
          v-if="showData.moreBtn && showData.moreBtnStyle !== '1'"
        >
          <ArrowRight />
        </ElIcon>
      </div>
    </div>
    <div
      v-if="showData.showDesc"
      class="diy-desc"
      :style="{
        fontSize: `${showData.descSize}px`,
        color: showData.descColor,
        fontWeight: showData.descWeight === '1' ? 'bold' : '',
      }"
    >
      <p>{{ showData.desc }}</p>
    </div>
    <ElDivider v-if="showData.bottomLine" />
  </div>
</template>

<style scoped lang="scss">
.el-divider--horizontal {
  margin: 0% !important;
}

.title {
  display: flex;
  justify-content: space-between;
  padding-bottom: 10px;

  .more-btn {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
