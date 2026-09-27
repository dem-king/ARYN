<script setup lang="ts">
import type { ShopInfoProps } from './types';

import { computed, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { Shop } from '@element-plus/icons-vue';
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
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
  >
    <div v-for="shop in state.items" :key="shop.id" class="shop-content">
      <ElAvatar
        v-if="shop.logoUrl"
        :size="48"
        class="shop-logo"
        :src="shop.logoUrl"
      >
        <ElIcon><Shop /></ElIcon>
      </ElAvatar>
      <span v-else class="shop-logo shop-logo--empty">
        <ElIcon><Shop /></ElIcon>
      </span>
      <div class="shop-main">
        <div class="shop-name">{{ shop.name }}</div>
        <div
          v-if="showData.showDescription && shop.address"
          class="shop-description"
        >
          {{ shop.address }}
        </div>
        <div v-if="showData.showDescription && shop.phone" class="shop-phone">
          {{ shop.phone }}
        </div>
      </div>
      <!-- 小程序端是 open-type=contact 的联系按钮，预览按同尺寸静态呈现 -->
      <span v-if="showData.showContact" class="contact-button">联系</span>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-shop-info 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.shop-content {
  display: flex;
  gap: 9px;
  align-items: center;
  min-height: 56px;
}

.shop-logo {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  font-size: 17px;
  color: #606266;
  background: #f2f3f5;
  border-radius: 4px;
}

.shop-main {
  flex: 1;
  min-width: 0;
}

.shop-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14.5px;
  font-weight: 600;
  color: #1f2329;
  white-space: nowrap;
}

.shop-description,
.shop-phone {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

.contact-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 28px;
  font-size: 11.5px;
  color: #303133;
  background: #fff;
  border: 0.5px solid #dcdfe6;
  border-radius: 4px;
}
</style>
