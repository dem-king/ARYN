<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

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
  placeholder: '',
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
const { t } = useI18n()
const searchValue = ref(props.modelValue)
/** 未传 placeholder 时回落到与当前语言一致的默认文案 */
const placeholderText = computed(() => props.placeholder || t('search.inputPlaceholder'))
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
  const sys = uni.getWindowInfo()
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
  // #ifndef MP-WEIXIN
  navBarHeight.value = 44
  capsuleCenterOffset.value = 0
  // #endif
})

/**
 * 导航栏占位高度（状态栏 + 导航栏）。
 *
 * 对外暴露，供「需要从导航栏下沿开始渲染」的浮层使用（如分类页的全部分类面板）：
 * 让浮层直接读取这个值，避免各页面再按平台手算导航栏高度而漂移。
 */
const placeholderHeight = computed(() => statusBarHeight.value + navBarHeight.value)

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
function handleDisabledClick() {
  if (props.disabled)
    handleSearch(searchValue.value)
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

defineExpose({ placeholderHeight })
</script>

<template>
  <view class="fixed left-0 right-0 top-0 z-1000" :style="dynamicStyle">
    <view :style="{ paddingTop: `${statusBarHeight + capsuleCenterOffset}px`, height: `${navBarHeight}px`, paddingRight: `${capsulePaddingRight}px` }" class="flex items-center justify-between px-12rpx">
      <view v-if="leftArrow" class="w-68rpx flex items-center justify-center">
        <wd-icon
          :color="titleColor"
          :name="isCanBack ? 'arrow-left' : 'home1'"
          size="22px"
          @click="handleLeftClick"
        />
      </view>

      <view class="mx-10rpx min-w-0 flex-1">
        <view
          class="box-border h-36px min-w-0 flex items-center border border-[#ececf0] rounded-20rpx border-solid bg-[#f5f6f8] py-4rpx pl-20rpx pr-8rpx"
          @click="handleDisabledClick"
        >
          <text class="i-carbon:search mr-8rpx text-16px text-gray-500" />
          <input
            v-if="!disabled"
            class="flex-1 rounded-16rpx bg-transparent px-8rpx py-4rpx text-28rpx text-[#333]"
            type="text"
            :value="searchValue"
            :placeholder="placeholderText"
            :focus="focus"
            confirm-type="search"
            @input="(e:any) => updateValue(e.detail?.value ?? e.target?.value)"
            @confirm="handleSearch(searchValue)"
            @focus="handleFocus"
          >
          <view v-else class="min-w-0 flex flex-1">
            <view
              class="min-w-0 py-6rpx"
              :class="searchValue ? 'text-[#333]' : 'text-gray-400'"
            >
              <view v-if="searchValue" class="min-w-0 flex items-center rounded-6rpx bg-gray-100 px-20rpx py-4rpx text-26rpx">
                <view class="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap">
                  {{ searchValue }}
                </view>
                <wd-icon name="close" size="20rpx" custom-class="ml-4rpx flex-none" color="#6b7280" />
              </view>
              <view v-else class="text-28rpx">
                {{ placeholderText }}
              </view>
            </view>
          </view>

          <view
            v-if="searchBtn"
            class="ml-8rpx h-28px flex flex-none items-center rounded-14rpx bg-primary px-24rpx text-26rpx text-white"
            @click.stop="handleSearch(searchValue)"
          >
            {{ t('search.searchButton') }}
          </view>
        </view>
      </view>

      <view class="flex items-center">
        <slot name="right" />
      </view>
    </view>
    <view v-if="bordered" class="h-1px bg-[#eaeaea]" />
  </view>
  <view v-if="navPlaceholder" :style="{ height: `${placeholderHeight}px` }" />
</template>

<style lang="scss" scoped>
</style>
