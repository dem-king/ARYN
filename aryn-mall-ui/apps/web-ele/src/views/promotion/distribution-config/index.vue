<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  DistributionConfigPayload,
  DistributionConfigRecord,
} from '#/api/promotion/distribution-config';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  addObj,
  delObj,
  editObj,
  getPage,
} from '#/api/promotion/distribution-config';
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
const { distribution_config_status } = useDict('distribution_config_status');
const loading = ref(false);
const showSearch = ref(true);
const showForm = ref(false);
const formRef = ref();
const state = reactive({
  queryParams: {
    configName: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as DistributionConfigRecord[],
  formData: {
    id: '',
    configName: '',
    commissionRate: 0.1,
    commissionRateLevel2: 0,
    minWithdrawAmount: 100,
    settleCycleDays: 7,
    status: '0',
  } as DistributionConfigPayload,
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

const openAdd = () => {
  state.formData = {
    id: '',
    configName: '',
    commissionRate: 0.1,
    commissionRateLevel2: 0,
    minWithdrawAmount: 100,
    settleCycleDays: 7,
    status: '0',
  };
  showForm.value = true;
};

const openEdit = (row: DistributionConfigRecord) => {
  state.formData = {
    id: row.id,
    configName: row.configName,
    commissionRate: row.commissionRate ?? 0.1,
    commissionRateLevel2: row.commissionRateLevel2 ?? 0,
    minWithdrawAmount: row.minWithdrawAmount ?? 100,
    settleCycleDays: row.settleCycleDays ?? 7,
    status: row.status ?? '0',
  };
  showForm.value = true;
};

const submitForm = async () => {
  await formRef.value?.validate();
  if (state.formData.commissionRate + state.formData.commissionRateLevel2 > 1) {
    ElMessage.error('一级与二级佣金比例合计不能超过100%');
    return;
  }
  if (state.formData.id) {
    await editObj(state.formData);
    ElMessage.success('修改成功');
  } else {
    await addObj(state.formData);
    ElMessage.success('新增成功');
  }
  showForm.value = false;
  initPage();
};

const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该配置，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

initPage();
</script>

<template>
  <div>
    <div class="hx-layout-container">
      <div class="hx-layout-container-auto hx-layout-container-view">
        <!-- 搜索 -->
        <ElForm
          :model="state.queryParams"
          ref="queryRef"
          :inline="true"
          v-show="showSearch"
        >
          <ElFormItem label="配置名称" prop="configName">
            <ElInput
              v-model="state.queryParams.configName"
              clearable
              style="width: 200px"
              placeholder="请输入配置名称"
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
          <div>
            <ElButton
              type="primary"
              @click="openAdd"
              v-access:code="'promotion:distributionconfig:add'"
              :icon="Plus"
            >
              新增
            </ElButton>
          </div>
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
            prop="configName"
            label="配置名称"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn prop="commissionRate" label="佣金比例" align="center">
            <template #default="scope">
              {{
                scope.row.commissionRate != null
                  ? `${(scope.row.commissionRate * 100).toFixed(2)}%`
                  : '-'
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="commissionRateLevel2"
            label="二级佣金比例"
            align="center"
          >
            <template #default="scope">
              {{
                `${(Number(scope.row.commissionRateLevel2 || 0) * 100).toFixed(2)}%`
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="minWithdrawAmount"
            label="最低提现金额"
            align="center"
          />
          <ElTableColumn
            prop="settleCycleDays"
            label="结算周期(天)"
            align="center"
          />
          <ElTableColumn prop="status" label="状态" align="center">
            <template #default="scope">
              <DictTag
                :options="distribution_config_status"
                :value="scope.row.status"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createTime" label="创建时间" width="180" />
          <ElTableColumn label="操作" width="200" align="center" fixed="right">
            <template #default="scope">
              <ElButton
                link
                type="primary"
                @click="openEdit(scope.row)"
                v-access:code="'promotion:distributionconfig:edit'"
                :icon="Edit"
              >
                修改
              </ElButton>
              <ElButton
                link
                type="danger"
                @click="del(scope.row.id)"
                v-access:code="'promotion:distributionconfig:del'"
                :icon="Delete"
              >
                删除
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

    <!-- 新增/编辑表单 -->
    <ElDialog v-model="showForm" title="分销配置" width="560px">
      <ElForm ref="formRef" :model="state.formData" label-width="120px">
        <ElFormItem label="配置名称" prop="configName" required>
          <ElInput
            v-model="state.formData.configName"
            maxlength="32"
            show-word-limit
            placeholder="请输入配置名称"
          />
        </ElFormItem>
        <ElFormItem label="佣金比例" prop="commissionRate" required>
          <ElInputNumber
            v-model="state.formData.commissionRate"
            :max="1"
            :min="0"
            :step="0.01"
            :precision="4"
          />
          <span style="margin-left: 8px; font-size: 12px; color: #909399">
            如0.10表示10%
          </span>
        </ElFormItem>
        <ElFormItem label="二级佣金比例" prop="commissionRateLevel2" required>
          <ElInputNumber
            v-model="state.formData.commissionRateLevel2"
            :max="1"
            :min="0"
            :step="0.01"
            :precision="4"
          />
        </ElFormItem>
        <ElFormItem label="最低提现金额" prop="minWithdrawAmount" required>
          <ElInputNumber
            v-model="state.formData.minWithdrawAmount"
            :min="0"
            :precision="2"
          />
          <span style="margin-left: 8px; font-size: 12px; color: #909399">
            元
          </span>
        </ElFormItem>
        <ElFormItem label="结算周期" prop="settleCycleDays" required>
          <ElInputNumber
            v-model="state.formData.settleCycleDays"
            :max="365"
            :min="0"
            :precision="0"
          />
          <span style="margin-left: 8px; font-size: 12px; color: #909399">
            天
          </span>
        </ElFormItem>
        <ElFormItem label="启用状态">
          <ElSwitch
            :model-value="state.formData.status === '0'"
            @update:model-value="
              (val) => (state.formData.status = val === true ? '0' : '1')
            "
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showForm = false">取消</ElButton>
        <ElButton type="primary" @click="submitForm">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
