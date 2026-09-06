<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/upms/sys-role';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const RoleMenu = defineAsyncComponent(() => import('./rolemenu.vue'));
const state = reactive({
  queryParams: {
    roleName: '',
    roleCode: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const roleMenuRef = ref();
const queryRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
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
  ElMessageBox.confirm('此操作将删除该角色，是否继续?', '提示', {
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
 * 受保护角色（超级管理员/配送员资格角色）由系统管理，
 * 不提供修改与删除入口，配送资格在配送员管理中开通或停用
 */
const isProtectedRole = (roleCode: string) =>
  roleCode === 'ROLE_ADMIN' || roleCode === 'delivery_staff';
const onAuth = (row: any) => {
  roleMenuRef.value.initRoleMenu(row.id);
};
initPage();
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
        <ElFormItem label="角色名称" prop="roleName">
          <ElInput
            v-model="state.queryParams.roleName"
            clearable
            placeholder="请输入角色名称"
          />
        </ElFormItem>
        <ElFormItem label="角色编码" prop="roleCode">
          <ElInput
            v-model="state.queryParams.roleCode"
            clearable
            placeholder="请输入角色编码"
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
        <div>
          <ElButton
            type="primary"
            v-access:code="'upms:sysrole:add'"
            @click="add"
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
      <!-- 新增/编辑表单 -->
      <Form ref="formRef" @init-page="initPage" />
      <RoleMenu ref="roleMenuRef" @init-page="initPage" />
      <!-- table列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="roleName" label="角色名称" />
        <ElTableColumn prop="roleCode" label="角色编码" />
        <ElTableColumn prop="roleDesc" label="角色描述" />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" align="center" fixed="right" width="300">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:sysrole:edit'"
              v-if="!isProtectedRole(scope.row.roleCode)"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'upms:sysrole:del'"
              v-if="!isProtectedRole(scope.row.roleCode)"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'upms:sysrole:add'"
              @click="onAuth(scope.row)"
              :icon="Plus"
            >
              权限
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
