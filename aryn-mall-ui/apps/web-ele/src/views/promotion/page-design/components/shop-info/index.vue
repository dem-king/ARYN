<script setup lang="ts">
import type { ShopInfoProps } from './types';

import { computed, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { Location, Phone, Service, Shop } from '@element-plus/icons-vue';
import { ElAvatar, ElIcon } from 'element-plus';

import { loadShopInfo } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateShopInfo } from './types';

interface TenantUserInfo {
  tenantId?: string;
}

const props = defineProps<{ showData: ShopInfoProps }>();
const userStore = useUserStore();
const tenantId = computed(
  () =>
    localStorage.getItem('switch-tenant-id') ||
    (userStore.userInfo as null | TenantUserInfo)?.tenantId ||
    '',
);
const controller =
  createRetailPreviewController<
    Awaited<ReturnType<typeof loadShopInfo>>[number]
  >();
const { state } = controller;

/**
 * 画布框通用样式：
 * - 悬浮压图开启时顶部外边距上移 16px（C 端 32rpx 同口径），上移发生在
 *   frame 的 margin 上，卡片整体叠到上方 banner 画布块之上；
 * - C 端卡片自带 24rpx 圆角，运营未设置圆角时画布框默认 12px 对齐卡片
 *   外观，设置过圆角则按运营值（C 端根节点裁切会盖过卡片自带圆角）。
 */
const frameCommonStyle = computed(() => {
  const style = { ...props.showData.commonStyle };
  if (props.showData.liftOverBanner) {
    style.styleTopMargin = (Number(style.styleTopMargin) || 0) - 16;
  }
  const hasRadius = [
    style.styleLtRadius,
    style.styleRtRadius,
    style.styleLbRadius,
    style.styleRbRadius,
  ].some((radius) => Number(radius) > 0);
  if (!hasRadius) {
    style.styleLtRadius = 12;
    style.styleRtRadius = 12;
    style.styleLbRadius = 12;
    style.styleRbRadius = 12;
  }
  return style;
});

watch(
  [() => props.showData, tenantId],
  ([showData, currentTenantId]) => {
    const errors = validateShopInfo(showData);
    if (!currentTenantId) errors.push('请先切换到需要装修的租户');
    return controller.load(errors, () => loadShopInfo(currentTenantId));
  },
  { deep: true, immediate: true },
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="frameCommonStyle"
    :message="state.message"
    :status="state.status"
    class="shop-info-frame"
  >
    <div v-for="shop in state.items" :key="shop.id" class="shop-content">
      <ElAvatar
        v-if="shop.logoUrl"
        :size="44"
        class="shop-logo"
        :src="shop.logoUrl"
      >
        <ElIcon><Shop /></ElIcon>
      </ElAvatar>
      <span v-else class="shop-logo shop-logo--empty">
        <ElIcon :size="22"><Shop /></ElIcon>
      </span>
      <div class="shop-main">
        <div class="shop-name">{{ shop.name }}</div>
        <div v-if="showData.showDescription && shop.address" class="shop-meta">
          <ElIcon :size="12" class="shop-meta-icon"><Location /></ElIcon>
          <span class="shop-meta-text">{{ shop.address }}</span>
        </div>
        <div v-if="showData.showDescription && shop.phone" class="shop-meta">
          <ElIcon :size="12" class="shop-meta-icon"><Phone /></ElIcon>
          <span class="shop-meta-text">{{ shop.phone }}</span>
        </div>
      </div>
      <!-- 小程序端是 open-type=contact 的联系按钮，预览按同尺寸静态呈现 -->
      <span v-if="showData.showContact" class="contact-button">
        <ElIcon :size="13"><Service /></ElIcon>
        联系
      </span>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-shop-info 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  卡片本体（白底/圆角/投影）由画布框承载：frame 默认背景即白色，圆角经
  frameCommonStyle 兜底 12px，投影挂在 frame 根节点（frame overflow:hidden
  会裁掉内层元素的投影，根节点自身的投影不受影响）。
-->
<style scoped lang="scss">
.shop-info-frame {
  position: relative;
  z-index: 1;
  border-color: transparent;
  box-shadow: 0 6px 16px rgb(31 35 41 / 6%);
}

.shop-content {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
}

.shop-logo {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: #fff;
  background: #f2f3f5;
  border-radius: 10px;
}

/* 无 logo 兜底：主题色渐变底 + 白色店铺图标（与 C 端 shop-logo--empty 同源） */
.shop-logo--empty {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-primary, #ff2237),
    var(--wot-color-theme-secondary, #ff6b7a)
  );
}

.shop-main {
  flex: 1;
  min-width: 0;
}

.shop-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  white-space: nowrap;
}

.shop-meta {
  display: flex;
  gap: 3px;
  align-items: center;
  margin-top: 4px;
  overflow: hidden;
}

.shop-meta-icon {
  flex: 0 0 auto;
  color: var(--wot-color-theme-primary, #ff2237);
}

.shop-meta-text {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #667085;
  white-space: nowrap;
}

.contact-button {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 3px;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-primary, #ff2237),
    var(--wot-color-theme-secondary, #ff6b7a)
  );
  border-radius: 30px;
  box-shadow: 0 4px 10px rgb(31 35 41 / 12%);
}
</style>
