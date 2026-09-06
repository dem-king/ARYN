<script setup lang="ts">
import type { PageDesignAssetResult } from '#/api/promotion/page-design';

import { ref, watch } from 'vue';

import { ElAlert, ElDialog, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getAssets } from '#/api/promotion/page-design';

const props = defineProps<{
  modelValue: boolean;
  pageId: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const result = ref<null | PageDesignAssetResult>(null);

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    result.value = null;
    if (!props.pageId) return;
    loading.value = true;
    getAssets(props.pageId)
      .then((data) => {
        result.value = data;
      })
      .finally(() => {
        loading.value = false;
      });
  },
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="素材引用检查"
    width="680px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="result">
      <ElAlert
        v-if="result.insecureCount > 0"
        :closable="false"
        :title="`存在 ${result.insecureCount} 个非 https 引用，小程序真机可能无法加载`"
        type="warning"
      />
      <ElAlert
        v-else
        :closable="false"
        title="未发现不安全的素材引用"
        type="success"
      />
      <div class="asset-summary">
        <ElTag effect="plain" size="small" type="info">
          图片 {{ result.imageCount }}
        </ElTag>
        <ElTag effect="plain" size="small" type="info">
          视频 {{ result.videoCount }}
        </ElTag>
      </div>
      <ElTable v-loading="loading" :data="result.assets" max-height="380">
        <ElTableColumn label="类型" width="80" align="center">
          <template #default="{ row }">
            <ElTag
              :type="row.mediaType === 'video' ? 'warning' : 'info'"
              effect="plain"
              size="small"
            >
              {{ row.mediaType === 'video' ? '视频' : '图片' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="地址"
          min-width="260"
          prop="url"
          show-overflow-tooltip
        />
        <ElTableColumn label="来源组件" width="130">
          <template #default="{ row }">
            {{ row.componentType }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="引用" width="80" align="center">
          <template #default="{ row }">
            <ElTag
              :type="row.external ? 'success' : 'info'"
              effect="plain"
              size="small"
            >
              {{ row.external ? '外链' : '站内' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
    </template>
  </ElDialog>
</template>

<style scoped>
.asset-summary {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
</style>
