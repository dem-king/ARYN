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
          :class="index === props.activeIndex ? 'icon-strip-item--active' : ''"
          hover-class="icon-strip-item--pressed"
          :hover-stay-time="120"
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
  /* 顶边 4rpx 与 .icon-strip-inner 的 20rpx 相加仍是原 24rpx：拆开是为了给选中项留裁切余量 */
  padding: 4rpx 0 18rpx;
  /* 与下方内容区形成层次分隔，避免纯白平贴 */
  border-bottom: 1rpx solid #f1f2f4;
}

.icon-strip-scroll {
  width: 100%;
  white-space: nowrap;
}

.icon-strip-inner {
  display: inline-flex;
  /**
   * 顶部 20rpx 是给选中项留的裁切余量。scroll-view 会在自身内容盒上沿裁掉溢出内容，
   * 而选中项被 translateY(-2rpx) 上提，圆再叠加弹跳动画的 scale（45% 处峰值 1.1、
   * 结束保持 1.04）与选中描边阴影 5rpx 的扩散：圆顶最多外溢
   * 1px(上提) + 2.4px(峰值放大) + 2.5px(阴影) ≈ 6px，余量不足时表现为顶部被平切。
   * 这份余量从 .icon-strip 的 padding 让出，两者相加仍等于原来的 24rpx：
   * 图标位置与图标条总高都不变，只是滚动容器上沿上移、多出这段不裁切的空间。
   */
  padding: 20rpx 120rpx 0 20rpx;
}

.icon-strip-item {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  width: 140rpx;
  flex: none;
  transition: transform 180ms ease;

  &--active {
    transform: translateY(-2rpx);
  }

  /* 按压反馈（hover-class，小程序与 H5 通用）：未选中项轻压回弹 */
  &--pressed {
    transform: scale(0.92);
  }
}

.icon-strip-circle {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  overflow: hidden;
  box-sizing: border-box;
  border: 3rpx solid transparent;
  background: #f5f6f8;
  box-shadow: 0 5rpx 14rpx rgba(29, 39, 54, 0.08);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;

  &--active {
    border-color: var(--theme-color-primary, var(--wot-color-theme-primary));
    box-shadow: 0 0 0 5rpx rgba(32, 112, 235, 0.1), 0 7rpx 18rpx rgba(29, 39, 54, 0.12);
    /* 选中弹跳：overshoot 曲线给切换一个「落位」的弹性感，动画结束保持 1.04 */
    animation: icon-strip-bounce 480ms cubic-bezier(0.34, 1.56, 0.64, 1) both;
  }
}

@keyframes icon-strip-bounce {
  0% {
    transform: scale(1);
  }

  45% {
    transform: scale(1.1);
  }

  70% {
    transform: scale(0.97);
  }

  100% {
    transform: scale(1.04);
  }
}

.icon-strip-pic {
  width: 100%;
  height: 100%;
  display: block;
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
  margin-top: 12rpx;
  max-width: 132rpx;
  font-size: 23rpx;
  line-height: 32rpx;
  color: #737985;
  transition: color 180ms ease, font-weight 180ms ease;
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
  background: linear-gradient(to right, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.96) 78%);
}

.icon-strip-expand-btn {
  width: 96rpx;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fff;
  transition: opacity 160ms ease, transform 160ms ease;
}

.icon-strip-expand:active .icon-strip-expand-btn {
  opacity: 0.72;
  transform: scale(0.96);
}

.icon-strip-expand-text {
  font-size: 22rpx;
  line-height: 30rpx;
  color: #4b5360;
}

@media (prefers-reduced-motion: reduce) {
  .icon-strip-item,
  .icon-strip-circle,
  .icon-strip-circle--active,
  .icon-strip-expand-btn {
    animation: none;
    transition: none;
  }
}
</style>
