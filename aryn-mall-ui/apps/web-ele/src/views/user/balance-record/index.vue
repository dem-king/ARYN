<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/user/balance-record';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    nickname: '',
    changeType: '',
    dateRange: [] as string[],
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
  loading.value = true;
  const params: any = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  if (state.queryParams.nickname) params.nickname = state.queryParams.nickname;
  if (state.queryParams.changeType)
    params.changeType = state.queryParams.changeType;
  if (state.queryParams.dateRange?.length === 2) {
    params.beginTime = state.queryParams.dateRange[0];
    params.endTime = state.queryParams.dateRange[1];
  }
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

const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
};

initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="会员昵称" prop="nickname">
          <ElInput
            v-model="state.queryParams.nickname"
            placeholder="请输入会员昵称"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="变动类型" prop="changeType">
          <ElSelect
            v-model="state.queryParams.changeType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <ElOption label="充值" value="1" />
            <ElOption label="消费" value="2" />
            <ElOption label="调整" value="3" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="变动时间" prop="dateRange">
          <ElDatePicker
            v-model="state.queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh">重置</ElButton>
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
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="nickname" label="用户昵称" />
        <ElTableColumn prop="changeType" label="变动类型" width="80">
          <template #default="scope">
            <ElTag v-if="scope.row.changeType === '1'" type="success">
              充值
            </ElTag>
            <ElTag v-else-if="scope.row.changeType === '2'" type="danger">
              消费
            </ElTag>
            <ElTag v-else-if="scope.row.changeType === '3'" type="warning">
              调整
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="changeAmount" label="变动金额" width="100">
          <template #default="scope">
            <span
              :style="{
                color: scope.row.changeType === '2' ? '#f56c6c' : '#67c23a',
              }"
            >
              {{ scope.row.changeType === '2' ? '-' : '+'
              }}{{ scope.row.changeAmount }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="balanceAfter" label="变动后余额" width="100" />
        <ElTableColumn prop="triggerScene" label="触发场景" width="80" />
        <ElTableColumn prop="remark" label="备注" />
        <ElTableColumn prop="createTime" label="创建时间" width="170" />
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
