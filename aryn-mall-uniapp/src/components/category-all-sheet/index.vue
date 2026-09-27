<script setup lang="ts">
/**
 * 「全部分类」浮层（参考图 2）。
 *
 * 一级分类按 5 列宫格铺开，选中项名称使用主题色胶囊底；底部「点击收起」关闭。
 *
 * 几何对齐参考图（该图的像素测量见需求包「实施结果」）：
 *   · **顶边落在导航栏之下**，搜索框保持纯白可点。参考图里搜索框是 (255,255,255)、
 *     购物车角标是满饱和红，说明导航栏渲染在遮罩之上，遮罩并没有盖住它。
 *   · **高度自适应内容**：分类少时面板随宫格收窄，「点击收起」紧贴最后一行；
 *     分类多时被上限封顶（底边最多落在约 86.5% 屏高处），宫格内部滚动，
 *     下方始终保留遮罩带露出压暗的页面内容。
 *   · 底角带圆角，形成「自上滑出的卡片」观感。
 *
 * 层级：遮罩 999 —— 高于 H5 tabBar(998) 与 SKU 弹层(990)，低于导航栏(1000)。
 * 因此导航栏保持点亮、其余页面内容被压暗，与参考图一致。
 *
 * 组件由父级用 `v-if` 懒挂载：分类页不打开浮层时不产生任何节点。
 */
import { computed, ref } from 'vue'
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
  /**
   * 面板顶边偏移（px）：由父级传入导航栏实际占位高度（状态栏 + 导航栏），
   * 保证面板恰好从搜索框下沿开始，不遮挡它。
   */
  topOffset?: number
}

const props = withDefaults(defineProps<Props>(), {
  categories: () => [],
  activeIndex: 0,
  topOffset: 0,
})

const emit = defineEmits<{
  (e: 'select', index: number): void
  (e: 'close'): void
}>()

/** 图片加载失败的类目：已填图但域名不可达时同样走首字色块，避免空白圆 */
const failedImages = ref<Record<number, boolean>>({})

function handleImageError(index: number) {
  failedImages.value[index] = true
}

function showFallback(item: CategoryItem, index: number) {
  return !item.categoryPic || failedImages.value[index] === true
}

const sheetStyle = computed(() => ({
  top: `${props.topOffset}px`,
  /**
   * 面板高度自适应内容；上限使底边最多落在 86.5% 屏高处
   * （绝对定位元素的百分比参照 fixed 全屏根节点），分类再多也不铺满全屏。
   */
  maxHeight: `calc(86.5% - ${props.topOffset}px)`,
}))

/** 打开时把选中项滚入可视区；用 scroll-into-view 而非 DOM API，小程序同样可用 */
const activeAnchor = computed(() => `all-sheet-anchor-${props.activeIndex}`)
</script>

<template>
  <view class="all-sheet-root">
    <view class="all-sheet-mask" @click="emit('close')" />

    <view class="all-sheet" :style="sheetStyle">
      <view class="all-sheet-header">
        <view class="all-sheet-title">
          全部分类
        </view>
        <view class="all-sheet-close" @click="emit('close')">
          <wd-icon name="close" size="32rpx" color="#666" />
        </view>
      </view>

      <scroll-view class="all-sheet-body" scroll-y :scroll-into-view="activeAnchor" scroll-with-animation>
        <view class="all-sheet-grid">
          <view
            v-for="(item, index) in props.categories"
            :id="`all-sheet-anchor-${index}`"
            :key="item.id ?? index"
            class="all-sheet-item"
            hover-class="all-sheet-item--pressed"
            :hover-stay-time="120"
            @click="emit('select', index)"
          >
            <view
              class="all-sheet-circle"
              :class="index === props.activeIndex ? 'all-sheet-circle--active' : ''"
            >
              <image
                v-if="item.categoryPic && !failedImages[index]"
                class="all-sheet-pic"
                :src="item.categoryPic"
                mode="aspectFill"
                @error="handleImageError(index)"
              />
              <view
                v-if="showFallback(item, index)"
                class="all-sheet-fallback"
                :style="{ backgroundColor: resolveCategoryFallback(item.name, item.id).bgColor }"
              >
                {{ resolveCategoryFallback(item.name, item.id).text }}
              </view>
            </view>
            <view
              class="all-sheet-name"
              :class="index === props.activeIndex ? 'all-sheet-name--active' : ''"
            >
              {{ item.name }}
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="all-sheet-footer" @click="emit('close')">
        点击收起
        <wd-icon name="arrow-up" size="26rpx" color="#666" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.all-sheet-root {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  /**
   * 高于 H5 tabBar(998)/SKU 弹层(990)，低于搜索导航栏(1000)。
   * 导航栏因此保持点亮可点，其余页面内容被遮罩压暗 —— 与参考图一致。
   */
  z-index: 999;
}

