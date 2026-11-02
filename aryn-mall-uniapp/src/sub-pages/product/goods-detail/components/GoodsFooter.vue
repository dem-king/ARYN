<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatCartBadgeText } from '@/utils/cart-badge'

interface Props {
  shoppingCartCount: number
}

interface Emits {
  (e: 'toHome'): void
  (e: 'toCart'): void
  (e: 'customerService'): void
  (e: 'openSkuPopup', value: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const { t } = useI18n()

const cartBadge = computed(() => formatCartBadgeText(props.shoppingCartCount))
const useNativeContact
  = import.meta.env.VITE_CUSTOMER_SERVICE_MODE === 'NATIVE'

function toHome() {
  emit('toHome')
}

function toCart() {
  emit('toCart')
}

function openSkuPopup(value: number) {
  emit('openSkuPopup', value)
}

function customerService() {
  if (!useNativeContact)
    emit('customerService')
}
</script>

<template>
  <view class="goods-footer">
    <view
      class="goods-footer__entry"
      hover-class="goods-footer__entry--pressed"
      :hover-stay-time="80"
      @click="toHome"
    >
      <view class="goods-footer__icon-box">
        <wd-icon name="home" size="26px" custom-style="line-height: 1;" />
      </view>
      <text class="goods-footer__label">
        {{ t('product.home') }}
      </text>
    </view>
    <button
      class="goods-footer__entry"
      :open-type="useNativeContact ? 'contact' : undefined"
      @click="customerService"
    >
      <view class="goods-footer__icon-box">
        <wd-icon name="service" size="26px" custom-style="line-height: 1;" />
      </view>
      <text class="goods-footer__label">
        {{ t('product.support') }}
      </text>
    </button>
    <view
      class="goods-footer__entry"
      hover-class="goods-footer__entry--pressed"
      :hover-stay-time="80"
      @click="toCart"
    >
      <view class="goods-footer__icon-box">
        <wd-icon name="cart" size="26px" custom-style="line-height: 1;" />
        <text v-if="cartBadge" class="goods-footer__badge">
          {{ cartBadge }}
        </text>
      </view>
      <text class="goods-footer__label">
        {{ t('product.cart') }}
      </text>
    </view>
    <view
      class="goods-footer__action"
      hover-class="goods-footer__action--pressed"
      @click="openSkuPopup(2)"
    >
      {{ t('product.addToCart') }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
/**
 * 与悬浮玻璃底栏同一套材质与几何：高 52px、圆角 26px、贴底 16px、
 * 左右各留 20px、白 86% + blur 18px saturate 160%。
 * 本页没有 tabBar，这条操作栏就是用户在这一页看到的「底部导航」，
 * 尺寸与材质必须和 tab 页底栏一致，切页时底栏位置不能跳。
 * 层级 9：低于本页的领券弹层（wd-action-sheet，默认 10）与 SKU 弹窗（990）。
 */
.goods-footer {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 40px);
  max-width: 480px;
  bottom: 16px;
  z-index: 9;
  display: flex;
  align-items: center;
  height: 52px;
  box-sizing: border-box;
  padding: 0 10px;
  overflow: hidden;
  border-radius: 26px;
  /* 与顶栏磨砂白同一数值（86%），上下两块玻璃看起来才是一套材质 */
  background-color: rgba(255, 255, 255, 0.86);
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
}

.goods-footer__entry {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 46px;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  /* 未选中图标与原生 tabBar 的灰色图标同色（#979797），文案另用 #7a7e83 */
  color: var(--app-text-3);
  line-height: normal;

  &::after {
    border: none;
  }

  &--pressed {
    opacity: 0.72;
  }
}

.goods-footer__icon-box {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-bottom: 3px;
}

/* 角标与原生 tabBar / glass-tabbar 同款：主题色偏浅时白字会看不清，不跟随主题 */
.goods-footer__badge {
  position: absolute;
  top: -4px;
  right: -10px;
  box-sizing: border-box;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 8px;
  background-color: #ff2237;
  color: #fff;
  font-size: 9px;
  line-height: 15px;
  text-align: center;
}

.goods-footer__label {
  font-size: 11px;
  line-height: 15px;
  color: var(--app-text-2);
}

.goods-footer__action {
  flex: 1;
  min-width: 0;
  height: 36px;
  margin-left: 6px;
  border-radius: 18px;
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  line-height: 36px;
  text-align: center;
  transition: transform 120ms ease, opacity 120ms ease;

  &--pressed {
    opacity: 0.88;
    transform: scale(0.99);
  }
}

/* 深色模式：与 glass-tabbar 同源（wot-theme-dark 挂在根节点） */
.wot-theme-dark .goods-footer {
  background-color: rgba(26, 26, 26, 0.86);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.46);
}

.wot-theme-dark .goods-footer__label {
  color: #a8adb5;
}
</style>
