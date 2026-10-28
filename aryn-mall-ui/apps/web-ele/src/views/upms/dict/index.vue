<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDrawer,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage, refresh } from '#/api/upms/dict';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const { status } = useDict('status');
const DictValue = defineAsyncComponent(() => import('../dict-value/index.vue'));
const state = reactive({
  queryParams: {
    status: '',
    type: '',
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
const refForm = ref();
const dictId = ref('');
const dictValueDrawer = ref(false);

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response: any) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 新增按钮
 */
const add = () => {
  refForm.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  refForm.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该字典，是否继续?', '提示', {
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

const refreshCache = () => {
  refresh().then(() => {
    ElMessage.success('刷新成功');
  });
};
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
        <ElFormItem label="字典类型" prop="type">
          <ElInput
            v-model="state.queryParams.type"
            placeholder="请输入字典类型"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="字典状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择字典状态"
          >
            <ElOption label="正常" value="0" />
            <ElOption label="停用" value="1" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个字典">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'upms:sysdict:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <ElButton @click="refreshCache" :icon="Refresh"> 刷新缓存 </ElButton>
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
      <Form ref="refForm" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无字典"
      >
        <ElTableColumn prop="type" label="字典类型" />
        <ElTableColumn prop="description" label="字典描述" />
        <ElTableColumn prop="remarks" label="备注" />
        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="260" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:sysdict:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'upms:sysdict:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="primary"
              @click="((dictValueDrawer = true), (dictId = scope.row.id))"
              :icon="View"
            >
              键值列表
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElDrawer size="40%" v-model="dictValueDrawer" direction="rtl">
        <template #header>
          <h4>键值列表</h4>
        </template>
        <template #default>
          <DictValue :dict-id="dictId" v-if="dictValueDrawer" />
        </template>
      </ElDrawer>
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
