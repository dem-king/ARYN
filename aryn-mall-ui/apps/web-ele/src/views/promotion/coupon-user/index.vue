<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/promotion/coupon-user';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    couponName: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
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
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="优惠券名称" prop="couponName">
          <ElInput
            v-model="state.queryParams.couponName"
            clearable
            placeholder="请输入优惠券名称"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="优惠券状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择优惠券状态"
          >
            <ElOption label="未使用" value="0" />
            <ElOption label="已使用" value="1" />
            <ElOption label="已过期" value="2" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条领券记录">
        <template #right>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        border
        empty-text="暂无领券记录"
      >
        <ElTableColumn
          prop="userInfoVO"
          label="用户信息"
          show-overflow-tooltip
          align="center"
        >
          <template #default="scope">
            <span style="color: var(--el-color-primary)">
              {{ scope.row.userInfoVO.nickname }}({{
                scope.row.userInfoVO.phone
              }})
            </span>
          </template>
        </ElTableColumn>
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
            <ElTag v-if="scope.row.status === '0'" type="primary">
              未使用
            </ElTag>
            <ElTag v-else-if="scope.row.status === '1'" type="success">
              已使用
            </ElTag>
            <ElTag v-else-if="scope.row.status === '2'" type="info">
              已过期
            </ElTag>
            <ElTag v-else-if="scope.row.status === '3'" type="warning">
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
  </div>
</template>
