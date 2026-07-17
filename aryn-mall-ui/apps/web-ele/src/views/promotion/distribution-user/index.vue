<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  CircleCheck,
  CircleClose,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { disable, enable, getPage } from '#/api/promotion/distribution-user';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const queryRef = ref<FormInstance>();
const { distribution_user_status } = useDict('distribution_user_status');
const loading = ref(false);
const showSearch = ref(true);
const state = reactive({
  queryParams: {
    userId: '',
    inviterUserId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as any[],
});

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

const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};

async function doEnable(row: any) {
  await ElMessageBox.confirm('确认启用该分销用户？', '启用', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await enable(row.userId);
  ElMessage.success('已启用');
  initPage();
}

async function doDisable(row: any) {
  await ElMessageBox.confirm('确认禁用该分销用户？', '禁用', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await disable(row.userId);
  ElMessage.success('已禁用');
  initPage();
}

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
        <ElFormItem label="分销员ID" prop="userId">
          <ElInput
            v-model="state.queryParams.userId"
            clearable
            style="width: 200px"
            placeholder="请输入用户ID"
          />
        </ElFormItem>
        <ElFormItem label="邀请人ID" prop="inviterUserId">
          <ElInput
            v-model="state.queryParams.inviterUserId"
            clearable
            style="width: 200px"
            placeholder="请输入邀请人ID"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
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
        <ElTableColumn
          prop="userId"
          label="分销员ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="nickname"
          label="昵称"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="inviterUserId"
          label="邀请人ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="totalCommission" label="累计佣金" align="center" />
        <ElTableColumn
          prop="availableCommission"
          label="可提现佣金"
          align="center"
        />
        <ElTableColumn
          prop="frozenCommission"
          label="冻结佣金"
          align="center"
        />
        <ElTableColumn prop="status" label="状态" align="center">
          <template #default="scope">
            <DictTag
              :options="distribution_user_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="160" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-if="scope.row.status === '1'"
              v-access:code="'promotion:distributionuser:edit'"
              :icon="CircleCheck"
              link
              type="primary"
              @click="doEnable(scope.row)"
            >
              启用
            </ElButton>
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:distributionuser:edit'"
              :icon="CircleClose"
              link
              type="danger"
              @click="doDisable(scope.row)"
            >
              禁用
            </ElButton>
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
    </div>
  </div>
</template>
