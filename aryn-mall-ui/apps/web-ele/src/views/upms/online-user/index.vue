<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getList } from '#/api/upms/online-user';

const state = reactive({
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const initPage = async () => {
  loading.value = true;
  const params = {};
  await getList(params)
    .then((response) => {
      state.tableData = response;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
// 强退用户
const forced = (token: string) => {
  ElMessageBox.confirm(`此操作将强退该用户, 是否继续?`, '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      delObj(token)
        .then(() => {
          ElMessage.success('强退成功');
          initPage();
        })
        .catch(() => {});
    })
    .catch(() => {});
};
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
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
        <ElTableColumn prop="tokenId" label="token" width="180" />
        <ElTableColumn prop="tokenTimeout" label="过期时间" />
        <ElTableColumn prop="userName" label="登录用户" />
        <ElTableColumn prop="ipAddr" label="登录地址" />
        <ElTableColumn prop="location" label="登录地点" />
        <ElTableColumn prop="loginTime" label="登录时间" />
        <ElTableColumn prop="browser" label="浏览器" />
        <ElTableColumn prop="os" label="操作系统" />
        <ElTableColumn label="操作" align="center" fixed="right" width="150">
          <template #default="scope">
            <ElButton
              link
              type="danger"
              v-access:code="'upms:onlineuser:forced'"
              @click="forced(scope.row.tokenId)"
              :icon="Delete"
            >
              强退用户
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>
