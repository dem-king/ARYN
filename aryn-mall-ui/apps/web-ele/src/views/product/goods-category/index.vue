<script lang="ts" setup>
import { defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Sort } from '@element-plus/icons-vue';
import {
  ElButton,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/goods-category';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
// 字典
const { status } = useDict('status');
const state = reactive({
  tableData: [],
  queryParams: {},
});
const showSearch = ref(true);
const refreshTable = ref(true);
const isDefaultExpand = ref(false);
const loading = ref(false);
const formRef = ref();

const initPage = async () => {
  loading.value = true;
  await getPage()
    .then((response) => {
      state.tableData = response;
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
  formRef.value.initForm();
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
  ElMessageBox.confirm('此操作将删除该类目，是否继续?', '提示', {
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
/**
 * 展开/折叠菜单
 */
const onExpand = () => {
  isDefaultExpand.value = !isDefaultExpand.value;
  refreshTable.value = false;
  nextTick(() => {
    refreshTable.value = true;
  });
};
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'product:goodscategory:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <ElButton @click="onExpand" :icon="Sort"> 展开/折叠 </ElButton>
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
      <ElTable
        v-if="refreshTable"
        v-loading="loading"
        :data="state.tableData"
        row-key="id"
        style="width: 100%"
        :default-expand-all="isDefaultExpand"
        border
      >
        <ElTableColumn prop="name" label="类目名称" width="200" />
        <ElTableColumn prop="description" label="类目描述" />
        <ElTableColumn prop="categoryPic" label="类目图片">
          <template #default="scope">
            <ElImage
              style="width: 50px; height: 50px"
              v-if="scope.row.categoryPic"
              :src="scope.row.categoryPic"
              :preview-src-list="[scope.row.categoryPic]"
              fit="cover"
              :preview-teleported="true"
            />
          </template>
        </ElTableColumn>

        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort" label="排序" align="center" />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" align="center" width="200">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'product:goodscategory:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'product:goodscategory:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>
