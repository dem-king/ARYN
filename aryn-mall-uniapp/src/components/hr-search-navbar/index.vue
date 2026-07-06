<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

interface Props {
  modelValue: string
  placeholder?: string
  placeholderLeft?: boolean
  bordered?: boolean
  leftArrow?: boolean
  transparent?: boolean
  scrollTop?: number
  navPlaceholder?: boolean
  disabled?: boolean
  focus?: boolean
  bgColor?: string
  fontColor?: string
  primary?: boolean
  gradient?: boolean
  searchBtn?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请输入搜索关键词',
  placeholderLeft: false,
  bordered: true,
  leftArrow: true,
  transparent: false,
  scrollTop: 0,
  navPlaceholder: true,
  disabled: false,
  focus: false,
  bgColor: '#ffffff',
  fontColor: '#000000',
  primary: false,
  gradient: true,
  searchBtn: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
  (e: 'focus'): void
}>()

const router = useRouter()
const searchValue = ref(props.modelValue)
const isCanBack = ref(true)
const capsulePaddingRight = ref(0)
const statusBarHeight = ref(0)
const navBarHeight = ref(44)
const capsuleCenterOffset = ref(0)
const maxScroll = 100
watch(() => props.modelValue, (val) => {
  searchValue.value = val
})

onMounted(() => {
  const pages = getCurrentPages()
  if (pages.length <= 1 || pages[pages.length - 1].route === 'pages/login/index') {
    isCanBack.value = false
  }
  else {
    isCanBack.value = true
  }
  const sys = uni.getSystemInfoSync()
  statusBarHeight.value = sys.statusBarHeight || 0
  // #ifdef MP-WEIXIN
  const rect = uni.getMenuButtonBoundingClientRect()
  const rightSpace = sys.windowWidth - rect.right
  capsulePaddingRight.value = rightSpace + rect.width
  navBarHeight.value = 44
  const barHeight = rect.bottom - rect.top
  const capsuleCenter = rect.top + barHeight / 2
  capsuleCenterOffset.value = capsuleCenter - (statusBarHeight.value + navBarHeight.value / 2)
  // #endif
  // #ifdef MP-ALIPAY
  // 支付宝小程序无右上角胶囊按钮，使用固定右侧间距
  capsulePaddingRight.value = 16
  navBarHeight.value = 44
  capsuleCenterOffset.value = 0
  // #endif
  // #ifdef MP-TOUTIAO
  // 抖音小程序有右上角胶囊按钮
  const ttRect = uni.getMenuButtonBoundingClientRect()
  const ttRightSpace = sys.windowWidth - ttRect.right
  capsulePaddingRight.value = ttRightSpace + ttRect.width
  navBarHeight.value = 44
  const ttBarHeight = ttRect.bottom - ttRect.top
  const ttCapsuleCenter = ttRect.top + ttBarHeight / 2
  capsuleCenterOffset.value = ttCapsuleCenter - (statusBarHeight.value + navBarHeight.value / 2)
  // #endif
  // #ifdef MP-BAIDU
  capsulePaddingRight.value = 16
  navBarHeight.value = 44
  capsuleCenterOffset.value = 0
  // #endif
  // #ifdef H5
  navBarHeight.value = 44
  capsuleCenterOffset.value = 0
  // #endif
})

function updateValue(val: string) {
  searchValue.value = val
  emit('update:modelValue', val)
}
function handleSearch(val: string) {
  emit('search', val)
}
function handleFocus() {
  emit('focus')
}
function handleLeftClick() {
  if (isCanBack.value) {
    router.back()
  }
  else {
    router.pushTab({ name: 'home' })
  }
}

const dynamicStyle = computed(() => {
  let style = ''
  if (props.transparent) {
    const opacity = Math.min(props.scrollTop / maxScroll, 1)
    style += `background-color: rgba(255, 255, 255, ${opacity});`
    style += `color: ${opacity > 0.5 ? '#000' : '#fff'};`
  }
  else if (props.primary && props.gradient) {
    style += 'background: linear-gradient(to right, var(--wot-color-theme-primary), var(--wot-color-theme-secondary));'
    style += 'color: #fff;'
  }
  else if (props.primary) {
    style += 'background-color: var(--wot-color-theme-primary);'
    style += 'color: #fff;'
  }
  else if (props.bgColor) {
    style += `background-color: ${props.bgColor};`
    style += `color: ${props.fontColor || ''};`
  }
  else {
    style += `color: ${props.fontColor || ''};`
  }
  return style
})

const titleColor = computed(() => {
  if (props.transparent) {
    const opacity = Math.min(props.scrollTop / maxScroll, 1)
    return opacity > 0.5 ? '#000000' : '#ffffff'
  }
  if (props.primary)
    return '#ffffff'
  return props.fontColor || '#000000'
})
</script>

<template>
  <view class="fixed left-0 right-0 top-0 z-1000" :style="dynamicStyle">
    <view :style="{ paddingTop: `${statusBarHeight + capsuleCenterOffset}px`, height: `${navBarHeight}px`, paddingRight: `${capsulePaddingRight}px` }" class="flex items-center justify-between px-12rpx">
      <view class="w-68rpx flex items-center justify-center">
        <wd-icon
          v-if="leftArrow"
          :color="titleColor"
          :name="isCanBack ? 'arrow-left' : 'home1'"
          size="22px"
          @click="handleLeftClick"
        />
      </view>

      <view class="mx-10rpx min-w-0 flex-1">
        <view class="h-30px min-w-0 flex items-center border border-primary rounded-18rpx border-solid bg-white/90 py-4rpx pl-16rpx pr-8rpx">
          <text class="i-carbon:search mr-8rpx text-16px text-gray-500" />
          <input
            v-if="!disabled"
            class="flex-1 rounded-16rpx bg-transparent px-8rpx py-4rpx text-28rpx text-[#333]"
            type="text"
            :value="searchValue"
            :placeholder="placeholder"
            :focus="focus"
            confirm-type="search"
            @input="(e:any) => updateValue(e.detail?.value ?? e.target?.value)"
            @confirm="handleSearch(searchValue)"
            @focus="handleFocus"
          >
          <view
            v-else class="min-w-0 flex flex-1"
            @click="handleSearch(searchValue)"
          >
            <view
              class="min-w-0 py-6rpx"
              :class="searchValue ? 'text-[#333]' : 'text-gray-400'"
            >
              <view v-if="searchValue" class="min-w-0 flex items-center rounded-6rpx bg-gray-100 px-20rpx py-4rpx text-26rpx">
                <view class="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap">
                  {{ searchValue }}
                </view>
                <wd-icon name="close" size="20rpx" custom-class="ml-4rpx flex-none" :color="searchValue ? 'text-[#333]' : 'text-gray-400'" />
              </view>
              <view v-else class="text-28rpx text-[#333]">
                {{ placeholder }}
              </view>
            </view>
          </view>

          <view
            v-if="searchBtn"
            class="ml-8rpx flex-none rounded-16rpx bg-primary px-20rpx py-10rpx text-26rpx text-white"
            @click="handleSearch(searchValue)"
          >
            搜索
          </view>
        </view>
      </view>

      <view class="flex items-center">
        <slot name="right" />
      </view>
    </view>
    <view v-if="bordered" class="h-1px bg-[#eaeaea]" />
  </view>
  <view v-if="navPlaceholder" :style="{ height: `${statusBarHeight + navBarHeight}px` }" />
</template>

<style lang="scss" scoped>
</style>
