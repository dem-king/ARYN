<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { useRequireLogin } from '@/composables/useRequireLogin'

definePage({ name: 'shared-cart-chain-import', style: { navigationStyle: 'custom', navigationBarTitleText: '粘贴接龙报货' } })
const { ensureLoggedIn } = useRequireLogin()
/** 接龙新写统一进入采购工作台，旧深链在公共历史报告页读取。 */
onLoad((options) => {
  const cartId = encodeURIComponent(options?.cartId ?? '')
  const importId = encodeURIComponent(options?.importId ?? '')
  const legacy = options?.legacy ? '&legacy=1' : ''
  const redirectPath = `/sub-pages/order/shared-cart/chain-import?cartId=${cartId}&importId=${importId}${legacy}`
  if (!ensureLoggedIn(redirectPath))
    return
  uni.redirectTo({ url: options?.legacy
    ? `/sub-pages/order/shared-cart/import?cartId=${cartId}&importId=${importId}&legacy=1`
    : `/sub-pages/order/procurement/index?cartId=${cartId}` })
})
</script>

<template><view /></template>
