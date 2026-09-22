<script setup lang="ts">
/**
 * 商品详情底部弹层（参考小象超市分类页：点商品不跳整页，从底部滑出详情面板）。
 *
 * 结构：
 *   大图轮播（可点预览、右下页码）＋ 价格条 ＋ 规格行
 *   ＋ 标题/卖点 ＋ 图文详情（mp-html 富文本）
 *   ＋ 底部固定「加入购物车」
 *
 * 加购统一唤起 vk-data-goods-sku-popup（mode=2，自带数量步进器与库存校验）：
 *   · 未登录 → 本地拦截跳登录（避免 401 把访客踢出当前页）
 *   · SKU 主键字段直接复用 getById 返回的 `id`（详情页同口径，不做字段映射）
 */
// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import { nextTick, ref, watch } from 'vue'

import { addShoppingCart } from '@/api/order/shoppingCart'
import { getById as getSpuById } from '@/api/product/spu'
import { initGoodsSpecs } from '@/utils/goods-specs'

interface Props {
  /** 控制显隐（v-model） */
  modelValue: boolean
  /** 当前商品 SPU ID */
  spuId?: string
}

const props = withDefaults(defineProps<Props>(), {
  spuId: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 加购成功后触发，父级可据此刷新角标 */
  (e: 'added', spuId: string): void
}>()

const authStore = useAuthStore()
const shoppingCartStore = useShoppingCartStore()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const loading = ref(false)
const goodsSpu = ref<any>({})
const imgIndex = ref(0)

const skuPopup = ref()
const skuKey = ref(false)
const skuMounted = ref(false)
const skuGoods = ref<any>({})

/** 图片列表兜底：接口缺图时给个空数组，swiper 不崩 */
const imageList = computed(() => goodsSpu.value?.spuUrls ?? [])

/** 是否有可展示的划线价 */
const showOriginalPrice = computed(
  () => Number(goodsSpu.value?.salesPrice) < Number(goodsSpu.value?.originalPrice),
)

/** 规格摘要：单规格取第一个 sku 的规格值；多规格引导选择 */
const specSummary = computed(() => {
  if (!goodsSpu.value?.id)
    return ''
  if (goodsSpu.value.enableSpecs === '0')
    return '默认'
  const first = goodsSpu.value.goodsSkus?.[0]
  const names = (first?.specsArr ?? []).map((s: any) => s.specsValueName).filter(Boolean)
  return names.length ? names.join(' / ') : '请选择规格'
})

async function loadSpu(id: string) {
  if (!id)
    return
  loading.value = true
  try {
    const response = await getSpuById(id)
    goodsSpu.value = response ?? {}
    if (response)
      initGoodsSpecs(response)
    imgIndex.value = 0
  }
  catch {
    goodsSpu.value = {}
  }
  finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.spuId],
  ([open, id]) => {
    if (open && id)
      loadSpu(id as string)
  },
  { immediate: true },
)

function close() {
  visible.value = false
}

function onImgChange(e: any) {
  imgIndex.value = e.detail.current
}

function previewImage() {
  if (!imageList.value.length)
    return
  uni.previewImage({
    current: imageList.value[imgIndex.value],
    urls: imageList.value,
  })
}

