<script setup lang="ts">
/**
 * 评价图片墙：1~2 张单图、3~4 张等分一行、≥5 张取前 4 张并叠加 +N。
 * 主评与追评共用，点击任意图片进入系统预览。
 */
const props = withDefaults(defineProps<{
  urls?: string[]
}>(), {
  urls: () => [],
})

function previewImage(index: number) {
  const urls = props.urls || []
  if (!urls.length)
    return
  uni.previewImage({
    current: index,
    urls,
  })
}
</script>

<template>
  <view v-if="urls && urls.length > 0" class="mt-10rpx">
    <!-- 1 或 2 张：只显示第一张 -->
    <view v-if="urls.length <= 2" class="h-[120px] w-[120px]">
      <wd-img width="100%" height="100%" :src="urls[0]" radius="12rpx" :enable-preview="true" />
    </view>

    <!-- 3~4 张：一行等分 -->
    <view v-else-if="urls.length <= 4" class="flex overflow-hidden rounded-12rpx">
      <view
        v-for="(pic, picIndex) in urls"
        :key="picIndex"
        class="h-[100px] flex-1 overflow-hidden"
        :class="[
          picIndex === 0 ? 'rounded-l-12rpx' : '',
          picIndex === urls.length - 1 ? 'rounded-r-12rpx' : 'mx-2rpx',
        ]"
      >
        <wd-img width="100%" height="100%" :src="pic" @click="previewImage(picIndex)" />
      </view>
    </view>

    <!-- ≥5 张：最多显示 4 张，最后一张 +N 角标 -->
    <view v-else class="flex gap-6rpx">
      <view
        v-for="(pic, picIndex) in urls.slice(0, 4)"
        :key="picIndex"
        class="relative h-[86px] flex-1 overflow-hidden rounded-10rpx"
      >
        <wd-img width="100%" height="100%" :src="pic" @click="previewImage(picIndex)" />
        <view
          v-if="picIndex === 3 && urls.length > 4"
          class="absolute inset-0 flex items-center justify-center bg-black/40 text-28rpx text-white font-bold"
        >
          +{{ urls.length - 4 }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped></style>
