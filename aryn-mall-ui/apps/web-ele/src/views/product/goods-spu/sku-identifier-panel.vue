<script setup lang="ts">
import type { SkuIdentifier } from '#/api/product/sku-measurement';

import { ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElInput,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  getSkuIdentifiers,
  registerSkuIdentifiers,
  reviewSkuIdentifier,
} from '#/api/product/sku-measurement';

const props = defineProps<{ skuId?: string }>();
const rows = ref<SkuIdentifier[]>([]);
const text = ref('');
const busy = ref(false);
const error = ref('');
async function run(action: () => Promise<unknown>) {
  if (busy.value || !props.skuId) return;
  busy.value = true;
  error.value = '';
  try {
    await action();
    rows.value = await getSkuIdentifiers(props.skuId);
  } catch (error_) {
    error.value = error_ instanceof Error ? error_.message : '映射操作失败';
  } finally {
    busy.value = false;
  }
}
function importRows() {
  return run(async () => {
    const inputs = text.value
      .split(/\r?\n/)
      .filter((line) => line.trim())
      .map((line) => {
        const [type, ...values] = line.split('\t');
        return {
          skuId: props.skuId!,
          type: type?.trim() ?? '',
          value: values.join('\t').trim(),
          source: 'IMPORT',
        };
      });
    await registerSkuIdentifiers(inputs);
    text.value = '';
  });
}
function approve(id: string, revision: number, approved: boolean) {
  const row = rows.value.find((value) => value.id === id);
  if (!row) return;
  return run(async () => {
    await ElMessageBox.confirm(
      `${approved ? '审核通过' : '拒绝'} ${row.identifierType}：${row.displayValue}。只有审核通过的映射参与采购匹配。`,
      '审核映射',
    );
    await reviewSkuIdentifier(id, revision, approved);
  });
}
watch(
  () => props.skuId,
  () => {
    rows.value = [];
    void run(async () => {});
  },
  { immediate: true },
);
</script>
<template>
  <div class="mt-4 grid gap-3">
    <ElAlert
      v-if="!skuId"
      title="先保存SKU，再维护编码和别名。"
      :closable="false"
    />
    <template v-else>
      <ElAlert
        title="按行粘贴「码制 + Tab + 值」，码制支持 IMPA / ISSA / CUSTOM / ALIAS；导入为待审核，不自动参与匹配。"
        :closable="false"
      />
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <ElInput
        v-model="text"
        type="textarea"
        :rows="3"
        placeholder="ALIAS&#9;土豆"
      />
      <ElButton
        v-access:code="['product:goodsspu:edit']"
        :disabled="busy || !text.trim()"
        @click="importRows"
      >
        导入待审核映射
      </ElButton>
      <ElTable :data="rows">
        <ElTableColumn prop="identifierType" label="码制" /><ElTableColumn
          prop="displayValue"
          label="值"
        /><ElTableColumn prop="status" label="状态" /><ElTableColumn
          prop="source"
          label="来源"
        /><ElTableColumn prop="reviewBy" label="审核人" />
        <ElTableColumn label="审核" width="160">
          <template #default="{ row }">
            <ElButton
              v-access:code="['product:goodsspu:edit']"
              :disabled="busy || row.status === 'APPROVED'"
              @click="approve(row.id, row.revision, true)"
            >
              {{ '通过' }}
            </ElButton>
            <ElButton
              v-access:code="['product:goodsspu:edit']"
              :disabled="busy || row.status === 'REJECTED'"
              @click="approve(row.id, row.revision, false)"
            >
              拒绝
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </template>
  </div>
</template>
