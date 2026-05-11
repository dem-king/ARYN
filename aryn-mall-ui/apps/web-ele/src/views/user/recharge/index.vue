<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue';
import { ElButton, ElTable, ElTableColumn } from 'element-plus';

import { delObj, getPage } from '#/api/user/recharge-config';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(() => import('#/components/right-toolbar/index.vue'));
const Pagination = defineAsyncComponent(() => import('#/components/pagination/index.vue'));
const DictTag = defineAsyncComponent(() => import('#/components/dict-tag/index.vue'));
const Form = defineAsyncComponent(() => import('./form.vue'));

const { status } = useDict('status');
const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10, asc: '', desc: 'create_time' },
  tableData: [],
});
const loading = ref(false);
const formRef = ref();

const initPage = async () => {
  loading.value = true;
  const params = { current: state.page.currentPage, size: state.page.pageSize, asc: state.page.asc, desc: state.page.desc };
  await getPage(params).then((res) => { state.tableData = res.records; state.page.total = res.total; loading.value = false; }).catch(() => { loading.value = false; });
};

const handleAdd = () => { formRef.value.initForm(); };
const handleEdit = (row: any) => { formRef.value.initForm(row); };
const handleDelete = async (row: any) => { await delObj(row.id); initPage(); };

initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="hx-table-toolbar">
        <div><ElButton type="primary" v-access:code="'user:rechargeconfig:add'" @click="handleAdd" :icon="Plus">新增</ElButton></div>
        <RightToolbar :search-btn="false" :refresh-btn="true" @refresh="initPage" />
      </div>
      <Form ref="formRef" @init-page="initPage" />
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="rechargeAmount" label="充值金额" />
        <ElTableColumn prop="giftAmount" label="赠送金额" />
        <ElTableColumn prop="giftPoint" label="赠送积分" />
        <ElTableColumn prop="sortOrder" label="排序" width="80" />
        <ElTableColumn prop="status" label="状态" width="80">
          <template #default="scope"><DictTag :options="status" :value="scope.row.status" /></template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" align="center">
          <template #default="scope">
            <ElButton link type="primary" v-access:code="'user:rechargeconfig:edit'" @click="handleEdit(scope.row)" :icon="Edit">编辑</ElButton>
            <ElButton link type="danger" v-access:code="'user:rechargeconfig:del'" @click="handleDelete(scope.row)" :icon="Delete">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <Pagination :total="state.page.total" v-model:current="state.page.currentPage" v-model:size="state.page.pageSize" @change="initPage" />
    </div>
  </div>
</template>
