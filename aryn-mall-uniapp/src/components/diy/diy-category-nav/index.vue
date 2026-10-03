<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { getActiveTree } from '@/api/product/category'
import { followDecorationLink } from '@/components/diy/link-resolver'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { resolveCategoryFallback } from '@/utils/category-icon'

/**
 * 金刚区（分类导航）。
 *
 * · source = 'static'：沿用装修配置的 navList（图标/文字/链接全部手工维护）。
 * · source = 'category'：实时拉取启用中的一级分类自动渲染——分类改名/新增/停用
 *   即时跟随，无需重新发布装修页。未配图的分类走首字色块兜底（与分类页
 *   category-icon-strip 同一套 resolveCategoryFallback，色板按 id 稳定哈希）。
 */
const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const showNum = computed(() => props.showData.showNum || 4)
const imgSize = computed(() => props.showData.imgSize || 25)
const imgRadius = computed(() => props.showData.imgRadius || 0)
const fontColor = computed(() => props.showData.fontColor || '#333')
const scrollShow = computed(() => props.showData.scrollShow === true)
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

const itemWidth = computed(() => `${100 / showNum.value}%`)

const isDynamic = computed(() => props.showData.source === 'category')

interface CategoryNavEntry {
  id: string
  text: string
  url: string
}

const dynamicEntries = ref<CategoryNavEntry[]>([])

/** 未配图但 URL 不可达的分类（种子数据指向示例域名的情况），失败后同样走色块兜底 */
const failedImages = ref<Record<number, boolean>>({})

function handleImageError(index: number) {
  failedImages.value[index] = true
}

watch(
  () => [props.showData.source, Number(props.showData.categoryMax) || 8] as const,
  async ([source, max], _previous, onCleanup) => {
    if (source !== 'category') {
      dynamicEntries.value = []
      return
    }
    let active = true
    onCleanup(() => {
      active = false
    })
    try {
      const tree = (await getActiveTree()) ?? []
      if (!active)
        return
      failedImages.value = {}
      dynamicEntries.value = tree
        .sort(
          (a: any, b: any) => (Number(a.sort) || 0) - (Number(b.sort) || 0),
        )
        .slice(0, max)
        .map((node: any) => ({
          id: String(node.id ?? ''),
          text: node.name,
          url: node.categoryPic || '',
        }))
    }
    catch {
      // 导航属辅助入口，拉取失败静默降级为空，不阻塞首屏其它楼层
      if (active)
        dynamicEntries.value = []
    }
  },
  { immediate: true },
)

const displayList = computed<any[]>(() => {
  if (!isDynamic.value)
    return props.showData.navList || []
  return dynamicEntries.value.map(entry => ({
    ...entry,
    // 与后台分类树选择器产出的链接同构：按明确层级跳，goods-list 直接查询
    link: {
      params: { categoryFirstId: entry.id },
      path: '',
      targetId: entry.id,
      type: 'category',
    },
  }))
})

/** 动态模式分类为空时整体隐藏（静态模式保持原行为） */
const visible = computed(() => !isDynamic.value || displayList.value.length > 0)

function showFallback(item: any, index: number) {
  return isDynamic.value
    && (!item.url || failedImages.value[index] === true)
}

const fallbackFontSize = computed(() =>
  Math.max(10, Math.round(imgSize.value * 0.45)),
)

function handleClick(item: any) {
  followDecorationLink(item.link || item.linkUrl)
}
</script>

<template>
  <view v-if="visible" class="category-nav" :style="dynamicStyles">
    <scroll-view v-if="scrollShow" scroll-x class="category-nav-scroll">
      <view class="category-nav-grid">
        <view
          v-for="(item, index) in displayList"
          :key="item.id || index"
          class="category-nav-item"
          :style="{ width: itemWidth }"
          @click="handleClick(item)"
        >
          <view
            v-if="showFallback(item, index)"
            class="category-nav-fallback"
            :style="{
              width: `${imgSize}px`,
              height: `${imgSize}px`,
              borderRadius: `${imgRadius}px`,
              backgroundColor: resolveCategoryFallback(item.text, item.id).bgColor,
              fontSize: `${fallbackFontSize}px`,
            }"
          >
            {{ resolveCategoryFallback(item.text, item.id).text }}
          </view>
          <image
            v-else-if="item.url"
            :src="resolveImageSrc(item.url)"
            mode="aspectFit"
            :style="{
              width: `${imgSize}px`,
              height: `${imgSize}px`,
              borderRadius: `${imgRadius}px`,
            }"
            @error="handleImageError(index)"
          />
          <text class="category-nav-text" :style="{ color: fontColor }">
            {{ item.text }}
          </text>
        </view>
      </view>
    </scroll-view>
    <view v-else class="category-nav-grid">
      <view
        v-for="(item, index) in displayList"
        :key="item.id || index"
        class="category-nav-item"
        :style="{ width: itemWidth }"
        @click="handleClick(item)"
      >
        <view
          v-if="showFallback(item, index)"
          class="category-nav-fallback"
          :style="{
            width: `${imgSize}px`,
            height: `${imgSize}px`,
            borderRadius: `${imgRadius}px`,
            backgroundColor: resolveCategoryFallback(item.text, item.id).bgColor,
            fontSize: `${fallbackFontSize}px`,
          }"
        >
          {{ resolveCategoryFallback(item.text, item.id).text }}
        </view>
        <image
          v-else-if="item.url"
          :src="resolveImageSrc(item.url)"
          mode="aspectFit"
          :style="{
            width: `${imgSize}px`,
            height: `${imgSize}px`,
            borderRadius: `${imgRadius}px`,
          }"
          @error="handleImageError(index)"
        />
        <text class="category-nav-text" :style="{ color: fontColor }">
          {{ item.text }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.category-nav {
  padding: 12px 0;

  .category-nav-scroll {
    white-space: nowrap;
  }

  .category-nav-grid {
    display: flex;
    flex-wrap: wrap;

    .category-nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 8px 0;

      .category-nav-fallback {
        display: flex;
        align-items: center;
        justify-content: center;
        overflow: hidden;
        font-weight: 600;
        line-height: 1;
        color: #fff;
      }

      .category-nav-text {
        margin-top: 6px;
        font-size: 12px;
        color: #333;
        text-align: center;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 100%;
      }
    }
  }
}
</style>
