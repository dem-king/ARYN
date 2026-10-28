<script lang="ts" setup>
import type { TenantOverview } from '#/api/upms/tenant';

import { ref } from 'vue';

import { ARYN_PLATFORM_TENANT_ID } from '@vben/constants';

import dayjs from 'dayjs';
import {
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElTag,
} from 'element-plus';

import { getOverview } from '#/api/upms/tenant';
import { useDict } from '#/utils/dict';

const { status } = useDict('status');
const { business_mode: businessModeOptions } = useDict('business_mode');

const visible = ref(false);
const loading = ref(false);
const overview = ref<null | TenantOverview>(null);

const open = async (id: string) => {
  visible.value = true;
  loading.value = true;
  overview.value = null;
  try {
    overview.value = await getOverview(id);
  } finally {
    loading.value = false;
  }
};

const labelOf = (options: any, value?: null | string) => {
  // 模板中 ref 会被自动解包，这里同时兼容 Ref 与数组两种入参
  const list = Array.isArray(options) ? options : (options?.value ?? []);
  return list.find((item: any) => item.value === value)?.label ?? value ?? '-';
};

const formatDateTime = (value?: null | string) => {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
};

const formatNumber = (value?: null | number) => {
  return value === null || value === undefined ? '-' : value.toLocaleString();
};

const formatBytes = (value?: null | number) => {
  if (value === null || value === undefined) return '-';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const remainingText = (data: TenantOverview) => {
  if (data.id === ARYN_PLATFORM_TENANT_ID) return '长期有效';
  const value = data.remainingDays;
  if (value === null || value === undefined) return '-';
  if (value < 0) return `已过期 ${Math.abs(value)} 天`;
  if (value === 0) return '今天到期';
  return `${value} 天`;
};

const remainingTagType = (
  data: TenantOverview,
): 'danger' | 'info' | 'success' | 'warning' => {
  if (data.id === ARYN_PLATFORM_TENANT_ID) return 'success';
  const value = data.remainingDays;
  if (value === null || value === undefined) return 'info';
  if (value <= 7) return 'danger';
  if (value <= 30) return 'warning';
  return 'success';
};

const metrics = (data: TenantOverview) => [
  { label: '管理端账号', value: formatNumber(data.adminUserCount) },
  { label: '商城会员', value: formatNumber(data.mallUserCount) },
  { label: '商品数', value: formatNumber(data.goodsCount) },
  { label: '订单数', value: formatNumber(data.orderCount) },
  { label: '素材数', value: formatNumber(data.materialCount) },
  { label: '存储占用', value: formatBytes(data.storageBytes) },
  { label: '在线会话', value: formatNumber(data.onlineSessionCount) },
];

defineExpose({ open });
</script>
<template>
  <ElDrawer v-model="visible" title="租户概览" size="560px">
    <div v-loading="loading" class="tenant-detail">
      <template v-if="overview">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="租户名称" :span="2">
            {{ overview.name }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="套餐">
            {{ overview.packageName || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <ElTag :type="overview.status === '0' ? 'success' : 'danger'">
              {{ labelOf(status, overview.status) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="业务模式">
            {{ labelOf(businessModeOptions, overview.businessMode) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="剩余天数">
            <ElTag :type="remainingTagType(overview)">
              {{ remainingText(overview) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="授权开始">
            {{ formatDateTime(overview.authBeginTime) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="授权结束">
            {{
              overview.id === ARYN_PLATFORM_TENANT_ID
                ? '长期有效'
                : formatDateTime(overview.authEndTime)
            }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <h4 class="tenant-detail__title">资源与经营</h4>
        <div class="tenant-detail__metrics">
          <div
            v-for="item in metrics(overview)"
            :key="item.label"
            class="tenant-detail__metric"
          >
            <span class="tenant-detail__metric-label">{{ item.label }}</span>
            <span class="tenant-detail__metric-value">{{ item.value }}</span>
          </div>
        </div>

        <ElDescriptions :column="1" border>
          <ElDescriptionsItem label="最近登录">
            {{ formatDateTime(overview.lastLoginTime) }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </template>
    </div>
  </ElDrawer>
</template>
<style scoped>
.tenant-detail {
  min-height: 240px;
}

.tenant-detail__title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.tenant-detail__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 20px;
}

.tenant-detail__metric {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.tenant-detail__metric-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.tenant-detail__metric-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
