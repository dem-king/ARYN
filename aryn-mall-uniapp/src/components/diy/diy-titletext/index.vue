<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => { },
  },
})
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))
</script>

<template>
  <view class="base" :style="dynamicStyles">
    <view
      class="title" :class="{
        'title-center': showData.titleCenter,
        'title-left': !showData.titleCenter,
      }"
    >
      <view
        class="diy-title dark:text-white!" :style="{
          'font-size': `${showData.titleSize}px`,
          'color': `${showData.titleColor}`,
          'font-weight': `${showData.titleWeight === '1' ? '700' : ''}`,
        }"
      >
        <view>{{ showData.title }}</view>
      </view>
      <view class="more-btn" @click="followDecorationLink(showData.link)">
        <text
          v-if="showData.moreBtn && showData.moreBtnStyle !== '3'"
          :style="{ 'color': showData.moreBtnColor, 'font-weight': `${showData.moreBtnWeight === '1' ? '700' : ''}`, 'font-size': `${showData.moreBtnSize}px` }"
        >
          {{ showData.moreBtnText }}
        </text>
        <wd-icon
          v-if="showData.moreBtnStyle !== '1'" :color="showData.moreBtnColor" name="arrow-right"
          :custom-style="`font-weight: ${showData.moreBtnWeight === '1' ? '700' : ''}`" :size="showData.moreBtnSize"
        />
      </view>
    </view>
    <view
      v-if="showData.showDesc" class="diy-desc" :style="{
        'font-size': `${showData.descSize}px`,
        'color': `${showData.descColor}`,
        'font-weight': `${showData.descWeight === '1' ? '700' : ''}`,
        'text-align': `${showData.descCenter ? 'center' : ''}`,
      }"
    >
      <view>{{ showData.desc }}</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.el-divider--horizontal {
  margin: 0% !important;
}

.title {
  position: relative;
  display: flex;
  align-items: center;
  padding-bottom: 10px;

  &.title-left {
    justify-content: flex-start;

    .diy-title {
      text-align: left;
    }

    .more-btn {
      position: absolute;
      top: 0;
      right: 0;
      transform: translateY(0);
    }
  }

  &.title-center {
    justify-content: center;

    .diy-title {
      text-align: center;
    }

    .more-btn {
      position: absolute;
      top: -10%;
      right: 0;
      transform: translateY(0);
    }
  }
}
</style>
