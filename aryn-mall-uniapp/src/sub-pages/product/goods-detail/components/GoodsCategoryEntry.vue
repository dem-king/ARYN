<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage } from '@/api/product/spu'
import { useCategoryLocateStore } from '@/store/categoryLocateStore'

interface Props {
  spuId: string
  categoryFirstId?: string
  categorySecondId?: string
  categoryName?: string
}

const props = withDefaults(defineProps<Props>(), {
  categoryFirstId: '',
  categorySecondId: '',
  categoryName: '',
})

const categoryLocateStore = useCategoryLocateStore()
const products = ref<any[]>([])
const { t } = useI18n()

const visible = computed(() => Boolean(props.categoryFirstId || props.categorySecondId))

/** 二级分类下所有商品共用同一个分类名；只有一级分类时退回通用标题 */
const categoryTitle = computed(() => {
  const source = String(props.categoryName || '').trim()
  if (source)
    return source
  if (!props.categorySecondId)
    return t('product.similarGoods')
  return String(products.value[0]?.categoryName || '').trim() || t('product.similarGoods')
})

/**
 * 取同分类商品首图做一个小拼贴：比纯文字入口更能传达「这里还有东西可逛」。
 * 排除当前商品；接口失败或分类下只有当前商品时退回占位图标，不影响点击跳转。
 */
const thumbs = computed(() => {
  const list: string[] = []
  for (const item of products.value) {
    if (String(item?.id || '') === String(props.spuId || ''))
      continue
    const pic = Array.isArray(item?.spuUrls)
      ? item.spuUrls[0]
      : String(item?.spuUrls || '').split(',')[0]
    if (pic)
      list.push(pic)
    if (list.length >= 2)
      break
  }
  return list
})

watch(
  () => [props.categoryFirstId, props.categorySecondId],
  () => loadCategoryProducts(),
  { immediate: true },
)

async function loadCategoryProducts() {
  if (!visible.value)
    return
  try {
    const params: Record<string, any> = { current: 1, size: 4 }
    if (props.categorySecondId)
      params.categorySecondId = props.categorySecondId
    else
      params.categoryFirstId = props.categoryFirstId
    const response = await getPage(params)
    products.value = response?.records || []
  }
  catch {
    // 缩略图属装饰信息，失败不影响入口本身
    products.value = []
  }
}

function goCategory() {
  if (props.categoryFirstId || props.categorySecondId) {
    categoryLocateStore.setPendingLocate({
      categoryFirstId: props.categoryFirstId || '',
      categorySecondId: props.categorySecondId || '',
    })
  }
  // 压栈打开分类页：商详仍留在返回栈里，左上角返回箭头可以原路回到商详。
  // 分类 tab 是 root 页，switchTab/reLaunch 会清空栈，不能用于这个入口。
  uni.navigateTo({
    url: '/sub-pages/product/category-browse/index',
    fail: () => {
      // 页面栈满等极端情况下退回分类 tab：入口不能失效，代价是没有返回栈
      uni.reLaunch({ url: '/pages/product/category/index' })
    },
  })
}
</script>

<template>
  <view
    v-if="visible"
    class="category-entry bg-white dark:bg-[var(--wot-dark-background2)]"
    hover-class="category-entry--pressed"
    @click="goCategory"
  >
    <view class="category-entry__media">
      <template v-if="thumbs.length">
        <view v-for="(pic, index) in thumbs" :key="index" class="category-entry__thumb">
          <wd-img width="100%" height="100%" mode="aspectFill" radius="14rpx" :src="pic" />
        </view>
      </template>
      <view v-else class="category-entry__thumb category-entry__thumb--placeholder">
        <wd-icon name="goods" size="34rpx" color="#b5bac3" />
      </view>
    </view>
    <view class="category-entry__body">
      <view class="category-entry__title">
        {{ categoryTitle }}
      </view>
      <view class="category-entry__desc">
        {{ t('product.similarGoodsDesc') }}
      </view>
    </view>
    <view class="category-entry__action">
      <text>{{ t('product.browse') }}</text>
      <wd-icon name="arrow-right" size="12px" color="#fff" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.category-entry {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin: 16rpx 24rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  transition: transform 120ms ease, opacity 120ms ease;

  &--pressed {
    opacity: 0.86;
    transform: scale(0.99);
  }

  &__media {
    display: flex;
    flex: none;
    gap: 8rpx;
  }

  &__thumb {
    width: 80rpx;
    height: 80rpx;
    overflow: hidden;
    border-radius: 14rpx;
    background: #f5f6f8;

    &--placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  &__body {
    min-width: 0;
    flex: 1;
  }

  &__title {
    overflow: hidden;
    color: #1f2329;
    font-size: 26rpx;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__desc {
    margin-top: 6rpx;
    color: #8a8f99;
    font-size: 22rpx;
  }

  &__action {
    display: flex;
    flex: none;
    align-items: center;
    gap: 4rpx;
    padding: 12rpx 20rpx;
    border-radius: 999rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    color: #fff;
    font-size: 22rpx;
    font-weight: 500;
  }
}
</style>
