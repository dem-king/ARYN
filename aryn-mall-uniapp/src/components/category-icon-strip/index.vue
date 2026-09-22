<script setup lang="ts">
/**
 * 一级分类圆形图标横滑条（分类页顶部）。
 *
 * 对应参考图 1 的上半部分：圆形图标 + 名称，选中项文字变主题色并显示描边；
 * 右端固定「展开」入口，点击后由父组件打开「全部分类」浮层（参考图 2）。
 *
 * 未配图的类目走 `resolveCategoryFallback` 的首字色块兜底，不渲染空白圆。
 */
import { ref } from 'vue'
import { resolveCategoryFallback } from '@/utils/category-icon'

interface CategoryItem {
  id?: string
  name?: string
  categoryPic?: string
  children?: CategoryItem[]
}

interface Props {
  categories?: CategoryItem[]
  activeIndex?: number
}

const props = withDefaults(defineProps<Props>(), {
  categories: () => [],
  activeIndex: 0,
})

const emit = defineEmits<{
  (e: 'select', index: number): void
  (e: 'expand'): void
}>()

/**
 * 图片加载失败的类目。
 *
 * 存量数据里有已填 `category_pic` 但域名不可达的情况（种子数据指向示例域名），
 * 只判断 `categoryPic` 是否为空会渲染出一个空白圆 —— 因此这里记录加载失败的
 * 索引，失败后同样走首字色块兜底。
 */
const failedImages = ref<Record<number, boolean>>({})

function handleImageError(index: number) {
  failedImages.value[index] = true
}

function showFallback(item: CategoryItem, index: number) {
  return !item.categoryPic || failedImages.value[index] === true
}
</script>

<template>
  <view class="icon-strip">
    <scroll-view class="icon-strip-scroll" scroll-x :show-scrollbar="false">
      <view class="icon-strip-inner">
        <view
          v-for="(item, index) in props.categories"
          :key="item.id ?? index"
          class="icon-strip-item"
          @click="emit('select', index)"
        >
          <view class="icon-strip-circle" :class="index === props.activeIndex ? 'icon-strip-circle--active' : ''">
            <image
              v-if="item.categoryPic && !failedImages[index]"
              class="icon-strip-pic"
              :src="item.categoryPic"
              mode="aspectFill"
              @error="handleImageError(index)"
            />
            <view
              v-if="showFallback(item, index)"
              class="icon-strip-fallback"
              :style="{ backgroundColor: resolveCategoryFallback(item.name, item.id).bgColor }"
            >
              {{ resolveCategoryFallback(item.name, item.id).text }}
            </view>
          </view>
          <view
            class="icon-strip-name"
            :class="index === props.activeIndex ? 'icon-strip-name--active' : ''"
          >
            {{ item.name }}
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="icon-strip-expand" @click="emit('expand')">
      <view class="icon-strip-mask" />
      <view class="icon-strip-expand-btn">
        <view class="icon-strip-expand-text">
          展开
        </view>
        <wd-icon name="arrow-down" size="24rpx" color="#333" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.icon-strip {
  position: relative;
  background: #fff;
  padding: 20rpx 0 16rpx;
}

.icon-strip-scroll {
  width: 100%;
  white-space: nowrap;
}

.icon-strip-inner {
  display: inline-flex;
  padding: 0 120rpx 0 20rpx;
}

.icon-strip-item {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  width: 140rpx;
  flex: none;
}

.icon-strip-circle {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  overflow: hidden;
  box-sizing: border-box;
  border: 4rpx solid transparent;

  &--active {
    border-color: var(--theme-color-primary, var(--wot-color-theme-primary));
  }
}

.icon-strip-pic {
  width: 100%;
  height: 100%;
}

.icon-strip-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 40rpx;
  font-weight: 600;
}

.icon-strip-name {
  margin-top: 10rpx;
  max-width: 132rpx;
  font-size: 24rpx;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &--active {
    color: var(--theme-color-primary, var(--wot-color-theme-primary));
    font-weight: 600;
  }
}

.icon-strip-expand {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
}

.icon-strip-mask {
  width: 40rpx;
  height: 100%;
  background: linear-gradient(to right, rgba(255, 255, 255, 0), #fff);
}

.icon-strip-expand-btn {
  width: 96rpx;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.icon-strip-expand-text {
  font-size: 22rpx;
  color: #333;
}
</style>