.all-sheet-mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(16, 24, 40, 0.52);
  animation: all-sheet-mask-in 220ms ease-out both;
}

.all-sheet {
  position: absolute;
  right: 0;
  left: 0;
  /* 高度自适应内容；上限由内联 maxHeight 封顶（底边最多 86.5% 屏高），分类多时宫格内滚动 */
  display: flex;
  flex-direction: column;
  background: #fff;
  border-bottom-left-radius: 28rpx;
  border-bottom-right-radius: 28rpx;
  box-shadow: 0 16rpx 48rpx rgba(20, 29, 45, 0.14);
  overflow: hidden;
  transform-origin: top center;
  animation: all-sheet-panel-in 260ms cubic-bezier(0.2, 0.75, 0.25, 1) both;
}

.all-sheet-header {
  position: relative;
  flex: none;
  padding: 28rpx 32rpx 20rpx;
  border-bottom: 1rpx solid #f4f5f7;
}

.all-sheet-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #252a34;
  letter-spacing: 1rpx;
}

.all-sheet-close {
  position: absolute;
  top: 20rpx;
  right: 24rpx;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.all-sheet-body {
  /**
   * 内容驱动高度：分类少时高度就等于宫格实际高度，面板随之收窄、「点击收起」紧贴最后一行。
   * 内容超过面板 maxHeight 上限时，本节点是唯一可收缩项（header/footer 均 flex:none），
   * 收缩出的空间交给内部滚动。
   * 不用 flex:1 —— flex-grow 在 auto 高度容器里依赖引擎实现，小程序端会出现面板不收缩
   * 或主体被压扁的差异。
   */
  flex: 0 1 auto;
  min-height: 0;
}

.all-sheet-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 0 12rpx 12rpx;
}

.all-sheet-item {
  width: 20%;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 行距对齐参考图实测值（约 167rpx/行）：padding 偏大时最后一行会被面板裁切 */
  padding: 6rpx 0 6rpx;
}

.all-sheet-circle {
  width: 100rpx;
  height: 100rpx;
  border: 1rpx solid rgba(26, 36, 52, 0.05);
  border-radius: 50%;
  box-shadow: 0 6rpx 16rpx rgba(26, 36, 52, 0.08);
  overflow: hidden;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;

  &--active {
    border-color: var(--theme-color-primary, var(--wot-color-theme-primary));
    box-shadow: 0 0 0 4rpx rgba(32, 112, 235, 0.1), 0 6rpx 16rpx rgba(26, 36, 52, 0.12);
  }
}

.all-sheet-item:active .all-sheet-circle {
  transform: scale(0.9);
}

/* 小程序端 :active 不生效，用 hover-class 提供同等按压反馈 */
.all-sheet-item--pressed .all-sheet-circle {
  transform: scale(0.9);
}

.all-sheet-pic {
  width: 100%;
  height: 100%;
}

.all-sheet-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 42rpx;
  font-weight: 600;
}

.all-sheet-name {
  margin-top: 4rpx;
  max-width: 136rpx;
  padding: 4rpx 12rpx;
  font-size: 24rpx;
  line-height: 1.3;
  color: #4b5260;
  text-align: center;
  border-radius: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &--active {
    background: var(--theme-color-primary, var(--wot-color-theme-primary));
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.12);
    color: #fff;
    font-weight: 600;
  }
}

.all-sheet-footer {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-top: 1rpx solid #f0f1f3;
  background: #fff;
  font-size: 28rpx;
  color: #687080;
  transition: background-color 160ms ease;

  &:active {
    background: #f7f8fa;
  }
}

@keyframes all-sheet-mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes all-sheet-panel-in {
  from { opacity: 0; transform: translateY(-18rpx) scale(0.985); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .all-sheet-mask,
  .all-sheet,
  .all-sheet-circle,
  .all-sheet-footer {
    animation: none;
    animation-duration: 1ms;
    transition: none;
  }
}
</style>
