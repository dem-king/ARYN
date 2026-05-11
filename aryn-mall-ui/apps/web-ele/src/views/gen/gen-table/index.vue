<script lang="ts" setup name="wxApp">
import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getList } from '#/api/gen/datasource';
import { getPage } from '#/api/gen/table';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: { dsName: '', tableName: '' },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();
const dsList = ref();
const router = useRouter();

const initPage = async () => {
  if (state.queryParams.dsName === '') {
    return;
  }
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
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
};

const getDataSource = async () => {
  const response = await getList({});
  dsList.value = response;
  if (dsList.value && dsList.value.length > 0) {
    state.queryParams.dsName = dsList.value[0].dbName;
    await initPage();
  }
};
const genCode = async (tableName: string) => {
  router.push({
    path: '/code/gen',
    query: { tableName, dsName: state.queryParams.dsName },
  });
};
getDataSource();
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
        <ElFormItem label="数据源" prop="dsName">
          <ElSelect
            v-model="state.queryParams.dsName"
            clearable
            placeholder="请选择数据源"
            style="width: 200px"
          >
            <ElOption
              v-for="item in dsList"
              :key="item.dbName"
              :label="item.name"
              :value="item.dbName"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="表名称" prop="tableName">
          <ElInput
            v-model="state.queryParams.tableName"
            clearable
            placeholder="请输入表名称"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
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
        <ElTableColumn prop="name" label="表名称" />
        <ElTableColumn prop="comment" label="表描述" />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="danger"
              @click="genCode(scope.row.name)"
              v-access:code="'gen:datasource:del'"
              :icon="Download"
            >
              生成代码
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
