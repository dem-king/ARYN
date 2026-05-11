<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/promotion/coupon-user';

// 字典

const props = defineProps({
  userId: {
    type: String,
    default: '',
  },
});
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const state = reactive({
  queryParams: {
    userId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();
const initPage = async () => {
  if (!props.userId) {
    return;
  }
  state.queryParams.userId = props.userId;

  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 重置搜索表单
 */
const resetQuery = () => {
  state.queryParams.userId = props.userId;
  initPage();
};
initPage();
</script>
<template>
  <div>
    <!-- 搜索 -->
    <ElForm
      :model="state.queryParams"
      ref="queryRef"
      :inline="true"
      v-show="showSearch"
    >
      <ElFormItem>
        <ElButton type="primary" @click="initPage"> 搜索 </ElButton>
        <ElButton @click="resetQuery"> 重置 </ElButton>
      </ElFormItem>
    </ElForm>
    <!-- 工具栏 -->
    <div class="hx-table-toolbar">
      <div></div>
      <RightToolbar
        :search-btn="true"
        :refresh-btn="true"
        @search="showSearch = !showSearch"
        @refresh="initPage"
      />
    </div>
    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData">
      <ElTableColumn
        prop="couponName"
        label="优惠券"
        show-overflow-tooltip
        align="center"
      />
      <ElTableColumn prop="receivedTime" label="领取时间" align="center" />
      <ElTableColumn prop="validatTime" label="有效时间" align="center" />
      <ElTableColumn prop="usedTime" label="使用时间" align="center" />
      <ElTableColumn prop="status" label="状态" align="center">
        <template #default="scope">
          <ElTag v-if="scope.row.status === '0'" type="info">未使用</ElTag>
          <ElTag v-else-if="scope.row.status === '1'" type="danger">
            已使用
          </ElTag>
          <ElTag v-else-if="scope.row.status === '2'" type="danger">
            已过期
          </ElTag>
          <ElTag v-else-if="scope.row.status === '3'" type="danger">
            冻结
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createTime" label="创建时间" width="180" />
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
