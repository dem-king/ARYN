<script setup lang="ts">
/**
 * 装修用搜索栏。
 *
 * 运营可配：占位文案、圆角风格、是否显示扫码。
 *
 * 热词轮播（B 版原型「SKU 编码 · 品名 · 船上补给」）：
 *   运营可在后台配多个热词，未配置时退回占位文案。轮播只在配置了
 *   2 个及以上热词时启动，避免单个热词白白起一个定时器。
 *
 * 消息角标：显示未读总数，未登录或未读为 0 时不渲染。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useDiyStyle } from '@/composables/useDiyStyle'
import { useAuthStore } from '@/store/authStore'
import { useMallThemeStore } from '@/store/mallThemeStore'
import { useMessageStore } from '@/store/messageStore'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const authStore = useAuthStore()
const messageStore = useMessageStore()
/** 全站自动动效总开关（商城主题配置）；关闭时热词不轮换，固定展示首个 */
const mallThemeStore = useMallThemeStore()

const placeholder = computed(() => props.showData.placeholder || '搜索商品')
const style = computed(() => props.showData.style || '1')
const bgColor = computed(() => props.showData.bgColor || '#f5f5f5')
const showScan = computed(() => props.showData.showScan !== false)

const borderRadius = computed(() => (style.value === '1' ? '20px' : '4px'))

/** 通用样式（外边距/内边距/背景/圆角）挂根节点，与设置面板「通用样式」同源 */
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

/** 热词列表：兼容后台给字符串数组或逗号分隔字符串两种写法 */
const hotWords = computed<string[]>(() => {
  const raw = props.showData.hotWords
  if (Array.isArray(raw))
    return raw.map(word => String(word).trim()).filter(Boolean)
  if (typeof raw === 'string')
    return raw.split(/[,，]/).map(word => word.trim()).filter(Boolean)
  return []
})

const hotIndex = ref(0)
let hotTimer: ReturnType<typeof setInterval> | null = null

/** 当前展示文案：有热词时轮播，否则用占位文案 */
const displayText = computed(() => {
  if (hotWords.value.length === 0)
    return placeholder.value
  return hotWords.value[hotIndex.value % hotWords.value.length]
})

const unreadCount = computed(() => (authStore.isLoggedIn ? messageStore.totalUnread : 0))
const unreadText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

function handleSearch() {
  uni.navigateTo({ url: '/sub-pages/product/goods-search/index' })
}

function goMessage() {
  uni.navigateTo({ url: '/sub-pages/message/notice/index' })
}

onMounted(() => {
  startRotation()
})

// 总开关可能异步到达（主题拉取晚于挂载）：到达后再决定启停
watch([() => mallThemeStore.autoMotionEnabled, hotWords], () => {
  startRotation()
})

onBeforeUnmount(() => {
  stopRotation()
})

/** 启停热词轮换：需同时满足「配置了 2 个及以上热词」与「全站允许自动动效」 */
function startRotation() {
  stopRotation()
  if (hotWords.value.length < 2)
    return
  if (!mallThemeStore.autoMotionEnabled)
    return
  hotTimer = setInterval(() => {
    hotIndex.value = (hotIndex.value + 1) % hotWords.value.length
  }, 3000)
}

function stopRotation() {
  if (hotTimer) {
    clearInterval(hotTimer)
    hotTimer = null
  }
}
</script>

<template>
  <view class="search-bar" :style="dynamicStyles">
    <view class="search-bar-row">
      <view
        class="search-bar-inner"
        :style="{ backgroundColor: bgColor, borderRadius }"
        @click="handleSearch"
      >
        <wd-icon name="search" size="16px" color="#999" />
        <text class="search-bar-placeholder">
          {{ displayText }}
        </text>
      </view>

      <!-- 消息角标：未读为 0 或未登录时不渲染 -->
      <view class="search-bar-bell" @click.stop="goMessage">
        <wd-icon name="notification" size="22px" color="#333" />
        <view v-if="unreadCount > 0" class="search-bar-bell-dot">
          {{ unreadText }}
        </view>
      </view>

      <view v-if="showScan" class="search-bar-scan" @click.stop="handleSearch">
        <wd-icon name="scan" size="22px" color="#333" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.search-bar {
  /* 留白挂内层行容器：根节点 padding 会被通用样式的内联零值覆盖（仓库统一口径） */
  .search-bar-row {
    display: flex;
    align-items: center;
    padding: 6px 12px;
  }

  .search-bar-inner {
    flex: 1;
    display: flex;
    align-items: center;
    height: 36px;
    padding: 0 12px;

    .search-bar-placeholder {
      margin-left: 6px;
      overflow: hidden;
      font-size: 14px;
      color: #999;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .search-bar-bell {
    position: relative;
    display: flex;
    margin-left: 10px;
    align-items: center;
  }

  .search-bar-bell-dot {
    position: absolute;
    top: -6px;
    right: -10px;
    box-sizing: border-box;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: 8px;
    background: var(--wot-color-theme-primary, #ff2237);
    color: #fff;
    font-size: 10px;
    line-height: 16px;
    text-align: center;
  }

  .search-bar-scan {
    margin-left: 10px;
    display: flex;
    align-items: center;
  }
}
</style>
