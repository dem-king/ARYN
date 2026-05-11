<script setup lang="ts">
import { useDiyStyle } from '@/composables/useDiyStyle'

interface ShowData {
  commonStyle?: any | null
  color: string
  bgColorDirection: string
  bgStartColor: string
  bgEndColor: string
  contentList: any[]
  speed: number
  direction: string
  titleType: string
  titleUrl: string
  titleText: string
  titleColor: string
  titleSize: number
  titleStyle: string
}
const props = defineProps<{
  showData: ShowData
}>()
const getNoticeText = computed(() => {
  return props.showData.contentList.map(v => v.content)
})
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

// 跳转
function toPage(text: string, index: number) {
  const item = props.showData.contentList[index]
  if (item.type === 1) {
    toJumpUrl(item.url)
  }
}
</script>

<template>
  <view :style="dynamicStyles">
    <wd-notice-bar
      custom-class="dark:text-white!"
      :text="getNoticeText" :direction="showData.direction" :color="showData.color"
      :speed="showData.speed" background-color="transparent !important;" custom-style="padding: 0 !important;" @click="toPage"
    >
      <template #prefix>
        <image
          v-if="showData.titleType === '1'" :src="showData.titleUrl" mode="scaleToFill"
          style="width: 18px; height: 18px;"
        />
        <view
          v-else-if="showData.titleType === '2'"
          class="dark:text-white!" :style="{
            color: showData.titleColor,
            fontSize: `${showData.titleSize}px`,
            fontWeight: showData.titleStyle === '1' ? 'bold' : 'normal',
          }"
        >
          {{ showData.titleText }}
        </view>
        <wd-icon v-else name="notification" :size="showData.titleSize" custom-class="dark:text-white!" :color="showData.titleColor" :style="{ fontWeight: showData.titleStyle === '1' ? 'bold' : 'normal' }" />
      </template>
    </wd-notice-bar>
  </view>
</template>

<style lang="scss" scoped>

</style>
