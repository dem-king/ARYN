<script lang="ts" setup>
import type { DistributionUserRecord } from '#/api/promotion/distribution-user';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { CircleCheck, CircleClose, Delete } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  delObj,
  disable,
  enable,
  getPage,
} from '#/api/promotion/distribution-user';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';
import { formatMoney } from '#/utils/util';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

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
  tableData: [] as DistributionUserRecord[],
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

async function doEnable(row: DistributionUserRecord) {
  await ElMessageBox.confirm('确认启用该分销用户？', '启用', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await enable(row.userId);
  ElMessage.success('已启用');
  initPage();
}

async function doDisable(row: DistributionUserRecord) {
  await ElMessageBox.confirm('确认禁用该分销用户？', '禁用', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await disable(row.userId);
  ElMessage.success('已禁用');
  initPage();
}

async function doDelete(row: DistributionUserRecord) {
  await ElMessageBox.confirm(
    '仅无余额、无待结算佣金、无欠款且无待审核提现的分销员可删除。确认继续？',
    '删除分销员',
    {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await delObj(row.id);
  ElMessage.success('删除成功');
  initPage();
}

initPage();

useKeepAliveRefresh(initPage);
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
        <ElFormItem label="分销员ID" prop="userId">
          <ElInput
            v-model="state.queryParams.userId"
            clearable
            placeholder="请输入用户ID"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="邀请人ID" prop="inviterUserId">
          <ElInput
            v-model="state.queryParams.inviterUserId"
            clearable
            placeholder="请输入邀请人ID"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个分销员">
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
        empty-text="暂无分销员"
      >
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
        <ElTableColumn prop="totalCommission" label="累计佣金" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.totalCommission) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="availableCommission"
          label="可提现佣金"
          align="center"
        >
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.availableCommission) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="frozenCommission" label="冻结佣金" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.frozenCommission) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="pendingCommission"
          label="待结算佣金"
          align="center"
        >
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.pendingCommission) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="commissionDebt" label="佣金欠款" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.commissionDebt) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="subordinateCount"
          label="邀请人数"
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
        <ElTableColumn label="操作" width="220" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-if="scope.row.status === '1'"
              v-access:code="'promotion:distributionuser:edit'"
              :icon="CircleCheck"
              link
              type="primary"
              @click="doEnable(scope.row as DistributionUserRecord)"
            >
              启用
            </ElButton>
            <ElButton
              v-access:code="'promotion:distributionuser:del'"
              :icon="Delete"
              link
              type="danger"
              @click="doDelete(scope.row as DistributionUserRecord)"
            >
              删除
            </ElButton>
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:distributionuser:edit'"
              :icon="CircleClose"
              link
              type="danger"
              @click="doDisable(scope.row as DistributionUserRecord)"
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
