<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref, watch } from 'vue';

import { ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getUserPage } from '#/api/user/points-record';

const props = defineProps<{ userId: string }>();

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const loading = ref(false);

const initPage = async () => {
  if (!props.userId) return;
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    userId: props.userId,
  };
  await getUserPage(params)
    .then((res) => {
      state.tableData = res.records;
      state.page.total = res.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

watch(
  () => props.userId,
  (val) => {
    if (val) initPage();
  },
  { immediate: true },
);
</script>
<template>
  <div>
    <ElTable v-loading="loading" :data="state.tableData" border>
      <ElTableColumn prop="changeType" label="变动类型" width="80">
        <template #default="scope">
          <ElTag v-if="scope.row.changeType === '1'" type="success">获取</ElTag>
          <ElTag v-else-if="scope.row.changeType === '2'" type="danger">
            消耗
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="changePoint" label="变动积分" width="100" />
      <ElTableColumn prop="balanceAfter" label="变动后余额" width="100" />
      <ElTableColumn prop="triggerScene" label="触发场景" width="80" />
      <ElTableColumn prop="remark" label="备注" />
      <ElTableColumn prop="createTime" label="变动时间" width="170" />
    </ElTable>
    <Pagination
      :total="state.page.total"
      v-model:current="state.page.currentPage"
      v-model:size="state.page.pageSize"
      @change="initPage"
    />
  </div>
</template>
