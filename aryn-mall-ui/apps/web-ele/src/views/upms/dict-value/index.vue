<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/upms/dict-value';

const props = defineProps({
  dictId: {
    type: String,
    default: '',
  },
});
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const state = reactive({
  queryParams: {},
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: 'sort',
    desc: '',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();

const initPage = async () => {
  if (props.dictId) {
    loading.value = true;
    const params = {
      current: state.page.currentPage,
      size: state.page.pageSize,
      asc: state.page.asc,
      desc: state.page.desc,
      dictId: props.dictId,
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
  }
};
/**
 * 新增按钮
 */
const add = () => {
  const row = { dictId: props.dictId };
  formRef.value.initForm(row);
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该字典键值，是否继续?', '提示', {
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
  <div class="layout-padding-auto layout-padding-view">
    <!-- 工具栏 -->
    <div class="hx-table-toolbar">
      <div>
        <ElButton
          type="primary"
          v-access:code="'upms:sysdictvalue:edit'"
          @click="add"
          :icon="Plus"
        >
          新增
        </ElButton>
      </div>
      <RightToolbar
        :search-btn="false"
        :refresh-btn="true"
        @search="showSearch = !showSearch"
        @refresh="initPage"
      />
    </div>
    <Form ref="formRef" @init-page="initPage" />
    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData" border>
      <ElTableColumn prop="dictLabel" label="字典标签" align="center" />
      <ElTableColumn prop="dictValue" label="字典键值" align="center" />
      <ElTableColumn prop="remarks" label="备注" align="center" />

      <ElTableColumn prop="status" label="状态" align="center">
        <template #default="scope">
          <ElTag v-if="scope.row.status === '0'" type="success">正常</ElTag>
          <ElTag v-if="scope.row.status === '1'" type="danger">停用</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="sort" label="排序" align="center" />
      <ElTableColumn prop="createTime" label="创建时间" />
      <ElTableColumn label="操作" width="180" align="center">
        <template #default="scope">
          <ElButton
            link
            type="primary"
            v-access:code="'upms:sysdictvalue:edit'"
            @click="edit(scope.row)"
            :icon="Edit"
          >
            修改
          </ElButton>
          <ElButton
            link
            type="danger"
            v-access:code="'upms:sysdictvalue:del'"
            @click="del(scope.row.id)"
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
</template>
