<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/user/member-level';
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
const Form = defineAsyncComponent(() => import('./form.vue'));
const PaidOrderTab = defineAsyncComponent(() => import('./paid-order-tab.vue'));

const { status } = useDict('status');
const activeTab = ref('level');
const state = reactive({
  queryParams: {
    levelName: '',
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
const formRef = ref();

const initPage = async () => {
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

const resetQuery = () => {
  state.queryParams.levelName = '';
  initPage();
};

const handleAdd = () => {
  formRef.value.initForm();
};

const handleEdit = (row: any) => {
  formRef.value.initForm(row);
};

const handleDelete = async (row: any) => {
  await delObj(row.id);
  initPage();
};

initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElTabs v-model="activeTab">
        <ElTabPane label="等级管理" name="level">
          <!-- 搜索栏 -->
          <ElForm v-show="showSearch" :inline="true" :model="state.queryParams">
            <ElFormItem label="等级名称" prop="levelName">
              <ElInput
                v-model="state.queryParams.levelName"
                placeholder="请输入等级名称"
                clearable
                @keyup.enter="initPage"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :icon="Search" @click="initPage">
                搜索
              </ElButton>
              <ElButton :icon="Refresh" @click="resetQuery">重置</ElButton>
            </ElFormItem>
          </ElForm>
          <!-- 工具栏 -->
          <div class="hx-table-toolbar">
            <div>
              <ElButton
                type="primary"
                v-access:code="'user:memberlevel:add'"
                @click="handleAdd"
                :icon="Plus"
              >
                新增
              </ElButton>
            </div>
            <RightToolbar
              v-model:show-search="showSearch"
              :refresh-btn="true"
              @refresh="initPage"
            />
          </div>
          <Form ref="formRef" @init-page="initPage" />

          <!-- 列表 -->
          <ElTable v-loading="loading" :data="state.tableData" border>
            <ElTableColumn prop="levelName" label="等级名称" />
            <ElTableColumn prop="levelIcon" label="等级图标" width="80">
              <template #default="scope">
                <ElImage
                  v-if="scope.row.levelIcon"
                  :src="scope.row.levelIcon"
                  style="width: 40px; height: 40px"
                  fit="contain"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="conditionType"
              label="升级条件类型"
              width="130"
            >
              <template #default="scope">
                <ElTag v-if="scope.row.conditionType === '1'" type="success">
                  累计消费金额
                </ElTag>
                <ElTag
                  v-else-if="scope.row.conditionType === '2'"
                  type="warning"
                >
                  累计积分
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="conditionValue" label="升级条件值" />
            <ElTableColumn prop="growthValue" label="成长值阈值" width="110" />
            <ElTableColumn prop="isPaid" label="付费会员" width="100">
              <template #default="scope">
                <ElTag v-if="scope.row.isPaid === '1'" type="danger">是</ElTag>
                <ElTag v-else type="info">否</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="exclusiveDiscount"
              label="专属折扣"
              width="100"
            >
              <template #default="scope">
                {{
                  scope.row.exclusiveDiscount
                    ? `${scope.row.exclusiveDiscount}%`
                    : '-'
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="sortOrder" label="排序" width="80" />
            <ElTableColumn prop="status" label="状态" width="80">
              <template #default="scope">
                <DictTag :options="status" :value="scope.row.status" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="150" align="center">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  v-access:code="'user:memberlevel:edit'"
                  @click="handleEdit(scope.row)"
                  :icon="Edit"
                >
                  编辑
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  v-access:code="'user:memberlevel:del'"
                  @click="handleDelete(scope.row)"
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
        </ElTabPane>
        <ElTabPane label="付费会员订单" name="paidOrder">
          <PaidOrderTab v-if="activeTab === 'paidOrder'" />
        </ElTabPane>
      </ElTabs>
    </div>
  </div>
</template>
