<script setup lang="ts">
/**
 * 快捷加购按钮。
 *
 * 商品卡片上的加购入口：单规格商品一键按合法数量加购，多规格商品自动唤起规格弹层。
 * 加购去向由 useCartDestination 统一决定：用户有进行中的共享购物车时先弹层询问
 * 加入共享车还是个人购物车，没有则保持原有行为直接入个人购物车。
 *
 * 数量规则（MOQ/步长）统一由 `useQuickCart` 决定，与结算侧
 * `SharedCartServiceImpl.validateQuantityRules` 的校验口径一致。
 *
 * 默认渲染圆形购物车图标；装修组件通过默认插槽注入自己的按钮样式，
 * 这样运营在后台选的「购买按钮样式」仍然生效，但按钮从装饰变为可用。
 *
 * 加购目的地的选择弹层不由本组件挂载：本组件随商品卡重复渲染，挂在这里会
 * 让同屏出现 N 份弹层（遮罩叠成全黑、动画抖动、按钮冒泡跳商详）。弹层统一
 * 挂在根组件 src/App.ku.vue，每页恰好一份。
 */
import { nextTick, ref } from 'vue'

import { useCartDestination } from '@/composables/useCartDestination'
import { useQuickCart } from '@/composables/useQuickCart'
import { initGoodsSpecs } from '@/utils/goods-specs'

const props = withDefaults(defineProps<{
  /** 商品 SPU ID */
  spuId: string
  /** 默认圆形按钮直径（rpx），使用插槽时不生效 */
  size?: string
}>(), {
  size: '44rpx',
})

const emit = defineEmits<{
  (e: 'added', spuId: string): void
}>()

const { pending, quickAdd } = useQuickCart()
const { submitCartAdd } = useCartDestination()
const skuPopup = ref()
const skuKey = ref(false)
const skuMode = ref(2)
/** 弹层是否已挂载，见模板中的懒挂载说明 */
const skuMounted = ref(false)
/** 多规格商品的弹层数据源，结构对齐详情页的 goodsSpu */
const skuGoods = ref<any>({})

function openSkuPopup(info: any) {
  const goods = {
    id: info?.spuId || props.spuId,
    name: info?.name || '',
    spuUrls: info?.spuUrls || [],
    enableSpecs: info?.enableSpecs,
    salesPrice: info?.salesPrice,
    goodsSkus: (info?.goodsSkus || []).map((sku: any) => ({
      ...sku,
      // 弹层默认按 `id` 读取 SKU 主键，后端 VO 用的是 skuId
      id: sku.skuId,
      spuId: info?.spuId || props.spuId,
      specsArr: sku.specsArr || [],
    })),
  }
  initGoodsSpecs(goods)
  skuGoods.value = goods
  skuMounted.value = true
  // 弹层由 v-if 新建，需等一帧再打开，否则首次点击不会弹出
  nextTick(() => {
    skuKey.value = true
  })
}

async function handleAdd() {
  const result = await quickAdd(props.spuId)
  if (result.added) {
    emit('added', props.spuId)
    return
  }
  if (result.needChoose) {
    openSkuPopup(result.info)
  }
}

/** SKU 弹层回传后加购：与快捷直加同一目的地口径（个人/共享），提示与角标由统一入口处理 */
async function handleSkuAdd(data: any) {
  try {
    const dest = await submitCartAdd({
      skuId: data.skuId,
      quantity: data.quantity,
      spuId: data.spuId || props.spuId,
    })
    if (dest === 'abort')
      return
    skuKey.value = false
    emit('added', props.spuId)
  }
  catch {
    // 请求异常已由统一拦截器提示，弹层保持打开让用户重试
  }
}
</script>

<template>
  <view
    class="quick-cart-btn"
    :class="{ 'quick-cart-btn--pending': pending }"
    @tap.stop="handleAdd"
  >
    <!-- 有插槽时由调用方决定外观（装修组件的「购买按钮样式」） -->
    <slot>
      <view
        class="flex items-center justify-center rounded-full bg-primary text-white"
        :style="{ width: size, height: size }"
      >
        <wd-icon name="cart" :size="`calc(${size} * 0.55)`" color="#ffffff" />
      </view>
    </slot>
  </view>

  <!--
    规格弹层懒挂载：列表页每张卡片都渲染一份弹层会让小程序节点数随商品数线性增长，
    只有真正点开多规格商品时才创建。
  -->
  <vk-data-goods-sku-popup
    v-if="skuMounted"
    ref="skuPopup"
    v-model="skuKey"
    border-radius="20"
    :localdata="skuGoods"
    sku-arr-name="specsArr"
    sku-list-name="goodsSkus"
    spec-list-name="specList"
    :mode="skuMode"
    @add-cart="handleSkuAdd"
  />
</template>

<style lang="scss" scoped>
.quick-cart-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.2s;

  &--pending {
    opacity: 0.6;
  }
}
</style>
