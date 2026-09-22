<script setup lang="ts">
/**
 * 「全部分类」浮层（参考图 2）。
 *
 * 一级分类按 5 列宫格铺开，选中项名称使用主题色胶囊底；底部「点击收起」关闭。
 *
 * 几何对齐参考图（该图的像素测量见需求包「实施结果」）：
 *   · **顶边落在导航栏之下**，搜索框保持纯白可点。参考图里搜索框是 (255,255,255)、
 *     购物车角标是满饱和红，说明导航栏渲染在遮罩之上，遮罩并没有盖住它。
 *   · **底边不贴屏幕底**，下方保留约 12% 屏高的遮罩带，露出压暗的页面内容
 *     （参考图面板底边在 832/953 ≈ 87.3% 屏高处）。
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

const sheetStyle = computed(() => ({ top: `${props.topOffset}px` }))

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
            @click="emit('select', index)"
          >
            <view class="all-sheet-circle">
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
  background: rgba(0, 0, 0, 0.6);
}

.all-sheet {
  position: absolute;
  right: 0;
  left: 0;
  /* 不贴屏幕底：参考图面板底边落在 86.5% 屏高处，下方留出压暗的页面内容 */
  bottom: 13.5%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-bottom-left-radius: 24rpx;
  border-bottom-right-radius: 24rpx;
  overflow: hidden;
}

.all-sheet-header {
  position: relative;
  flex: none;
  padding: 24rpx 32rpx 16rpx;
}

.all-sheet-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
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
  flex: 1;
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
  border-radius: 50%;
  overflow: hidden;
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
  color: #333;
  text-align: center;
  border-radius: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &--active {
    background: var(--theme-color-primary, var(--wot-color-theme-primary));
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
  border-top: 1rpx solid #f2f2f2;
  font-size: 28rpx;
  color: #666;
}
</style>
