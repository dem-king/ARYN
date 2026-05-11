<script lang="ts" setup>
import type { TableInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { ElRadio, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getPage } from '#/api/promotion/page-design';
import { useDict } from '#/utils/dict';

defineProps({
  // 双向绑定值，默认为 modelValue，
  modelValue: {
    type: Object,
    default: null,
  },
});
const emit = defineEmits(['update:modelValue']);
// 字典
const { status } = useDict('status');
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const selectedRow = ref<number>();
const tableRef = ref<TableInstance>();
const state = reactive({
  dialog: false,
  loading: false,
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
  },
  queryParams: {
    groupId: '',
  },
  tableData: [],
  currentList: [], // 选中的商品
});
/**
 * 商品分页列表
 */
const initPage = () => {
  state.dialog = true;
  state.loading = true;
  getPage({
    current: state.page.currentPage,
    size: state.page.pageSize,
  })
    .then((response) => {
      state.loading = false;
      state.tableData = response.records;
      state.page.total = response.total;
    })
    .catch(() => {
      state.loading = false;
    });
};

function handleRowClick(row: any) {
  // 当点击一行时，设置对应的单选按钮为选中状态
  if (selectedRow.value !== row.id) {
    selectedRow.value = row.id;
    emit('update:modelValue', {
      name: row.pageName,
      url: `/pages/shop/diy-page/index?id=${row.id}`,
    });
  }
}

function handleRadioChange(row: any) {
  // 单选按钮变化时，更新 selectedRow
  selectedRow.value = row.id;
  emit('update:modelValue', {
    name: row.pageName,
    url: `/pages/shop/diy-page/index?id=${row.id}`,
  });
}
initPage();
</script>
<template>
  <div>
    <ElTable
      ref="tableRef"
      v-loading="state.loading"
      :data="state.tableData"
      highlight-current-row
      @row-click="handleRowClick"
    >
      <ElTableColumn label="" align="center" width="50">
        <template #default="scope">
          <ElRadio
            v-model="selectedRow"
            :value="scope.row.id"
            :disabled="scope.row.homeStatus === '1'"
            @change="handleRadioChange(scope.row)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="pageName" label="名称" align="center" />
      <ElTableColumn prop="homeStatus" label="首页状态" align="center">
        <template #default="scope">
          <ElTag v-if="scope.row.homeStatus === '0'" type="danger">否</ElTag>
          <ElTag v-if="scope.row.homeStatus === '1'" type="success">是</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="状态" align="center">
        <template #default="scope">
          <DictTag :options="status" :value="scope.row.status" />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createTime" label="创建时间" align="center" />
    </ElTable>
    <!-- 分页 -->
    <Pagination
      :total="state.page.total"
      v-model:current="state.page.currentPage"
      v-model:size="state.page.pageSize"
      @change="initPage"
    />
  </div>
</template>
<style lang="scss" scoped>
:deep(.el-table .el-radio__label) {
  display: none !important;
}
</style>
