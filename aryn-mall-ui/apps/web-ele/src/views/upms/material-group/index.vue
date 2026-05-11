<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, editObj, getPage } from '#/api/upms/material-group';

const emit = defineEmits(['initGroup']);

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const state = reactive<any>({
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [],
});

const loading = ref(false);
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
  };
  await getPage(params)
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      state.tableData.unshift({ name: '未分组', id: '-1' });
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 修改素材
 */
const edit = (row: any) => {
  ElMessageBox.prompt('请输入分组名', '修改分组名', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputValue: row.name,
  }).then(({ value }) => {
    editObj({ id: row.id, name: value }).then(() => {
      ElMessage.success('修改成功');
      initPage();
      emit('initGroup', true);
    });
  });
};
/**
 * 删除分组
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该分组，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
        emit('initGroup', true);
      })
      .catch(() => {
        loading.value = false;
      });
  });
};

defineExpose({
  initPage,
});
</script>
<template>
  <div class="hx-layout-container-auto hx-layout-container-view">
    <ElTable v-loading="loading" border :data="state.tableData">
      <ElTableColumn prop="name" label="分组名" />
      <ElTableColumn label="操作" width="200" align="center">
        <template #default="scope">
          <ElButton
            link
            type="primary"
            @click="edit(scope.row)"
            v-access:code="'upms:materialgroup:edit'"
            v-if="scope.row.id !== '-1'"
            :icon="Edit"
          >
            修改
          </ElButton>
          <ElButton
            link
            type="danger"
            @click="del(scope.row.id)"
            v-access:code="'upms:materialgroup:del'"
            v-if="scope.row.id !== '-1'"
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
