<script setup lang="ts">
import type { ShopInfoProps } from './types';

import { computed, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { Location, Phone, Shop } from '@element-plus/icons-vue';
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
    <article v-for="shop in state.items" :key="shop.id" class="shop-panel">
      <ElAvatar :size="56" :src="shop.logoUrl">
        <ElIcon><Shop /></ElIcon>
      </ElAvatar>
      <div class="shop-panel__body">
        <strong>{{ shop.name }}</strong>
        <p v-if="showData.showDescription && shop.address">
          <ElIcon><Location /></ElIcon><span>{{ shop.address }}</span>
        </p>
        <p v-if="showData.showContact && shop.phone">
          <ElIcon><Phone /></ElIcon><span>{{ shop.phone }}</span>
        </p>
      </div>
    </article>
  </RetailPreviewFrame>
</template>

<style scoped>
.shop-panel {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 76px;
}

.shop-panel__body {
  min-width: 0;
}

.shop-panel__body strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  white-space: nowrap;
}

.shop-panel__body p {
  display: flex;
  gap: 5px;
  align-items: center;
  margin: 7px 0 0;
  overflow: hidden;
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.shop-panel__body p span {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
