<script lang="ts" setup>
import type { PayCheckResult } from '#/api/pay/config';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, View } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  checkRevision,
  delObj,
  disableConfig,
  getPage,
  migrateLegacy,
  publishRevision,
} from '#/api/pay/config';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';

const Form = defineAsyncComponent(() => import('./form.vue'));
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const { pay_terminal_type, pay_type } = useDict(
  'pay_terminal_type',
  'pay_type',
);
const state = reactive({
  queryParams: {
    mchId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [] as any[],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const checkDialog = ref(false);
const checkResult = ref<null | PayCheckResult>(null);

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getPage(
      Object.assign(
        {
          current: state.page.currentPage,
          size: state.page.pageSize,
          asc: state.page.asc,
          desc: state.page.desc,
        },
        state.queryParams,
      ),
    );
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const add = () => {
  formRef.value.initForm();
};

const edit = (row: any) => {
  formRef.value.initForm(row);
};

const history = (row: any) => {
  formRef.value.initForm(row, { focus: 'revisions' });
};

const del = (id: string) => {
  ElMessageBox.confirm(
    '删除前会校验历史交易；已关联交易或存量支付单的配置不能删除。是否继续?',
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

const enableToggle = async (row: any) => {
  if (row.enabled === '1') {
    await ElMessageBox.confirm(
      '停用只禁止新发起，历史回调/退款仍可处理。是否停用?',
      '停用支付渠道',
      { type: 'warning' },
    );
    await disableConfig(row.id, row.configVersion);
    ElMessage.success('已停用');
    initPage();
    return;
  }
  if (!row.activeRevisionId) {
    ElMessage.warning('请先创建并启用身份版本');
    formRef.value.initForm(row);
    return;
  }
  await publishRevision(row.id, row.activeRevisionId, row.configVersion);
  ElMessage.success('已启用');
  initPage();
};

const check = async (row: any) => {
  if (!row.activeRevisionId) {
    ElMessage.warning('请先创建身份版本');
    return;
  }
  checkResult.value = await checkRevision(row.id, row.activeRevisionId);
  checkDialog.value = true;
};

const migrate = async () => {
  await ElMessageBox.confirm(
    '只迁移当前租户的旧明文配置；材料加密入库，冲突配置阻断，不删除任何资金记录。',
    '迁移旧支付配置',
    { type: 'warning' },
  );
  const report: any = await migrateLegacy();
  await ElMessageBox.alert(
    `迁移 ${report.migrated} 条，跳过 ${report.skipped} 条，阻断 ${report.blocked} 条。请查看后端返回明细核对。`,
    '迁移结果',
    { type: report.blocked > 0 ? 'warning' : 'success' },
  );
  initPage();
};

const enabledTag = (row: any) => (row.enabled === '1' ? 'success' : 'info');
const checkLabel = (row: any) => {
  if (row.checkStatus === '1') return '本地通过';
  if (row.checkStatus === '2') return '检查失败';
  return '未检查';
};

initPage();

useKeepAliveRefresh(initPage);
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="商户号" prop="mchId">
          <ElInput
            v-model="state.queryParams.mchId"
            clearable
            placeholder="请输入商户号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>
      <PageToolbar :total="state.page.total" total-label="条配置">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'pay:payconfig:add'"
            :icon="Plus"
            @click="add"
          >
            新增
          </ElButton>
          <ElButton
            v-access:code="'pay:payconfig:migrate'"
            :icon="Refresh"
            @click="migrate"
          >
            迁移旧配置
          </ElButton>
        </template>
        <template #right>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>
      <Form ref="formRef" @init-page="initPage" />
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        border
        empty-text="暂无支付配置"
      >
        <ElTableColumn prop="type" label="支付渠道" width="100">
          <template #default="scope">
            <DictTag :options="pay_type" :value="scope.row.type" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="terminalType" label="终端" width="100">
          <template #default="scope">
            <DictTag
              :options="pay_terminal_type"
              :value="scope.row.terminalType"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="appId" label="应用ID" min-width="160" />
        <ElTableColumn prop="mchId" label="商户号/PID" min-width="150" />
        <ElTableColumn label="启用" width="90">
          <template #default="scope">
            <ElTag :type="enabledTag(scope.row)">
              {{ scope.row.enabled === '1' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="资料" width="170">
          <template #default="scope">
            <ElTag :type="scope.row.materialReady ? 'success' : 'warning'">
              {{ scope.row.materialReady ? '材料齐备' : '待补全' }}
            </ElTag>
            <ElTag
              class="ml-1"
              :type="scope.row.checkStatus === '2' ? 'danger' : 'info'"
            >
              {{ checkLabel(scope.row) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="updateTime" label="更新时间" width="170" />
        <ElTableColumn label="操作" align="center" width="330" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'pay:payconfig:edit'"
              :icon="Edit"
              @click="edit(scope.row)"
            >
              配置
            </ElButton>
            <ElButton
              link
              v-access:code="'pay:payconfig:check'"
              :icon="View"
              @click="check(scope.row)"
            >
              检查
            </ElButton>
            <ElButton
              link
              v-access:code="'pay:payconfig:enable'"
              @click="enableToggle(scope.row)"
            >
              {{ scope.row.enabled === '1' ? '停用' : '启用' }}
            </ElButton>
            <ElButton
              link
              v-access:code="'pay:payconfig:get'"
              @click="history(scope.row)"
            >
              变更记录
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'pay:payconfig:del'"
              :icon="Delete"
              @click="del(scope.row.id)"
            >
              删除
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

    <ElDialog v-model="checkDialog" title="支付资料本地检查" width="620px">
      <ElAlert
        v-if="checkResult"
        :title="checkResult.passed ? '本地检查通过' : '本地检查未通过'"
        :type="checkResult.passed ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <p v-if="checkResult" class="check-proof">
        证明等级：{{
          checkResult.proofLevel
        }}。仅证明材料内部一致，不代表平台签约、域名或真实支付已通过。
      </p>
      <ElTable v-if="checkResult" :data="checkResult.items" border>
        <ElTableColumn prop="name" label="检查项" min-width="160" />
        <ElTableColumn label="结果" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.passed ? 'success' : 'danger'">
              {{ scope.row.passed ? '通过' : '失败' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="message" label="说明" min-width="220" />
      </ElTable>
    </ElDialog>
  </div>
</template>
<style scoped>
.check-proof {
  margin: 12px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
