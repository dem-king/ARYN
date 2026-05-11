<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/promotion/coupon-info';
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

const queryRef = ref<FormInstance>();
// 字典
const { status } = useDict('status');
const state = reactive({
  queryParams: {
    couponName: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
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
/**
 * 重置搜索表单
 */
const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};
/**
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm(null);
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
  ElMessageBox.confirm('此操作将删除该优惠券，是否继续?', '提示', {
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
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="优惠券名称" prop="couponName">
          <ElInput
            v-model="state.queryParams.couponName"
            clearable
            style="width: 200px"
            placeholder="请输入优惠券名称"
          />
        </ElFormItem>
        <ElFormItem label="优惠券状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            style="width: 200px"
            placeholder="请选择优惠券状态"
          >
            <ElOption
              v-for="item in status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            @click="add"
            v-access:code="'promotion:couponinfo:add'"
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
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="couponName"
          label="优惠券名称"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="picUrl"
          label="有效时间"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.receiveStartedAt }}-
            {{ scope.row.receiveEndedAt }}
          </template>
        </ElTableColumn>

        <ElTableColumn prop="couponType" label="优惠形式" align="center">
          <template #default="scope">
            <span v-if="scope.row.couponType === '1'" type="danger">
              指定金额:{{ scope.row.amount }}元
            </span>
            <span v-if="scope.row.couponType === '2'" type="success">
              {{ scope.row.discount }}折
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="useRange" label="可用范围" align="center">
          <template #default="scope">
            <ElTag v-if="scope.row.useRange === '1'" type="success">
              全部商品
            </ElTag>
            <ElTag v-if="scope.row.useRange === '2'" type="danger">
              指定商品
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="totalNum" label="发行数量" align="center" />
        <ElTableColumn prop="assignCount" label="已发放数量" align="center" />
        <ElTableColumn prop="usedCount" label="已使用数量" align="center" />
        <ElTableColumn prop="remainNum" label="剩余数量" align="center" />
        <ElTableColumn prop="status" label="状态" align="center">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="edit(scope.row)"
              v-access:code="'promotion:couponinfo:edit'"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'promotion:couponinfo:del'"
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
  </div>
</template>
