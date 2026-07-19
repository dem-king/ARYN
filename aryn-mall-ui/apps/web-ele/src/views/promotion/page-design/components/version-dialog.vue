<script setup lang="ts">
import type { PageDesignVersion } from '#/api/promotion/page-design';

import { ref, watch } from 'vue';

import { RefreshLeft } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getVersions, rollbackVersion } from '#/api/promotion/page-design';

const props = defineProps<{
  modelValue: boolean;
  pageId: string;
  pageName: string;
}>();

const emit = defineEmits<{
  restored: [];
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const versions = ref<PageDesignVersion[]>([]);

async function loadVersions() {
  if (!props.modelValue || !props.pageId) return;
  loading.value = true;
  try {
    versions.value = await getVersions(props.pageId);
  } finally {
    loading.value = false;
  }
}

async function restore(version: PageDesignVersion) {
  await ElMessageBox.confirm(
    `将版本 V${version.versionNo} 重新发布为最新版本，历史记录不会被覆盖。`,
    '确认回滚',
    {
      confirmButtonText: '回滚并发布',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await rollbackVersion(
    props.pageId,
    version.id,
    `回滚至 V${version.versionNo}`,
  );
  ElMessage.success('已创建新的回滚版本');
  await loadVersions();
  emit('restored');
}

watch(() => [props.modelValue, props.pageId], loadVersions);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="`${pageName} · 版本历史`"
    width="760px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElTable v-loading="loading" :data="versions" max-height="480">
      <ElTableColumn label="版本" width="90">
        <template #default="{ row }">
          <ElTag effect="plain">V{{ row.versionNo }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="publishRemark" label="发布说明" min-width="180" />
      <ElTableColumn prop="publishBy" label="发布人" width="120" />
      <ElTableColumn label="发布时间" width="170">
        <template #default="{ row }">
          {{
            row.publishedAt
              ? dayjs(row.publishedAt).format('YYYY-MM-DD HH:mm')
              : '-'
          }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="100" align="center">
        <template #default="{ row }">
          <ElButton
            v-access:code="'promotion:pagedesign:rollback'"
            :icon="RefreshLeft"
            link
            type="primary"
            @click="restore(row as PageDesignVersion)"
          >
            回滚
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </ElDialog>
</template>