/** 打开 SKU 弹层（选规格 + 数量）。localdata 直接用 getById 返回的 spu，主键字段为 id */
function openSkuPopup() {
  if (!goodsSpu.value.id)
    return
  skuGoods.value = goodsSpu.value
  skuMounted.value = true
  // 弹层由 v-if 新建，需等一帧再打开，否则首次点击不会弹出
  nextTick(() => {
    skuKey.value = true
  })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

/** 加入购物车：统一唤起 SKU 弹层选规格与数量（弹层内置库存校验） */
function handleAddCart() {
  if (!goodsSpu.value.id)
    return
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  openSkuPopup()
}

/** SKU 弹层回传后加购（回传结构含 skuId/quantity/spuId 等，与详情页同一接口口径） */
function handleSkuAdd(data: any) {
  addShoppingCart(data).then(() => {
    skuKey.value = false
    uni.showToast({ title: '已加入购物车', icon: 'success' })
    // 角标随 store 响应式刷新，tabBar 购物车数量实时更新
    shoppingCartStore.fetchCartCount().catch(() => {})
    emit('added', goodsSpu.value.id)
  })
}
</script>

<template>
  <!--
    弹层 z-index 提到 1000：wd-popup 默认 10，会被 H5 原生 tabBar(998) 盖住，
    底部「加入购物车」按钮点不到；与 category-all-sheet 同一层级策略。
  -->
  <wd-popup
    v-model="visible"
    position="bottom"
    :safe-area-inset-bottom="true"
    :z-index="1000"
    custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
  >
    <view class="detail-sheet">
      <!-- 大图区 -->
      <view class="detail-sheet-gallery">
        <swiper
          v-if="imageList.length"
          class="detail-sheet-swiper"
          :current="imgIndex"
          :indicator-dots="false"
          @change="onImgChange"
          @tap="previewImage"
        >
          <swiper-item v-for="(url, i) in imageList" :key="i">
            <image class="detail-sheet-img" :src="url" mode="aspectFill" />
          </swiper-item>
        </swiper>
        <view v-else class="detail-sheet-swiper detail-sheet-swiper--empty" />

        <view class="detail-sheet-close" @click="close">
          <wd-icon name="arrow-down" size="40rpx" color="#fff" />
        </view>
        <view v-if="imageList.length > 1" class="detail-sheet-index">
          {{ imgIndex + 1 }}/{{ imageList.length }}
        </view>
      </view>

      <scroll-view class="detail-sheet-body" scroll-y>
        <!-- 价格条 -->
        <view class="detail-sheet-price">
          <view class="detail-sheet-price-main">
            <text class="detail-sheet-symbol">¥</text>
            <text class="detail-sheet-value">{{ goodsSpu.salesPrice }}</text>
          </view>
          <text
            v-if="showOriginalPrice"
            class="detail-sheet-original"
          >
            ¥{{ goodsSpu.originalPrice }}
          </text>
        </view>

        <!-- 规格行 -->
        <view class="detail-sheet-spec" @click="openSkuPopup">
          <text class="detail-sheet-spec-text">{{ specSummary || '规格' }}</text>
          <wd-icon name="arrow-right" size="24rpx" color="#999" />
        </view>

        <!-- 标题与卖点 -->
        <view class="detail-sheet-title">
          {{ goodsSpu.name }}
        </view>
        <view v-if="goodsSpu.introduction" class="detail-sheet-intro">
          {{ goodsSpu.introduction }}
        </view>

        <!-- 图文详情（完整商品详情不再跳新页面，直接在弹层内阅读） -->
        <wd-divider class="detail-sheet-divider">
          商品详情
        </wd-divider>
        <view class="detail-sheet-rich">
          <mp-html :content="goodsSpu.description" />
        </view>
      </scroll-view>

      <!-- 底部操作栏：SKU 弹层弹出时隐藏，避免两层「加入购物车」按钮叠在一起 -->
      <view v-if="!skuKey" class="detail-sheet-footer">
        <view class="detail-sheet-add" @click="handleAddCart">
          <wd-icon name="plus" size="32rpx" color="#fff" />
          加入购物车
        </view>
      </view>

      <vk-data-goods-sku-popup
        v-if="skuMounted"
        ref="skuPopup"
        v-model="skuKey"
        border-radius="20"
        :localdata="skuGoods"
        sku-arr-name="specsArr"
        sku-list-name="goodsSkus"
        spec-list-name="specList"
        :mode="2"
        @add-cart="handleSkuAdd"
      />
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.detail-sheet {
  display: flex;
  flex-direction: column;
  /* 面板接近全屏，只在顶部露出下拉关闭钮与少量压暗背景 */
  height: 92vh;
  background: #fff;
}

.detail-sheet-gallery {
  position: relative;
  flex: none;
  height: 640rpx;
  background: #f5f5f5;
}

.detail-sheet-swiper {
  width: 100%;
  height: 100%;
}

.detail-sheet-swiper--empty {
  display: block;
}

.detail-sheet-img {
  width: 100%;
  height: 100%;
}

.detail-sheet-close {
  position: absolute;
  top: 24rpx;
  left: 24rpx;
  z-index: 2;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
}

.detail-sheet-index {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  z-index: 2;
  padding: 4rpx 16rpx;
  border-radius: 24rpx;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 22rpx;
}

.detail-sheet-body {
  flex: 1;
  min-height: 0;
}

.detail-sheet-price {
  display: flex;
  align-items: baseline;
  padding: 24rpx 32rpx 0;
  background: linear-gradient(180deg, #fff2f2 0%, #ffffff 100%);
}

.detail-sheet-price-main {
  color: #ff2237;
  font-weight: 700;
}

.detail-sheet-symbol {
  font-size: 26rpx;
}

.detail-sheet-value {
  font-size: 48rpx;
}

.detail-sheet-original {
  margin-left: 16rpx;
  color: #999;
  font-size: 24rpx;
  text-decoration: line-through;
}

.detail-sheet-spec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20rpx 32rpx 0;
  padding: 16rpx 20rpx;
  background: #f7f7f7;
  border-radius: 12rpx;
}

.detail-sheet-spec-text {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-sheet-title {
  padding: 20rpx 32rpx 0;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.4;
  color: #222;
}

.detail-sheet-intro {
  padding: 12rpx 32rpx 0;
  font-size: 26rpx;
  line-height: 1.5;
  color: #888;
}

.detail-sheet-divider {
  margin: 24rpx 0 0;
  padding: 0 32rpx;
  color: #333;
  font-weight: 600;
}

.detail-sheet-rich {
  padding: 16rpx 32rpx 32rpx;
}

.detail-sheet-footer {
  flex: none;
  display: flex;
  padding: 16rpx 32rpx;
  border-top: 1rpx solid #f2f2f2;
}

.detail-sheet-add {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border-radius: 44rpx;
  background: var(--theme-color-primary, var(--wot-color-theme-primary));
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
