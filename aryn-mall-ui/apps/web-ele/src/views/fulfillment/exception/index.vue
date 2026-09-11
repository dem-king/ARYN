<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { closeException, getExceptionPage } from '#/api/fulfillment/wave';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const typeLabel: Record<string, string> = {
  DAMAGE: '破损',
  OTHER: '其他',
  REPLACE: '替代',
  SHORT_PICK: '缺货/短装',
  WRONG_ITEM: '错发',
};

const statusLabel: Record<string, string> = {
  '1': '待处理',
  '2': '处理中',
  '3': '已关闭',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getExceptionPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const handleClose = (row: any) => {
  ElMessageBox.prompt('请输入处理说明', '关闭异常', {
    confirmButtonText: '确认关闭',
    cancelButtonText: '取消',
  })
    .then(({ value }) => closeException(row.id, value ?? ''))
    .then(() => {
      ElMessage.success('异常已关闭');
      initPage();
    })
    .catch(() => {});
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="exceptionType" label="异常类型" width="120">
          <template #default="scope">
            {{ typeLabel[scope.row.exceptionType] ?? scope.row.exceptionType }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="orderId" label="订单" min-width="180" />
        <ElTableColumn prop="description" label="描述" min-width="220" />
        <ElTableColumn prop="waveId" label="波次" min-width="160" />
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            {{ statusLabel[scope.row.status] ?? scope.row.status }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="handleRemark" label="处理说明" min-width="160" />
        <ElTableColumn prop="handledTime" label="处理时间" width="170" />
        <ElTableColumn label="操作" width="100">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'fulfillment:exception:close'"
              :disabled="scope.row.status === '3'"
              @click="handleClose(scope.row)"
            >
              关闭
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
