<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref, watch } from 'vue';

import { ElTable, ElTableColumn } from 'element-plus';

import { getPage } from '#/api/user/sign-in-record';

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
  const params: any = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    userId: props.userId,
  };
  await getPage(params)
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
      <ElTableColumn prop="signDate" label="签到日期" width="120" />
      <ElTableColumn prop="consecutiveDay" label="连续签到天数" width="130" />
      <ElTableColumn prop="rewardPoint" label="获得积分" width="100" />
    </ElTable>
    <Pagination
      :total="state.page.total"
      v-model:current="state.page.currentPage"
      v-model:size="state.page.pageSize"
      @change="initPage"
    />
  </div>
</template>
