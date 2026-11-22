<script setup lang="ts">
import type { CatalogIndexStatus } from '#/api/product/catalog-index';

import { onMounted, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  activateCatalogIndex,
  createCatalogIndex,
  getCatalogIndex,
  retryCatalogIndex,
} from '#/api/product/catalog-index';

const state = ref<CatalogIndexStatus>();
const busy = ref(false);
const error = ref('');
async function run(action: () => Promise<unknown>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await action();
    state.value = await getCatalogIndex();
  } catch (error_) {
    error.value =
      error_ instanceof Error ? error_.message : '索引操作失败，请刷新后重试';
  } finally {
    busy.value = false;
  }
}
async function activate(id: string) {
  await run(async () => {
    await ElMessageBox.confirm(
      '查询将同时使用该索引固定的模型版本；历史索引可再次切回。',
      '切换目录索引',
    );
    await activateCatalogIndex(id, state.value?.revision ?? 0);
  });
}
onMounted(() => run(async () => {}));
</script>
<template>
  <ElAlert
    title="当前租户目录独立构建。配置变更后先构建完整索引，再切换；构建期间继续使用原索引。无可用索引时使用字面匹配。"
    :closable="false"
    class="mb-4"
  />
  <ElAlert
    v-if="error"
    :title="error"
    type="error"
    :closable="false"
    class="mb-4"
  />
  <div class="mb-4 flex flex-wrap items-center gap-3">
    <span>当前索引：{{ state?.activeGeneration ?? '无' }}</span>
    <ElButton :loading="busy" @click="run(async () => {})">刷新进度</ElButton>
    <ElButton
      v-access:code="['upms:ai-config:edit']"
      :disabled="busy"
      type="primary"
      @click="run(createCatalogIndex)"
    >
      构建新索引
    </ElButton>
  </div>
  <ElTable :data="state?.generations ?? []" row-key="id">
    <ElTableColumn prop="id" label="索引版本" min-width="220" />
    <ElTableColumn prop="configVersion" label="固定配置版本" min-width="220" />
    <ElTableColumn prop="dimension" label="维度" width="90" />
    <ElTableColumn prop="status" label="状态" width="110" />
    <ElTableColumn prop="indexedRows" label="已构建" width="90" />
    <ElTableColumn prop="expectedRows" label="目录数量" width="100" />
    <ElTableColumn prop="errorCode" label="错误" min-width="130" />
    <ElTableColumn label="操作" width="150">
      <template #default="{ row }">
        <ElButton
          v-if="row.status === 'FAILED'"
          v-access:code="['upms:ai-config:edit']"
          :disabled="busy"
          @click="run(() => retryCatalogIndex(row.id))"
        >
          重试
        </ElButton>
        <ElButton
          v-if="row.status === 'READY'"
          v-access:code="['upms:ai-config:activate']"
          :disabled="busy || state?.activeGeneration === row.id"
          @click="activate(row.id)"
        >
          切换
        </ElButton>
      </template>
    </ElTableColumn>
  </ElTable>
</template>
