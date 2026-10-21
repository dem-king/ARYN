<script setup lang="ts" name="selectPromotionTarget">
import type { TableInstance } from 'element-plus';

import type { PromotionTargetColumn } from './types';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

const props = defineProps<{
  columns: PromotionTargetColumn[];
  limitNum?: number;
  /** 分页查询：query 含 current/size */
  loader: (query: Record<string, unknown>) => Promise<any>;
  noun: string;
  rowLabel: (row: Record<string, any>) => string;
  title: string;
}>();

const emit = defineEmits(['currentRow']);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const tableRef = ref<TableInstance>();
const state = reactive<any>({
  dialog: false,
  loading: false,
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 20,
  },
  tableData: [],
  currentList: [],
  maxHeight: window.screen.availHeight - 500,
});

const limit = () => props.limitNum ?? 1;

const initPage = () => {
  state.dialog = true;
  state.loading = true;
  props
    .loader({ current: state.page.currentPage, size: state.page.pageSize })
    .then((response) => {
      state.loading = false;
      state.tableData = response?.records ?? [];
      state.page.total = Number(response?.total ?? 0);
    })
    .catch(() => {
      state.loading = false;
    });
};

const handleCurrentChange = (val: any) => {
  if (val && val.length > limit()) {
    tableRef.value!.clearSelection();
    state.currentList = [];
    return ElMessage.error(`最多只能选择【${limit()}】个${props.noun}`);
  }
  state.currentList = val;
};

const onSubmit = () => {
  if (state.currentList.length <= 0) {
    return ElMessage.error(`请选择${props.noun}`);
  }
  if (state.currentList.length > limit()) {
    return ElMessage.error(`最多只能选择【${limit()}】个${props.noun}`);
  }
  const message = `已选择 【${props.rowLabel(state.currentList[0])}】等${
    state.currentList.length
  }个${props.noun}, 是否继续?`;
  ElMessageBox.confirm(message, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    emit('currentRow', state.currentList);
    state.dialog = false;
  });
};

function cellText(column: PromotionTargetColumn, row: Record<string, any>) {
  return column.formatter
    ? column.formatter(row)
    : (row[column.prop ?? ''] ?? '');
}

defineExpose({
  initPage,
});
</script>
<template>
  <div>
    <ElDialog v-model="state.dialog" width="80%" :title="title">
      <ElTable
        ref="tableRef"
        v-loading="state.loading"
        :data="state.tableData"
        highlight-current-row
        @selection-change="handleCurrentChange"
        :max-height="state.maxHeight"
      >
        <ElTableColumn type="selection" width="55" />
        <ElTableColumn
          v-for="column in columns"
          :key="column.label"
          :label="column.label"
          :align="column.align ?? 'center'"
          :width="column.width"
          show-overflow-tooltip
        >
          <template #default="scope">
            <ElTag
              v-if="column.tag && column.tag(scope.row)"
              :type="column.tag(scope.row)!.type"
            >
              {{ column.tag(scope.row)!.text }}
            </ElTag>
            <template v-else>{{ cellText(column, scope.row) }}</template>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="state.dialog = false">关闭</ElButton>
          <ElButton type="primary" @click="onSubmit"> 确 认 </ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>
