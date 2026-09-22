<script setup lang="ts">
import type { QuickCartInfo } from '@/utils/quick-cart'

/**
 * 首页商品勾选·批量加购操作条（B 版原型 `.rp-bar`）。
 *
 * 行为：
 *   勾选若干商品 → 底部条显示「已选 N 项」→ 批量加购
 *
 * 加购流程（难点在商品形态不一，见 utils/batch-cart.ts）：
 *   1. 对勾选项并发查一次加购信息（单规格/多规格/售罄）
 *   2. 单规格可直接算出 skuId 与合法数量 → 走批量接口
 *   3. 多规格必须先选规格 → 不塞进批量接口，提示用户
 *   4. 售罄/查询失败 → 跳过并给出原因
 *
 * 定位说明（**不要抄原型的 bottom:56px**）：
 *   本项目 tabBar 在 H5 是 fixed 且高 `--window-bottom`、在小程序为原生
 *   tabBar（该变量为 0）。购物车结算条已用 `bottom: var(--window-bottom, 0px)`
 *   解决了同一问题，这里复用同一写法，否则 H5 端会与 tabBar 重叠。
 */
import { computed, ref } from 'vue'
import { batchAddShoppingCart } from '@/api/order/shoppingCart'
import { getQuickCartInfo } from '@/api/product/spu'
import { useAuthStore } from '@/store/authStore'
import { useGoodsPickStore } from '@/store/goodsPickStore'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import { batchAddSummaryText, buildBatchAddPlan, needsBatchAddDetail } from '@/utils/batch-cart'

const authStore = useAuthStore()
const pickStore = useGoodsPickStore()
const shoppingCartStore = useShoppingCartStore()

const submitting = ref(false)
/** 需要用户选规格的项数（提交后展示，供用户知道还差什么） */
const needChooseCount = ref(0)

const pickedCount = computed(() => pickStore.pickedCount)
const visible = computed(() => authStore.isLoggedIn && pickedCount.value > 0)

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function clearAll() {
  pickStore.clear()
  needChooseCount.value = 0
}

async function handleBatchAdd() {
  if (submitting.value || pickedCount.value === 0)
    return
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }

  submitting.value = true
  try {
    const pickedIds = [...pickStore.pickedIds]
    // 并发查加购信息；单项失败不阻断其余项（由 buildBatchAddPlan 归入 blocked）
    const infoBySpuId: Record<string, QuickCartInfo | null> = {}
    await Promise.all(pickedIds.map(async (spuId) => {
      try {
        infoBySpuId[spuId] = await getQuickCartInfo(spuId)
      }
      catch {
        infoBySpuId[spuId] = null
      }
    }))

    const plan = buildBatchAddPlan(pickedIds, infoBySpuId)
    let addedCount = 0
    let failedCount = 0

    if (plan.ready.length > 0) {
      const result = await batchAddShoppingCart(
        plan.ready.map(item => ({ skuId: item.skuId, quantity: item.quantity })),
      )
      addedCount = Number(result?.addedCount ?? 0)
      failedCount = Number(result?.failedCount ?? 0)
    }

    needChooseCount.value = plan.needChoose.length

    const summary = batchAddSummaryText({
      added: addedCount,
      needChoose: plan.needChoose.length,
      blocked: plan.blocked.length,
      failed: failedCount,
    })

    if (needsBatchAddDetail({
      needChoose: plan.needChoose.length,
      blocked: plan.blocked.length,
      failed: failedCount,
    })) {
      // 有未进购物车的项：用弹窗而非 toast，让用户能看清哪些没进去
      uni.showModal({
        title: '批量加购结果',
        content: summary,
        showCancel: false,
        confirmText: '知道了',
      })
    }
    else {
      uni.showToast({ title: summary, icon: 'none' })
    }

    // 成功加入的项从勾选里移除；需选规格的保留，方便用户逐个处理
    for (const item of plan.ready) {
      if (addedCount > 0)
        pickStore.remove(item.spuId)
    }
    void shoppingCartStore.fetchCartCount().catch(() => {})
  }
  catch {
    uni.showToast({ title: '批量加购失败，请稍后重试', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view v-if="visible" class="batch-bar">
    <view class="batch-bar__inner">
      <view class="batch-bar__pick">
        <text>已选</text>
        <text class="batch-bar__count">
          {{ pickedCount }}
        </text>
        <text>项</text>
      </view>
      <view class="batch-bar__clear" @tap.stop="clearAll">
        清空
      </view>
      <view
        class="batch-bar__submit"
        :class="{ 'batch-bar__submit--pending': submitting }"
        @tap.stop="handleBatchAdd"
      >
        {{ submitting ? '加入中…' : '批量加购' }}
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.batch-bar {
  position: fixed;
  right: 0;
  bottom: var(--window-bottom, 0px);
  left: 0;
  // 高于商品卡、低于 SKU 弹层（共用购物车的 z-index 约定）
  z-index: 900;
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.batch-bar__inner {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
}

.batch-bar__pick {
  display: flex;
  align-items: baseline;
  font-size: 26rpx;
  color: #303133;
}

.batch-bar__count {
  margin: 0 6rpx;
  color: var(--theme-color-primary, var(--wot-color-theme-primary));
  font-size: 34rpx;
  font-weight: 700;
}

.batch-bar__clear {
  margin-left: 24rpx;
  color: #909399;
  font-size: 24rpx;
}

.batch-bar__submit {
  margin-left: auto;
  padding: 0 48rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 38rpx;
  background: var(--theme-color-primary, var(--wot-color-theme-primary));
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.batch-bar__submit--pending {
  opacity: 0.6;
}
</style>
