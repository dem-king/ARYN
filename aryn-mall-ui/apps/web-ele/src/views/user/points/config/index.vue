<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { delObj, getPage } from '#/api/user/points-config';
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

const { status } = useDict('status');
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
const formRef = ref();

const sceneMap: Record<string, string> = {
  REGISTER: '注册',
  CONSUME: '消费',
  SIGN_IN: '签到',
  REVIEW: '评价',
  EXCHANGE: '兑换',
};

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
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
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'user:pointsconfig:add'"
            @click="handleAdd"
            :icon="Plus"
          >
            新增
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="false"
          :refresh-btn="true"
          @refresh="initPage"
        />
      </div>
      <Form ref="formRef" @init-page="initPage" />
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="ruleName" label="规则名称" />
        <ElTableColumn prop="ruleType" label="规则类型" width="100">
          <template #default="scope">
            <ElTag v-if="scope.row.ruleType === '1'" type="success">获取</ElTag>
            <ElTag v-else-if="scope.row.ruleType === '2'" type="danger">
              消耗
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="triggerScene" label="触发场景" width="100">
          <template #default="scope">
            {{ sceneMap[scope.row.triggerScene] || scope.row.triggerScene }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="pointValue" label="积分值" width="80" />
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
              v-access:code="'user:pointsconfig:edit'"
              @click="handleEdit(scope.row)"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'user:pointsconfig:del'"
              @click="handleDelete(scope.row)"
              :icon="Delete"
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
  </div>
</template>
